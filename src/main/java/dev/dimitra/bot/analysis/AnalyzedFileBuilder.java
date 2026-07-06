package dev.dimitra.bot.analysis;

import dev.dimitra.bot.github.GitHubPrClient;
import dev.dimitra.bot.github.PullRequestContext;
import dev.dimitra.bot.github.RepoRef;
import dev.dimitra.bot.model.AnalyzedFile;
import dev.dimitra.bot.model.ChangedFile;
import dev.dimitra.bot.model.ContextFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzedFileBuilder {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;");

    private final GitHubPrClient github;

    public AnalyzedFileBuilder(GitHubPrClient github) {
        this.github = github;
    }

    public List<AnalyzedFile> build(List<ChangedFile> files,
            Map<String, String> diffByFile,
            PullRequestContext prContext,
            boolean fetchContents,
            boolean fetchRelatedFiles,
            int maxFileContentChars,
            int maxRelatedFilesPerFile,
            int maxRelatedFileChars,
            Path outFilesDir,
            boolean debugSmells) throws Exception {
        List<AnalyzedFile> analyzedFiles = new ArrayList<>();

        for (ChangedFile file : files) {
            if (file.filename() == null || !file.filename().endsWith(".java")) {
                continue;
            }

            String diffText = diffByFile.get(file.filename());
            if (diffText == null || diffText.isBlank()) {
                diffText = fallbackDiff(file);
            }

            String fullFileContent = null;
            if (fetchContents) {
                fullFileContent = resolvePrimaryFileContent(prContext, file, debugSmells);
                if (fullFileContent != null && !fullFileContent.isBlank()) {
                    writeArtifactFile(outFilesDir, file.filename(), fullFileContent);
                }
            }

            List<ContextFile> supportingFiles = List.of();
            if (fetchContents
                    && fetchRelatedFiles
                    && fullFileContent != null
                    && !fullFileContent.isBlank()
                    && maxRelatedFilesPerFile > 0) {
                supportingFiles = loadSupportingFiles(
                        prContext,
                        file,
                        fullFileContent,
                        maxRelatedFilesPerFile,
                        maxRelatedFileChars,
                        outFilesDir,
                        debugSmells);
            }

            analyzedFiles.add(new AnalyzedFile(
                    file.filename(),
                    file.status(),
                    file.previousFilename(),
                    file.additions(),
                    file.deletions(),
                    file.changes(),
                    diffText,
                    trimForPrompt(fullFileContent, maxFileContentChars),
                    supportingFiles));
        }

        return analyzedFiles;
    }

    private List<ContextFile> loadSupportingFiles(PullRequestContext prContext,
            ChangedFile changedFile,
            String currentFileContent,
            int maxRelatedFilesPerFile,
            int maxRelatedFileChars,
            Path outFilesDir,
            boolean debugSmells) throws Exception {
        String sourceRoot = inferJavaSourceRoot(changedFile.filename());
        if (sourceRoot == null) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        Matcher matcher = IMPORT_PATTERN.matcher(currentFileContent);
        while (matcher.find()) {
            String importedType = matcher.group(1);
            if (importedType.endsWith(".*")) {
                continue;
            }
            String candidatePath = sourceRoot + importedType.replace('.', '/') + ".java";
            if (!candidatePath.equals(changedFile.filename())) {
                candidates.add(candidatePath);
            }
        }

        List<ContextFile> supportingFiles = new ArrayList<>();
        for (String candidatePath : candidates) {
            String content = readLocalFileIfPresent(candidatePath);
            if (content == null) {
                content = github.fetchRepoFileContent(prContext.head(), candidatePath, true);
            }
            if (content == null || content.isBlank()) {
                continue;
            }

            writeArtifactFile(outFilesDir, candidatePath, content);
            supportingFiles.add(new ContextFile(
                    candidatePath,
                    "imported by " + changedFile.filename(),
                    trimForPrompt(content, maxRelatedFileChars)));

            if (supportingFiles.size() >= maxRelatedFilesPerFile) {
                break;
            }
        }

        if (debugSmells && !supportingFiles.isEmpty()) {
            System.out.println("Loaded " + supportingFiles.size() + " supporting files for " + changedFile.filename());
        }

        return supportingFiles;
    }

    private String resolvePrimaryFileContent(PullRequestContext prContext,
            ChangedFile file,
            boolean debugSmells) throws Exception {
        boolean removed = "removed".equalsIgnoreCase(file.status());
        String targetPath = removed && file.previousFilename() != null && !file.previousFilename().isBlank()
                ? file.previousFilename()
                : file.filename();

        if (!removed) {
            String local = readLocalFileIfPresent(file.filename());
            if (local != null) {
                return local;
            }
        }

        RepoRef repoRef = removed ? prContext.base() : prContext.head();
        String fetched = github.fetchRepoFileContent(repoRef, targetPath, true);

        if (debugSmells && fetched == null) {
            System.out.println("No full file content available for " + targetPath);
        }

        return fetched;
    }

    private static String readLocalFileIfPresent(String relativePath) throws Exception {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path localPath = Paths.get(relativePath);
        if (!Files.isRegularFile(localPath)) {
            return null;
        }
        return Files.readString(localPath);
    }

    private static void writeArtifactFile(Path root, String relativePath, String content) throws Exception {
        if (relativePath == null || relativePath.isBlank() || content == null) {
            return;
        }

        Path resolved = root.resolve(relativePath.replace('/', File.separatorChar)).normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!resolved.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
            return;
        }

        Files.createDirectories(resolved.getParent());
        Files.writeString(resolved, content);
    }

    private static String fallbackDiff(ChangedFile file) {
        if (file.patch() == null || file.patch().isBlank()) {
            return "";
        }
        return "=== FILE: " + nvl(file.filename(), "?") + " (" + nvl(file.status(), "?") + ") ===\n" + file.patch();
    }

    private static String inferJavaSourceRoot(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int idx = normalized.indexOf("/java/");
        if (idx < 0) {
            return null;
        }
        return normalized.substring(0, idx + "/java/".length());
    }

    private static String trimForPrompt(String value, int maxChars) {
        if (value == null || value.isBlank() || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n... [truncated]";
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
