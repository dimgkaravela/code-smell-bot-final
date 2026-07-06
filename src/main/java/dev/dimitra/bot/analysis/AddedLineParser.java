package dev.dimitra.bot.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddedLineParser {
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@.*$");

    public Map<Integer, String> parse(String diff) {
        Map<Integer, String> addedLines = new LinkedHashMap<>();
        if (diff == null || diff.isBlank()) {
            return addedLines;
        }

        int newLine = 0;
        boolean inHunk = false;
        for (String diffLine : diff.split("\\R", -1)) {
            Matcher hunk = HUNK_HEADER.matcher(diffLine);
            if (hunk.matches()) {
                // Unified diff hunks define the next line number on the new-file side.
                newLine = Integer.parseInt(hunk.group(1));
                inHunk = true;
                continue;
            }

            if (!inHunk || diffLine.isEmpty() || diffLine.startsWith("\\")) {
                continue;
            }

            char marker = diffLine.charAt(0);
            if (marker == '+') {
                // Only '+' hunk lines are valid anchors for findings on new/modified code.
                addedLines.put(newLine, diffLine.substring(1));
                newLine++;
            } else if (marker == ' ') {
                newLine++;
            }
        }

        return addedLines;
    }
}
