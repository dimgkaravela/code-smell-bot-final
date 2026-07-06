package dev.dimitra.bot.analysis;

import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.model.AnalyzedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmellAnalyzerTest {
    private static final String TOKEN_USAGE_HEADER = "dataset,caseId,scope,validationMode,matchMode,model,"
            + "maxFilesPerChunk,maxChunkChars,maxFileContentChars,chunkIndex,filesInChunk,promptChars,"
            + "promptTokens,outputTokens,totalTokens,finishReason,parseOk,warningCount";

    @Test
    void filtersNonJavaFiles() throws Exception {
        FakeLlmClient llm = new FakeLlmClient("[]");
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.DIFF);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(analyzedFile("README.md", "", "")));

        assertEquals(0, llm.callCount());
        assertEquals(0, result.chunksAnalyzed());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void strictLineDiffPromptRequiresAddedLineAnchoring() {
        String prompt = new PromptRenderer(AnalysisScope.DIFF, ValidationMode.STRICT_LINE).systemPrompt();

        assertTrue(prompt.contains("The line must be a new-file line number from an added/modified diff line."));
        assertFalse(prompt.contains("\"targetType\": \"METHOD|CLASS\""));
        assertFalse(prompt.contains("Every finding must include targetType and targetName."));
    }

    @Test
    void targetDiffPromptRequiresTargetIdentityAndMakesLineSecondary() {
        String prompt = new PromptRenderer(AnalysisScope.DIFF, ValidationMode.DIFF_TARGET_NAME).systemPrompt();

        assertTrue(prompt.contains("\"targetType\": \"METHOD|CLASS\""));
        assertTrue(prompt.contains("Every finding must include targetType and targetName."));
        assertTrue(prompt.contains(
                "The line is for anchoring/reporting; targetType and targetName are the primary smell identity."));
        assertTrue(prompt.contains("The target method/class must overlap added/modified diff lines."));
    }

    @Test
    void fileScopePromptOmitsSyntheticAllAddedDiff() {
        AnalyzedFile file = new AnalyzedFile(
                "src/Foo.java",
                "added",
                null,
                2,
                0,
                2,
                syntheticDiff("src/Foo.java", "public class Foo {\n    void work() {}\n}"),
                "public class Foo {\n    void work() {}\n}",
                List.of(),
                true);

        String prompt = new PromptRenderer(AnalysisScope.FILE, ValidationMode.TARGET_NAME).renderFileBlock(file);

        assertTrue(prompt.contains("=== CURRENT FILE CONTENT: src/Foo.java ==="));
        assertTrue(prompt.contains("void work()"));
        assertFalse(prompt.contains("diff --git"));
        assertFalse(prompt.contains("+    void work()"));
    }

    @Test
    void fileScopePromptKeepsRealDiffWhenPresent() {
        String diff = """
                diff --git a/src/Foo.java b/src/Foo.java
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void work() {}
                 }
                """;
        AnalyzedFile file = new AnalyzedFile(
                "src/Foo.java",
                "modified",
                null,
                1,
                0,
                1,
                diff,
                "public class Foo {\n    void work() {}\n}",
                List.of(),
                false);

        String prompt = new PromptRenderer(AnalysisScope.FILE, ValidationMode.TARGET_NAME).renderFileBlock(file);

        assertTrue(prompt.contains("diff --git a/src/Foo.java b/src/Foo.java"));
        assertTrue(prompt.contains("+    void work() {}"));
        assertTrue(prompt.contains("=== CURRENT FILE CONTENT: src/Foo.java ==="));
    }

    @Test
    void parsesPlainJsonAndFencedJson() throws Exception {
        FakeLlmClient llm = new FakeLlmClient(
                jsonFinding("src/Foo.java", 2),
                """
                ```json
                [
                  {
                    "file": "src/Bar.java",
                    "line": 2,
                    "rule": "Long Method",
                    "targetType": "METHOD",
                    "targetName": "longMethod",
                    "severity": "Minor",
                    "note": "The longMethod mixes several steps in one method.",
                    "suggestedRefactoring": "Extract Method",
                    "refactoringNote": "Extract each step into a named helper method."
                  }
                ]
                ```
                """);
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.DIFF, 1);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(
                        javaFile("src/Foo.java"),
                        javaFile("src/Bar.java")));

        assertEquals(2, llm.callCount());
        assertEquals(2, result.chunksAnalyzed());
        assertEquals(2, result.findings().size());
        assertEquals("src/Foo.java", result.findings().get(0).file());
        assertEquals("src/Bar.java", result.findings().get(1).file());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void invalidJsonCreatesWarningAndSkipsChunk() throws Exception {
        FakeLlmClient llm = new FakeLlmClient("not json");
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.DIFF);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(javaFile("src/Foo.java")));

        assertEquals(1, result.chunksAnalyzed());
        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("Chunk 1/1 returned invalid JSON. Findings from this chunk were skipped.",
                result.warnings().get(0));
    }

    @Test
    void writesTokenUsageCsvWithHeaderAndUsageMetadata(@TempDir Path outputDir) throws Exception {
        FakeLlmClient llm = new FakeLlmClient(
                new LlmClient.Result("[]", new LlmClient.Usage(11, 7, 18, "STOP")));
        SmellAnalyzer analyzer = analyzerWithTokenUsageDir(llm, outputDir);

        analyzer.analyze(
                "owner/repo",
                12,
                List.of(javaFile("src/Foo.java")),
                new SmellAnalyzer.AnalysisMetadata("dataset-a", "case-1", "target", 12_000));

        String[] cells = tokenUsageCells(outputDir);
        assertEquals("dataset-a", cells[0]);
        assertEquals("case-1", cells[1]);
        assertEquals("file", cells[2]);
        assertEquals("target-name", cells[3]);
        assertEquals("target", cells[4]);
        assertEquals("fake-model", cells[5]);
        assertEquals("4", cells[6]);
        assertEquals("18000", cells[7]);
        assertEquals("12000", cells[8]);
        assertEquals("1", cells[9]);
        assertEquals("1", cells[10]);
        assertTrue(Integer.parseInt(cells[11]) > 0);
        assertEquals("11", cells[12]);
        assertEquals("7", cells[13]);
        assertEquals("18", cells[14]);
        assertEquals("STOP", cells[15]);
        assertEquals("true", cells[16]);
        assertEquals("0", cells[17]);
    }

    @Test
    void tokenUsageCsvLeavesNullTokenMetadataEmpty(@TempDir Path outputDir) throws Exception {
        FakeLlmClient llm = new FakeLlmClient(new LlmClient.Result("[]", null));
        SmellAnalyzer analyzer = analyzerWithTokenUsageDir(llm, outputDir);

        analyzer.analyze("owner/repo", 12, List.of(javaFile("src/Foo.java")));

        String[] cells = tokenUsageCells(outputDir);
        assertEquals("", cells[0]);
        assertEquals("", cells[1]);
        assertEquals("", cells[4]);
        assertEquals("0", cells[8]);
        assertEquals("", cells[12]);
        assertEquals("", cells[13]);
        assertEquals("", cells[14]);
        assertEquals("", cells[15]);
        assertEquals("true", cells[16]);
        assertEquals("0", cells[17]);
    }

    @Test
    void tokenUsageCsvRecordsInvalidJsonAsParseFailure(@TempDir Path outputDir) throws Exception {
        FakeLlmClient llm = new FakeLlmClient(
                new LlmClient.Result("not json", new LlmClient.Usage(3, 2, 5, "MAX_TOKENS")));
        SmellAnalyzer analyzer = analyzerWithTokenUsageDir(llm, outputDir);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(javaFile("src/Foo.java")));

        String[] cells = tokenUsageCells(outputDir);
        assertEquals(1, result.warnings().size());
        assertEquals("3", cells[12]);
        assertEquals("2", cells[13]);
        assertEquals("5", cells[14]);
        assertEquals("MAX_TOKENS", cells[15]);
        assertEquals("false", cells[16]);
        assertEquals("1", cells[17]);
    }

    @Test
    void duplicateFindingsAreMerged() throws Exception {
        FakeLlmClient llm = new FakeLlmClient("""
                [
                  {
                    "file": "src/Foo.java",
                    "line": 2,
                    "rule": "Long Method",
                    "targetType": "METHOD",
                    "targetName": "longMethod",
                    "severity": "Minor",
                    "note": "The first explanation identifies a method doing too much.",
                    "suggestedRefactoring": "Extract Method",
                    "refactoringNote": "Extract each step into a named helper method."
                  },
                  {
                    "file": "src/Foo.java",
                    "line": 2,
                    "rule": "Long Method",
                    "targetType": "METHOD",
                    "targetName": "longMethod",
                    "severity": "Major",
                    "note": "The duplicate explanation should be merged away.",
                    "suggestedRefactoring": "Extract Method",
                    "refactoringNote": "Extract each step into a named helper method."
                  }
                ]
                """);
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.DIFF);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(javaFile("src/Foo.java")));

        assertEquals(1, result.findings().size());
        assertEquals("The first explanation identifies a method doing too much.", result.findings().get(0).note());
    }

    @Test
    void diffScopeRejectsNonAddedLineFindings() throws Exception {
        FakeLlmClient llm = new FakeLlmClient("""
                [
                  {
                    "file": "src/Foo.java",
                    "line": 1,
                    "rule": "Long Method",
                    "targetType": "METHOD",
                    "targetName": "longMethod",
                    "severity": "Minor",
                    "note": "The longMethod mixes several steps in one method.",
                    "suggestedRefactoring": "Extract Method",
                    "refactoringNote": "Extract each step into a named helper method."
                  }
                ]
                """);
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.DIFF, 4, ValidationMode.STRICT_LINE);
        AnalyzedFile file = analyzedFile("src/Foo.java", """
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    int count = 1;
                 }
                """, "");

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(file));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void fileScopeAcceptsValidFullFileFindings() throws Exception {
        FakeLlmClient llm = new FakeLlmClient(jsonFinding("src/Foo.java", 2));
        SmellAnalyzer analyzer = analyzer(llm, AnalysisScope.FILE);
        AnalyzedFile file = analyzedFile("src/Foo.java", "", """
                public class Foo {
                    void longMethod() { calculate(); persist(); }
                    void ok() {}
                }
                """);

        SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                "owner/repo",
                12,
                List.of(file));

        assertEquals(1, result.findings().size());
        assertEquals("src/Foo.java", result.findings().get(0).file());
        assertEquals(2, result.findings().get(0).line());
        assertTrue(result.warnings().isEmpty());
    }

    private static SmellAnalyzer analyzer(FakeLlmClient llm, AnalysisScope scope) {
        return analyzer(llm, scope, 4);
    }

    private static SmellAnalyzer analyzer(FakeLlmClient llm, AnalysisScope scope, int maxFilesPerChunk) {
        return analyzer(llm, scope, maxFilesPerChunk, ValidationMode.defaultForScope(scope));
    }

    private static SmellAnalyzer analyzer(
            FakeLlmClient llm,
            AnalysisScope scope,
            int maxFilesPerChunk,
            ValidationMode validationMode) {
        return new SmellAnalyzer(
                llm,
                maxFilesPerChunk,
                18_000,
                Map.of(),
                false,
                null,
                scope,
                validationMode);
    }

    private static SmellAnalyzer analyzerWithTokenUsageDir(FakeLlmClient llm, Path outputDir) {
        return new SmellAnalyzer(
                llm,
                4,
                18_000,
                Map.of(),
                false,
                null,
                AnalysisScope.FILE,
                ValidationMode.TARGET_NAME,
                outputDir);
    }

    private static String[] tokenUsageCells(Path outputDir) throws IOException {
        List<String> lines = Files.readAllLines(outputDir.resolve("token-usage.csv"));
        assertEquals(2, lines.size());
        assertEquals(TOKEN_USAGE_HEADER, lines.get(0));
        String[] cells = lines.get(1).split(",", -1);
        assertEquals(18, cells.length);
        return cells;
    }

    private static AnalyzedFile javaFile(String filename) {
        return analyzedFile(filename, """
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); }
                 }
                """, """
                public class Foo {
                    void longMethod() { calculate(); persist(); }
                }
                """);
    }

    private static AnalyzedFile analyzedFile(String filename, String diff, String fileContent) {
        return new AnalyzedFile(
                filename,
                "modified",
                null,
                1,
                0,
                1,
                diff,
                fileContent,
                List.of());
    }

    private static String syntheticDiff(String filename, String fileContent) {
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(filename).append(" b/").append(filename).append("\n");
        diff.append("--- /dev/null\n");
        diff.append("+++ b/").append(filename).append("\n");
        for (String line : fileContent.split("\\R", -1)) {
            diff.append("+").append(line).append("\n");
        }
        return diff.toString();
    }

    private static String jsonFinding(String file, int line) {
        return """
                [
                  {
                    "file": "%s",
                    "line": %d,
                    "rule": "Long Method",
                    "targetType": "METHOD",
                    "targetName": "longMethod",
                    "severity": "Minor",
                    "note": "The longMethod mixes several steps in one method.",
                    "suggestedRefactoring": "Extract Method",
                    "refactoringNote": "Extract each step into a named helper method."
                  }
                ]
                """.formatted(file, line);
    }

    private static final class FakeLlmClient implements LlmClient {
        private final List<Result> responses;
        private final List<List<Message>> requests = new ArrayList<>();

        private FakeLlmClient(String... responses) {
            this.responses = new ArrayList<>();
            for (String response : responses) {
                this.responses.add(new Result(response, null));
            }
        }

        private FakeLlmClient(Result... responses) {
            this.responses = new ArrayList<>(Arrays.asList(responses));
        }

        @Override
        public Result chat(String systemPrompt, List<Message> messages, Map<String, Object> options)
                throws IOException, InterruptedException {
            requests.add(messages);
            if (responses.isEmpty()) {
                throw new AssertionError("Unexpected LLM call");
            }
            return responses.remove(0);
        }

        @Override
        public String modelName() {
            return "fake-model";
        }

        private int callCount() {
            return requests.size();
        }
    }
}
