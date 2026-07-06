package dev.dimitra.bot.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimitra.bot.model.AnalyzedFile;
import dev.dimitra.bot.model.ContextFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class EvaluationDatasetLoader {
    private static final String TRUNCATION_MARKER = "\n... [truncated]";
    private static final List<Charset> SOURCE_CHARSETS = List.of(
            StandardCharsets.UTF_8,
            Charset.forName("windows-1253"),
            StandardCharsets.ISO_8859_1);

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final int maxFileContentChars;

    public EvaluationDatasetLoader() {
        this(0);
    }

    public EvaluationDatasetLoader(int maxFileContentChars) {
        this.maxFileContentChars = maxFileContentChars;
    }

    public List<LoadedCase> load(Path datasetRoot) throws IOException {
        Path casesRoot = Files.isDirectory(datasetRoot.resolve("cases"))
                ? datasetRoot.resolve("cases")
                : datasetRoot;

        if (!Files.isDirectory(casesRoot)) {
            throw new IOException("Evaluation dataset directory does not exist: " + casesRoot);
        }

        try (Stream<Path> paths = Files.walk(casesRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(this::loadCase)
                    .toList();
        }
    }

    private LoadedCase loadCase(Path path) {
        try {
            EvaluationCase evaluationCase = mapper.readValue(path.toFile(), EvaluationCase.class);
            List<AnalyzedFile> files = toAnalyzedFiles(evaluationCase, path.getParent());
            return new LoadedCase(path, evaluationCase, files);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load evaluation case " + path + ": " + e.getMessage(), e);
        }
    }

    private List<AnalyzedFile> toAnalyzedFiles(EvaluationCase evaluationCase, Path caseDir) throws IOException {
        List<EvaluationCase.EvaluationFile> files = evaluationCase.files() == null
                ? List.of()
                : evaluationCase.files();

        return files.stream()
                .map(file -> toAnalyzedFile(file, caseDir))
                .filter(Objects::nonNull)
                .toList();
    }

    private AnalyzedFile toAnalyzedFile(EvaluationCase.EvaluationFile file, Path caseDir) {
        if (file == null || isBlank(file.filename())) {
            return null;
        }

        try {
            String fileContent = firstNonBlank(
                    file.fileContent(),
                    readRelative(caseDir, file.fileContentPath()));
            fileContent = trimForPrompt(fileContent, maxFileContentChars);
            String diff = firstNonBlank(
                    file.diff(),
                    readRelative(caseDir, file.diffPath()));
            boolean syntheticDiff = false;

            if (isBlank(diff)) {
                diff = syntheticAllAddedDiff(file.filename(), fileContent);
                syntheticDiff = true;
            }

            List<ContextFile> supportingFiles = file.supportingFiles() == null
                    ? List.of()
                    : file.supportingFiles();

            return new AnalyzedFile(
                    file.filename(),
                    firstNonBlank(file.status(), "added"),
                    file.previousFilename(),
                    file.additions() == null ? countAddedLines(diff) : file.additions(),
                    file.deletions() == null ? 0 : file.deletions(),
                    file.changes() == null ? countAddedLines(diff) : file.changes(),
                    diff,
                    fileContent,
                    supportingFiles,
                    syntheticDiff);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load file for evaluation case: " + file.filename(), e);
        }
    }

    private static String syntheticAllAddedDiff(String filename, String fileContent) {
        String safeContent = fileContent == null ? "" : fileContent;
        List<String> lines = safeContent.lines().toList();
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(filename).append(" b/").append(filename).append("\n");
        diff.append("new file mode 100644\n");
        diff.append("index 0000000..1111111\n");
        diff.append("--- /dev/null\n");
        diff.append("+++ b/").append(filename).append("\n");
        diff.append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
        for (String line : lines) {
            diff.append("+").append(line).append("\n");
        }
        return diff.toString();
    }

    private static String readRelative(Path caseDir, String relativePath) throws IOException {
        if (isBlank(relativePath)) {
            return null;
        }
        Path path = caseDir.resolve(relativePath).normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Referenced file does not exist: " + path);
        }
        return readTextFile(path);
    }

    private static String readTextFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        CharacterCodingException lastFailure = null;

        for (Charset charset : SOURCE_CHARSETS) {
            try {
                return charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                lastFailure = e;
            }
        }

        throw new IOException("Could not decode text file with supported charsets: " + path, lastFailure);
    }

    private static int countAddedLines(String diff) {
        if (diff == null || diff.isBlank()) {
            return 0;
        }

        int count = 0;
        for (String line : diff.split("\\R", -1)) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                count++;
            }
        }
        return count;
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static String trimForPrompt(String value, int maxChars) {
        if (value == null || value.isBlank() || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + TRUNCATION_MARKER;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record LoadedCase(
            Path path,
            EvaluationCase evaluationCase,
            List<AnalyzedFile> analyzedFiles) {
    }
}
