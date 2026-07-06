package dev.dimitra.bot.diff;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiffSplitter {
    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");

    private DiffSplitter() {
    }

    public static Map<String, String> splitFullDiffByFile(String fullDiff) {
        Map<String, String> diffsByFile = new LinkedHashMap<>();
        if (fullDiff == null || fullDiff.isBlank()) {
            return diffsByFile;
        }

        String currentFilePath = null;
        StringBuilder currentFileDiff = null;

        // Each "diff --git" line starts a new file section.
        for (String line : fullDiff.split("\\R", -1)) {
            Matcher headerMatcher = DIFF_HEADER.matcher(line);
            if (headerMatcher.matches()) {
                storeCurrentDiff(diffsByFile, currentFilePath, currentFileDiff);

                currentFilePath = chooseFilePath(headerMatcher);
                currentFileDiff = new StringBuilder();
            }

            if (currentFileDiff != null) {
                currentFileDiff.append(line).append("\n");
            }
        }

        storeCurrentDiff(diffsByFile, currentFilePath, currentFileDiff);

        return diffsByFile;
    }

    private static void storeCurrentDiff(
            Map<String, String> diffsByFile,
            String currentFilePath,
            StringBuilder currentFileDiff
    ) {
        if (currentFilePath != null && currentFileDiff != null) {
            diffsByFile.put(currentFilePath, currentFileDiff.toString().trim());
        }
    }

    private static String chooseFilePath(Matcher headerMatcher) {
        String leftPath = headerMatcher.group(1);
        String rightPath = headerMatcher.group(2);
        return "/dev/null".equals(rightPath) ? leftPath : rightPath;
    }
}
