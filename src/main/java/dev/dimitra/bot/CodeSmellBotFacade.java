package dev.dimitra.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.dimitra.bot.analysis.AnalyzedFileBuilder;
import dev.dimitra.bot.analysis.SmellAnalyzer;
import dev.dimitra.bot.diff.DiffSplitter;
import dev.dimitra.bot.github.GitHubPrClient;
import dev.dimitra.bot.github.PullRequestContext;
import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.model.AnalyzedFile;
import dev.dimitra.bot.model.ChangedFile;
import dev.dimitra.bot.report.ReportRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application-level facade for the full code smell bot workflow.
 *
 * Callers do not need to know how GitHub fetching, diff parsing, file content
 * collection, LLM analysis, artifact writing, report rendering, and PR
 * commenting fit together.
 */
public class CodeSmellBotFacade {
    private final GitHubPrClient github;
    private final LlmClient llm;

    public CodeSmellBotFacade(GitHubPrClient github, LlmClient llm) {
        this.github = Objects.requireNonNull(github, "github");
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public void run(BotConfig config) throws Exception {
        Objects.requireNonNull(config, "config");

        PullRequestContext prContext = github.fetchPullRequestContext(
                config.owner(),
                config.repo(),
                config.prNumber());
        List<ChangedFile> allFiles = github.fetchAllChangedFiles(
                config.owner(),
                config.repo(),
                config.prNumber());
        List<ChangedFile> files = limitFiles(allFiles, config.maxFiles());

        Path outDir = Paths.get("out");
        Path outFilesDir = outDir.resolve("files");
        Files.createDirectories(outFilesDir);

        Path fullDiffPath = outDir.resolve("pr.diff");
        Files.writeString(fullDiffPath, prContext.fullDiff() == null ? "" : prContext.fullDiff());

        Map<String, String> diffByFile = DiffSplitter.splitFullDiffByFile(prContext.fullDiff());
        AnalyzedFileBuilder fileBuilder = new AnalyzedFileBuilder(github);
        List<AnalyzedFile> analyzedJavaFiles = fileBuilder.build(
                files,
                diffByFile,
                prContext,
                config.fetchContents(),
                config.fetchRelatedFiles(),
                config.maxFileContentChars(),
                config.maxRelatedFilesPerFile(),
                config.maxRelatedFileChars(),
                outFilesDir,
                config.debugSmells());

        ObjectWriter pretty = new ObjectMapper().writerWithDefaultPrettyPrinter();
        ReportRenderer.DiffReport report = ReportRenderer.buildDiffReport(
                config.repository(),
                config.prNumber(),
                allFiles.size(),
                files,
                config.maxFiles(),
                analyzedJavaFiles,
                fullDiffPath);
        Files.writeString(outDir.resolve("pr_diff.json"), pretty.writeValueAsString(report));

        SmellAnalyzer analyzer = new SmellAnalyzer(
                llm,
                config.maxFilesPerChunk(),
                config.maxChunkChars(),
                config.llmOptions(),
                config.debugSmells(),
                outDir.resolve("llm"),
                config.analysisScope(),
                config.validationMode());
        SmellAnalyzer.AnalysisResult analysis = analyzer.analyze(
                config.repository(),
                config.prNumber(),
                analyzedJavaFiles);

        // This is the auditable source for findings that survived analyzer validation.
        Files.writeString(outDir.resolve("validated_findings.json"), pretty.writeValueAsString(analysis.findings()));

        String markdown = ReportRenderer.renderMarkdown(analysis.findings(), analysis.warnings());
        Path githubCommentPath = outDir.resolve("github_comment.md");
        Files.writeString(githubCommentPath, markdown);

        String consoleReport = ReportRenderer.renderConsoleSummary(
                config.repository(),
                config.prNumber(),
                analyzedJavaFiles.size(),
                analysis.findings(),
                analysis.warnings(),
                analysis.chunksAnalyzed());
        System.out.println(consoleReport);

        if (config.postComment()) {
            github.postIssueComment(config.owner(), config.repo(), config.prNumber(), markdown);
        } else {
            System.out.println("\nGitHub-ready markdown saved to: " + githubCommentPath.toAbsolutePath());
        }
    }

    private static List<ChangedFile> limitFiles(List<ChangedFile> files, int maxFiles) {
        if (maxFiles <= 0 || files.size() <= maxFiles) {
            return files;
        }
        return new ArrayList<>(files.subList(0, maxFiles));
    }
}
