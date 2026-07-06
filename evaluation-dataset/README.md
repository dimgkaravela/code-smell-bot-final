# Evaluation Dataset

This folder contains local fixtures and adapter notes for testing the code-smell bot against labeled code-smell datasets.

The bot supports two evaluation scopes: `file` for whole-file or method-level datasets, and `diff` for PR-style changed-line benchmarks. Most public smell datasets are whole-file or method-level datasets, so use `file` scope unless you are specifically evaluating GitHub PR behavior.

## Quick Start

Build the jar:

```powershell
mvn -DskipTests package
```

Smoke-test the dataset loader without calling an LLM:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\micro --scope file --dry-run
```

Score the sample predictions:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\micro --predictions evaluation-dataset\micro\sample-predictions.json --scope file
```

Run the actual bot on the micro cases:

```powershell
$env:LLM_PROVIDER="gemini"
$env:GEMINI_API_KEY="..."
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\micro --scope file
```

Run the refactoring-only cases:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\refactoring_cases --scope file
```

The `refactoring_cases` dataset follows the same `<dataset>\cases` and `<dataset>\files` layout as the other bot datasets. Each case analyzes the pre-refactoring Java file, sets `evaluateDetection` to `false`, sets `evaluateRefactoring` to `true`, and uses labels with an empty smell `rule` plus the expected `suggestedRefactoring`.

Outputs are written to:

- `out/evaluation/<dataset>-<scope>/summary.json`
- `out/evaluation/<dataset>-<scope>/predictions.json`
- `out/evaluation/<dataset>-<scope>/cases.json`
- `out/evaluation/<dataset>-<scope>/report.md`

`report.md` includes the final metrics, per-case results, predicted findings, and any refactoring suggestions returned by the LLM.

For the built-in micro dataset, the default folders are:

- `out/evaluation/micro-file/` for whole-file benchmark runs
- `out/evaluation/micro-diff/` for PR-diff benchmark runs

## Clean Micro Evaluation V1

The evaluation cases were derived from the publicly available SmellyCodeDataset. Since the local evaluation uses unannotated Java files, the expected labels and line numbers were manually normalized to match the local files analyzed by the evaluator. Cases with unclear, incomplete, unsupported, or non-localized smells were excluded from the clean evaluation set.

Prompt and raw-response artifacts are only written when `DEBUG_SMELLS=true`, under:

- `out/evaluation/<dataset>-<scope>/debug/llm/<case-id>/`

## Case Format

Each JSON file under `<dataset>/cases` is one evaluation case:

```json
{
  "id": "micro-commented-out-code",
  "source": "manual-micro",
  "repository": "evaluation/micro",
  "prNumber": 1,
  "files": [
    {
      "filename": "src/main/java/example/CommentedOutOrderFlow.java",
      "status": "added",
      "fileContentPath": "../files/CommentedOutOrderFlow.java"
    }
  ],
  "labels": [
    {
      "file": "src/main/java/example/CommentedOutOrderFlow.java",
      "line": 5,
      "rule": "Commented Out Code",
      "severity": "Minor",
      "note": "The added comment contains disabled executable code."
    }
  ]
}
```

If `diff` or `diffPath` is missing, the runner creates a synthetic all-added diff from `fileContent` or `fileContentPath`.

## Analysis Scope

The analyzer now supports both modes:

- `diff`: PR-review behavior. Findings must anchor to added lines in the unified diff.
- `file`: dataset/whole-file behavior. Findings may anchor anywhere in the primary Java file content.

For GitHub PR runs, set:

```powershell
$env:ANALYSIS_SCOPE="diff"
```

For whole-file dataset evaluation, set:

```powershell
$env:EVAL_ANALYSIS_SCOPE="file"
```

You can also pass `--scope file` or `--scope diff` to `EvaluationMain`.

Line matching uses a tolerance of 3 by default. Override it with:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\micro --scope file --line-tolerance 0
```

## Supported Smells

The scorer only counts labels for smells currently allowed by the bot prompt:

- Long Method
- Long Parameter List
- Duplicate Code
- Large Class
- Feature Envy
- Message Chains

Labels outside this set are reported as unsupported and skipped in precision/recall/F1. The scorer canonicalizes selected legacy aliases, including `Duplicate Code Inside a Method` to `Duplicate Code` and `God Class` to `Large Class`. This matters for datasets with smells like Refused Bequest, Data Class, or Lazy Class.

## How to Use the Listed Datasets

Recommended order for the current bot:

1. `SmellyCodeDataset`: start with Java samples and map only overlapping smells. It is useful for prompt-level detection tests, but some labels may still be outside the current prompt.
2. `SACS`: use Long Method, Large Class, and Feature Envy subsets. Message Chains are not part of SACS.
3. `iSMELL`: use Feature Envy now if the public data is available in a convertible format. Keep God Class and Refused Bequest for a later phase unless you map God Class carefully to Large Class and add Refused Bequest support.
4. `EvaluateLLMCodeSmell`: useful as a comparison experiment, but it is closer to generated-code quality analysis than PR-diff validation.

Recommended order for future refactoring:

1. `LLM4Refactoring/ref-Dataset`: use for evaluating suggested refactorings, not smell detection accuracy.
2. `RefactoringMiner` and `ReExtractor`: use to mine real before/after refactoring commits after the detection benchmark is stable.

Baselines:

- `Designite Incremental Analysis`: good comparator for newly introduced smells in Java/Python projects.
- `Manele Code Smell Tool`: narrow comparator for commented-out C/C++ code; not directly compatible with this Java-only runner.
