package dev.dimitra.bot.report;

import dev.dimitra.bot.llm.LlmFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRendererTest {
    @Test
    void renderMarkdownKeepsFindingsTableSeparateFromRefactoringDetails() {
        List<LlmFinding> findings = List.of(
                new LlmFinding(
                        "experiments/smellycode/Cashier.java",
                        8,
                        "Large Class",
                        "Major",
                        "The class mixes checkout | customer contact work.",
                        "Extract Class",
                        "Extract customer-related fields and methods into a separate customer class."),
                new LlmFinding(
                        "experiments/smellycode/Cashier.java",
                        87,
                        "Long Parameter List",
                        "Major",
                        "The method accepts five individual string parameters.",
                        "Introduce Parameter Object",
                        "Encapsulate the contact fields into a ContactInfo object."));

        String markdown = ReportRenderer.renderMarkdown(findings, List.of());

        int tableHeader = markdown.indexOf("| File | Line | Rule | Severity | Note | Suggested Refactoring |");
        int firstRow = markdown.indexOf("| experiments/smellycode/Cashier.java | 8 | Large Class | Major | "
                + "The class mixes checkout \\| customer contact work. | Extract Class |");
        int secondRow = markdown.indexOf("| experiments/smellycode/Cashier.java | 87 | Long Parameter List | Major | "
                + "The method accepts five individual string parameters. | Introduce Parameter Object |");
        int notesHeading = markdown.indexOf("### Refactoring notes");
        int firstDetails = markdown.indexOf("<details>");

        assertTrue(tableHeader >= 0);
        assertTrue(firstRow > tableHeader);
        assertTrue(secondRow > firstRow);
        assertTrue(notesHeading > secondRow);
        assertTrue(firstDetails > notesHeading);
        assertFalse(markdown.substring(tableHeader, notesHeading).contains("<details>"));
        assertFalse(markdown.contains("```"));
        assertFalse(markdown.contains("<pre"));
        assertTrue(markdown.contains("<summary>experiments/smellycode/Cashier.java:8 \u2014 Large Class</summary>"));
        assertTrue(markdown.contains("Extract customer-related fields and methods into a separate customer class.\n\n"
                + "</details>"));
        assertTrue(markdown.contains("<summary>experiments/smellycode/Cashier.java:87 \u2014 Long Parameter List"
                + "</summary>"));
        assertTrue(markdown.contains("Encapsulate the contact fields into a ContactInfo object.\n\n</details>"));
    }

    @Test
    void renderMarkdownSkipsRefactoringSectionWhenNoNotesExist() {
        List<LlmFinding> findings = List.of(new LlmFinding(
                "src/Foo.java",
                10,
                "Long Method",
                "Major",
                "The method does too much.",
                "Extract Method",
                ""));

        String markdown = ReportRenderer.renderMarkdown(findings, List.of());

        assertFalse(markdown.contains("### Refactoring notes"));
        assertFalse(markdown.contains("<details>"));
    }
}
