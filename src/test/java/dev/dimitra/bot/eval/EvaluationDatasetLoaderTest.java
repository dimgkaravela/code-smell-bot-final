package dev.dimitra.bot.eval;

import dev.dimitra.bot.eval.EvaluationDatasetLoader.LoadedCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationDatasetLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsDatasetFromCasesDirectory() throws IOException {
        Path casesDir = tempDir.resolve("cases");
        Path filesDir = tempDir.resolve("files");
        Path diffsDir = tempDir.resolve("diffs");
        Files.createDirectories(casesDir);
        Files.createDirectories(filesDir);
        Files.createDirectories(diffsDir);

        Files.writeString(filesDir.resolve("OrderService.java"), """
                public class OrderService {
                    void createOrder() { calculate(); persist(); }
                }
                """);
        Files.writeString(diffsDir.resolve("order.diff"), """
                diff --git a/src/OrderService.java b/src/OrderService.java
                index 1111111..2222222 100644
                --- a/src/OrderService.java
                +++ b/src/OrderService.java
                @@ -1,2 +1,3 @@
                 public class OrderService {
                +    void createOrder() { calculate(); persist(); }
                 }
                """);

        Files.writeString(casesDir.resolve("order-service.json"), """
                {
                  "id": "order-service",
                  "source": "unit-test",
                  "repository": "local/repo",
                  "prNumber": 7,
                  "files": [
                    {
                      "filename": "src/OrderService.java",
                      "status": "modified",
                      "previousFilename": "src/OldOrderService.java",
                      "diffPath": "../diffs/order.diff",
                      "fileContentPath": "../files/OrderService.java"
                    }
                  ],
                  "labels": [
                    {
                      "file": "src/OrderService.java",
                      "line": 2,
                      "rule": "Long Method",
                      "targetType": "METHOD",
                      "targetName": "createOrder",
                      "severity": "Major",
                      "note": "The method mixes calculation and persistence.",
                      "suggestedRefactoring": "Extract Method",
                      "refactoringNote": "Extract calculation and persistence into helpers."
                    }
                  ]
                }
                """);

        List<LoadedCase> cases = new EvaluationDatasetLoader().load(tempDir);

        assertEquals(1, cases.size());
        LoadedCase loadedCase = cases.get(0);
        assertEquals("order-service", loadedCase.evaluationCase().id());
        assertEquals("local/repo", loadedCase.evaluationCase().repository());
        assertEquals(7, loadedCase.evaluationCase().prNumber());

        EvaluationCase.EvaluationLabel label = loadedCase.evaluationCase().labels().get(0);
        assertEquals("src/OrderService.java", label.file());
        assertEquals("METHOD", label.targetType());
        assertEquals("createOrder", label.targetName());

        assertEquals(1, loadedCase.analyzedFiles().size());
        var analyzedFile = loadedCase.analyzedFiles().get(0);
        assertEquals("src/OrderService.java", analyzedFile.filename());
        assertEquals("modified", analyzedFile.status());
        assertEquals("src/OldOrderService.java", analyzedFile.previousFilename());
        assertEquals(1, analyzedFile.additions());
        assertEquals(1, analyzedFile.changes());
        assertTrue(analyzedFile.fileContent().contains("void createOrder()"));
        assertTrue(analyzedFile.diff().contains("+    void createOrder()"));
    }

    @Test
    void loadsLegacyEncodedSourceFiles() throws IOException {
        Path casesDir = tempDir.resolve("cases");
        Path filesDir = tempDir.resolve("files");
        Files.createDirectories(casesDir);
        Files.createDirectories(filesDir);

        Path sourceFile = filesDir.resolve("Legacy.java");
        byte[] legacyBytes = "class Legacy {\n    // ".getBytes(Charset.forName("windows-1253"));
        byte[] alphaBytes = new byte[] {(byte) 0xe1};
        byte[] suffixBytes = "\n}\n".getBytes(Charset.forName("windows-1253"));
        Files.write(sourceFile, join(legacyBytes, alphaBytes, suffixBytes));

        Files.writeString(casesDir.resolve("legacy.json"), """
                {
                  "id": "legacy",
                  "files": [
                    {
                      "filename": "src/Legacy.java",
                      "fileContentPath": "../files/Legacy.java"
                    }
                  ],
                  "labels": []
                }
                """);

        List<LoadedCase> cases = new EvaluationDatasetLoader().load(tempDir);

        assertEquals(1, cases.size());
        assertEquals(new String(alphaBytes, Charset.forName("windows-1253")),
                cases.get(0).analyzedFiles().get(0).fileContent().lines().toList().get(1).substring(7));
    }

    @Test
    void trimsLongFileContentWhenMaxFileContentCharsIsSet() throws IOException {
        Path casesDir = tempDir.resolve("cases");
        Path filesDir = tempDir.resolve("files");
        Files.createDirectories(casesDir);
        Files.createDirectories(filesDir);

        Files.writeString(filesDir.resolve("Large.java"), """
                public class Large {
                    void first() {}
                    void second() {}
                }
                """);
        Files.writeString(casesDir.resolve("large.json"), """
                {
                  "id": "large",
                  "files": [
                    {
                      "filename": "src/Large.java",
                      "fileContentPath": "../files/Large.java"
                    }
                  ],
                  "labels": []
                }
                """);

        EvaluationOptions options = EvaluationOptions.parse(
                new String[] {"--dataset", tempDir.toString()},
                Map.of("MAX_FILE_CONTENT_CHARS", "25"));
        List<LoadedCase> cases = new EvaluationDatasetLoader(options.maxFileContentChars()).load(options.datasetDir());

        assertEquals(25, options.maxFileContentChars());
        var analyzedFile = cases.get(0).analyzedFiles().get(0);
        assertTrue(analyzedFile.fileContent().endsWith("\n... [truncated]"));
        assertFalse(analyzedFile.fileContent().contains("void second()"));
        assertTrue(analyzedFile.syntheticDiff());
        assertTrue(analyzedFile.diff().contains("... [truncated]"));
    }

    private static byte[] join(byte[] first, byte[] second, byte[] third) {
        byte[] joined = new byte[first.length + second.length + third.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        System.arraycopy(third, 0, joined, first.length + second.length, third.length);
        return joined;
    }
}
