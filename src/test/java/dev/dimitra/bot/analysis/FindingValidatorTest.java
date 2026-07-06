package dev.dimitra.bot.analysis;

import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.model.AnalyzedFile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingValidatorTest {
    @Test
    void diffScopeRequiresAddedLineAnchor() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                diff --git a/src/Foo.java b/src/Foo.java
                --- a/src/Foo.java
                +++ b/src/Foo.java
                @@ -1,3 +1,4 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); notifyUser(); }
                     void ok() {}
                 }
                """, """
                public class Foo {
                    void longMethod() { calculate(); persist(); notifyUser(); }
                    void ok() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(new LlmFinding(
                "src/Foo.java",
                10,
                "Long Method",
                "Minor",
                "The longMethod mixes calculation, persistence, and notification steps.",
                "Extract Method",
                "Extract each step into a named helper method.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void diffScopeAcceptsFindingOnAddedLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); notifyUser(); }
                 }
                """, "");

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                2,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void diffScopeReanchorsUnchangedLineToNearbyAddedLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); notifyUser(); }
                 }
                """, "");

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                1,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void diffScopeRejectsFindingOnDeletedLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,4 +1,4 @@
                 public class Foo {
                -    void longMethod() { calculate(); persist(); notifyUser(); }
                     void ok() {}
                +    int count = 1;
                 }
                """, "");

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                2,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void diffScopeReanchorsFindingWithinThreeLinesOfAddedLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -10,3 +10,4 @@
                +void longMethod() { calculate(); persist(); notifyUser(); }
                 class Foo {
                     void ok() {}
                 }
                """, "");

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                13,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(10, validated.get(0).line());
    }

    @Test
    void diffScopeRejectsFindingTooFarFromAddedLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); notifyUser(); }
                 }
                """, "");

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                6,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void fileScopeAcceptsWholeFileLineAnchor() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() { calculate(); persist(); notifyUser(); }
                    void ok() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(new LlmFinding(
                "src/Foo.java",
                2,
                "Long Method",
                "Minor",
                "The longMethod mixes calculation, persistence, and notification steps.",
                "Extract Method",
                "Extract each step into a named helper method.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void fileScopeRejectsOutOfRangeLineWithoutNearbyMatch() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void ok() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                20,
                "The method contains unrelated branching.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void fileScopeReanchorsOutOfRangeLineToNearbyRelatedMatch() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void ok() {}
                    void longMethod() { calculate(); persist(); notifyUser(); }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                5,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(3, validated.get(0).line());
    }

    @Test
    void fileScopeReanchorsMethodLevelFindingToNamedMethodDeclaration() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Customer {
                    public void orderWithUnnecessaryDetails(String pizzaType, String size, String crustType, String toppings,
                            boolean extraCheese, String discountCode) {
                        this.orderPizza(pizzaType);
                    }

                    public void duplicateComplaint() {
                        this.complain("Pizza is cold");
                        this.complain("Pizza is cold");
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(new LlmFinding(
                "src/Foo.java",
                8,
                "Long Parameter List",
                "Major",
                "The method orderWithUnnecessaryDetails accepts six related scalar parameters.",
                "Introduce Parameter Object",
                "Create an OrderDetails object for orderWithUnnecessaryDetails.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void fileScopeReanchorsLargeClassFindingToNamedClassDeclaration() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class UserController {
                    public void showProfile() {}
                    public void searchBooks() {}
                    public void recommendBooks() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(new LlmFinding(
                "src/Foo.java",
                4,
                "Large Class",
                "Major",
                "UserController mixes profile, search, and recommendation responsibilities.",
                "Extract Class",
                "Split UserController into focused controllers.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(1, validated.get(0).line());
    }

    @Test
    void fileScopeKeepsMessageChainOnChainedExpressionLine() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Customer {
                    public void chainOfMethods() {
                        this.pizzaShop.getCashier().getChef().cleanKitchen();
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(new LlmFinding(
                "src/Foo.java",
                3,
                "Message Chains",
                "Minor",
                "The method chainOfMethods navigates from pizzaShop to cashier to chef.",
                "Hide Delegate",
                "Add a Shop method that hides the cashier and chef navigation.")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(3, validated.get(0).line());
    }

    @Test
    void fileScopeRejectsWrongFile() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() { calculate(); persist(); notifyUser(); }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Other.java",
                2,
                "The longMethod mixes calculation, persistence, and notification steps.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void fileScopeRejectsNullOrBlankRequiredFields() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() { calculate(); persist(); notifyUser(); }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(Arrays.asList(
                null,
                new LlmFinding(null, 2, "Long Method", "Minor", "The method does too much.", "Extract Method",
                        "Extract each step."),
                new LlmFinding(" ", 2, "Long Method", "Minor", "The method does too much.", "Extract Method",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, null, "Minor", "The method does too much.", "Extract Method",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, " ", "Minor", "The method does too much.", "Extract Method",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Commented Out Code", "Minor", "The method does too much.",
                        "Extract Method", "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", null, "The method does too much.",
                        "Extract Method", "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", " ", "The method does too much.",
                        "Extract Method", "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", null, "Extract Method",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", " ", "Extract Method",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", "The method does too much.", null,
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", "The method does too much.", " ",
                        "Extract each step."),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", "The method does too much.",
                        "Extract Method", null),
                new LlmFinding("src/Foo.java", 2, "Long Method", "Minor", "The method does too much.",
                        "Extract Method", " ")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void targetNameModeAcceptsMethodTargetWithWrongLineAndAnchorsDeclaration() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() {
                        calculate();
                        persist();
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                99,
                "Long Method",
                "METHOD",
                "longMethod")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void targetNameModeAcceptsClassTargetWithWrongLineAndAnchorsDeclaration() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("", """
                public class UserController {
                    public void showProfile() {}
                    public void searchBooks() {}
                    public void recommendBooks() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                99,
                "Large Class",
                "CLASS",
                "UserController")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(1, validated.get(0).line());
    }

    @Test
    void targetNameModeRejectsWrongMethodName() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() {
                        calculate();
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                2,
                "Long Method",
                "METHOD",
                "missingMethod")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void targetNameModeRejectsWrongClassName() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("", """
                public class UserController {
                    public void showProfile() {}
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                1,
                "Large Class",
                "CLASS",
                "MissingController")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void targetNameModeRejectsMissingTargetIdentity() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.FILE, ValidationMode.TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("", """
                public class Foo {
                    void longMethod() {
                        calculate();
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(finding(
                "src/Foo.java",
                2,
                "The method does too much.")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void strictLineModeStillRejectsWrongLineEvenWhenTargetMatches() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.STRICT_LINE, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void longMethod() { calculate(); persist(); }
                 }
                """, """
                public class Foo {
                    void longMethod() { calculate(); persist(); }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                20,
                "Long Method",
                "METHOD",
                "longMethod")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    @Test
    void diffTargetNameModeAcceptsWhenAddedLinesOverlapTargetMethod() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.DIFF_TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,5 +1,6 @@
                 public class Foo {
                     void longMethod() {
                +        calculate();
                         persist();
                     }
                 }
                """, """
                public class Foo {
                    void longMethod() {
                        calculate();
                        persist();
                    }
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                99,
                "Long Method",
                "METHOD",
                "longMethod")), List.of(file));

        assertEquals(1, validated.size());
        assertEquals(2, validated.get(0).line());
    }

    @Test
    void diffTargetNameModeRejectsWhenTargetExistsButAddedLinesDoNotOverlapIt() {
        FindingValidator validator = new FindingValidator(
                new AddedLineParser(), AnalysisScope.DIFF, ValidationMode.DIFF_TARGET_NAME, false);
        AnalyzedFile file = analyzedFile("""
                @@ -1,7 +1,8 @@
                 public class Foo {
                     void longMethod() {
                         persist();
                     }

                     void ok() {}
                +    int count = 1;
                 }
                """, """
                public class Foo {
                    void longMethod() {
                        persist();
                    }

                    void ok() {}
                    int count = 1;
                }
                """);

        List<LlmFinding> validated = validator.validateAndAnchor(List.of(targetFinding(
                "src/Foo.java",
                2,
                "Long Method",
                "METHOD",
                "longMethod")), List.of(file));

        assertTrue(validated.isEmpty());
    }

    private static AnalyzedFile analyzedFile(String diff, String fileContent) {
        return new AnalyzedFile(
                "src/Foo.java",
                "modified",
                null,
                1,
                0,
                1,
                diff,
                fileContent,
                List.of());
    }

    private static LlmFinding finding(String file, int line, String note) {
        return new LlmFinding(
                file,
                line,
                "Long Method",
                "Minor",
                note,
                "Extract Method",
                "Extract each step into a named helper method.");
    }

    private static LlmFinding targetFinding(
            String file,
            int line,
            String rule,
            String targetType,
            String targetName) {
        return new LlmFinding(
                file,
                line,
                rule,
                targetType,
                targetName,
                "Minor",
                "The target contains a clear smell.",
                "Extract Method",
                "Extract the problematic logic into a named helper method.");
    }
}
