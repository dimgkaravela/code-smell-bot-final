# Appendix E - Commands and Configuration

These commands were run from the repository root on Windows PowerShell:

```powershell
cd C:\Users\gkara\Desktop\diplomatic\code-smell-bot
```

## Environment Configuration

Final `.env`-style configuration used for LLM-backed evaluation. Real keys and tokens are intentionally omitted.

```dotenv
LLM_PROVIDER=gemini
GEMINI_API_KEY=<redacted>
GEMINI_MODEL=gemini-3.1-flash-lite

LLM_TEMPERATURE=0.0
LLM_MAX_TOKENS=1200
LLM_MAX_FILES_PER_CHUNK=4
LLM_MAX_PATCH_CHARS=18000

ANALYSIS_SCOPE=file
VALIDATION_MODE=TARGET_NAME

EVAL_ANALYSIS_SCOPE=file
EVAL_VALIDATION_MODE=target-name
EVAL_MATCH_MODE=target
EVAL_LINE_TOLERANCE=3
EVAL_EVALUATE_DETECTION=auto
EVAL_EVALUATE_REFACTORING=auto
EVAL_SCORE_ONLY_ANNOTATED_RULES=false
```

For PR-review mode, `ANALYSIS_SCOPE=diff` can be used. For evaluation runs below, scope and validation mode are passed explicitly through CLI flags.

## Build And Test Commands

```powershell
mvn test
mvn -DskipTests package
```

## Command Template

The evaluator entry point is:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain
```

Prompt 2 does not use a separate CLI flag. Prompt 2 results were produced by running the same evaluator commands after applying the revised prompt implementation in the source code, and by writing to `*-prompt2` output directories.

## D.1-D.2 Prompt 1 Detection Commands

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope file --validation-mode strict-line --match-mode line --out out\evaluation\smellycode-file-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope file --validation-mode target-name --match-mode target --out out\evaluation\smellycode-file-target-name
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope diff --validation-mode strict-line --match-mode line --out out\evaluation\smellycode-diff-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\smellycode-diff-target-name

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope file --validation-mode strict-line --match-mode line --out out\evaluation\sacs-file-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope file --validation-mode target-name --match-mode target --out out\evaluation\sacs-file-target-name
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope diff --validation-mode strict-line --match-mode line --out out\evaluation\sacs-diff-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\sacs-diff-target-name

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --scope file --validation-mode target-name --match-mode target --out out\evaluation\figshare-audited-file-target-name
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --scope diff --validation-mode target-name --match-mode target --out out\evaluation\figshare-audited-diff-target-name

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope file --validation-mode strict-line --match-mode line --out out\evaluation\softdevl-file-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope file --validation-mode target-name --match-mode target --out out\evaluation\softdevl-file-target-name
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope diff --validation-mode strict-line --match-mode line --out out\evaluation\softdevl-diff-strict-line
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\softdevl-diff-target-name
```

MLCQ Prompt 1 quota runs:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_cropped_replaces_quota01 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-cropped-replaces-quota01-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota02 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota02-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota03 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota03-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota04 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota04-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota05 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota05-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota06 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota06-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota07 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota07-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota08 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota08-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota09 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota09-file-target
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota10 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota10-file-target
```

## D.3-D.4 Prompt 2 Detection Commands

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope file --validation-mode target-name --match-mode target --out out\evaluation\smellycode-file-target-name-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\smellycode-diff-target-name-prompt2

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope file --validation-mode target-name --match-mode target --out out\evaluation\sacs-file-target-name-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\sacs-diff-target-name-prompt2

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --scope file --validation-mode target-name --match-mode target --out out\evaluation\figshare-audited-file-target-name-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --scope diff --validation-mode target-name --match-mode target --out out\evaluation\figshare-audited-diff-target-name-prompt2

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope file --validation-mode target-name --match-mode target --out out\evaluation\softdevl-file-target-name-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --scope diff --validation-mode diff-target-name --match-mode target --out out\evaluation\softdevl-diff-target-name-prompt2
```

MLCQ Prompt 2 quota runs:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_cropped_replaces_quota01 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-cropped-replaces-quota01-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota02 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota02-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota03 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota03-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota04 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota04-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota05 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota05-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota06 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota06-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota07 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota07-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota08 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota08-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota09 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota09-file-target-prompt2
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota10 --scope file --validation-mode target-name --match-mode target --out out\evaluation\mlcq-quota10-file-target-prompt2
```

## D.5 Annotated-Only Scoring Commands

Annotated-only scoring reuses saved predictions and filters predictions to smells annotated in the dataset:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --predictions out\evaluation\smellycode-file-target-name\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\smellycode-file-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --predictions out\evaluation\smellycode-diff-target-name\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\smellycode-diff-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --predictions out\evaluation\smellycode-file-target-name-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\smellycode-file-target-name-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\smellycode --predictions out\evaluation\smellycode-diff-target-name-prompt2\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\smellycode-diff-target-name-prompt2-annotated-only

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --predictions out\evaluation\sacs-file-target-name\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\sacs-file-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --predictions out\evaluation\sacs-diff-target-name\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\sacs-diff-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --predictions out\evaluation\sacs-file-target-name-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\sacs-file-target-name-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SACS --predictions out\evaluation\sacs-diff-target-name-prompt2\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\sacs-diff-target-name-prompt2-annotated-only

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --predictions out\evaluation\figshare-audited-file-target-name\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\figshare-audited-file-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --predictions out\evaluation\figshare-audited-diff-target-name\predictions.json --scope diff --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\figshare-audited-diff-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --predictions out\evaluation\figshare-audited-file-target-name-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\figshare-audited-file-target-name-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset out\evaluation\figshare-audited-dataset --predictions out\evaluation\figshare-audited-diff-target-name-prompt2\predictions.json --scope diff --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\figshare-audited-diff-target-name-prompt2-annotated-only

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-file-target-name\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\softdevl-file-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-diff-target-name\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\softdevl-diff-target-name-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-file-target-name-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\softdevl-file-target-name-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-diff-target-name-prompt2\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\softdevl-diff-target-name-prompt2-annotated-only
```

MLCQ annotated-only scoring:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_cropped_replaces_quota01 --predictions out\evaluation\mlcq-cropped-replaces-quota01-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-cropped-replaces-quota01-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota02 --predictions out\evaluation\mlcq-quota02-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota02-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota03 --predictions out\evaluation\mlcq-quota03-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota03-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota04 --predictions out\evaluation\mlcq-quota04-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota04-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota05 --predictions out\evaluation\mlcq-quota05-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota05-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota06 --predictions out\evaluation\mlcq-quota06-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota06-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota07 --predictions out\evaluation\mlcq-quota07-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota07-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota08 --predictions out\evaluation\mlcq-quota08-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota08-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota09 --predictions out\evaluation\mlcq-quota09-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota09-file-target-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota10 --predictions out\evaluation\mlcq-quota10-file-target\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota10-file-target-annotated-only

java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_cropped_replaces_quota01 --predictions out\evaluation\mlcq-cropped-replaces-quota01-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-cropped-replaces-quota01-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota02 --predictions out\evaluation\mlcq-quota02-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota02-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota03 --predictions out\evaluation\mlcq-quota03-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota03-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota04 --predictions out\evaluation\mlcq-quota04-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota04-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota05 --predictions out\evaluation\mlcq-quota05-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota05-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota06 --predictions out\evaluation\mlcq-quota06-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota06-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota07 --predictions out\evaluation\mlcq-quota07-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota07-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota08 --predictions out\evaluation\mlcq-quota08-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota08-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota09 --predictions out\evaluation\mlcq-quota09-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota09-file-target-prompt2-annotated-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\mlcq_selected_cases\cases_quota10 --predictions out\evaluation\mlcq-quota10-file-target-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --score-only-annotated-rules true --out out\evaluation\mlcq-quota10-file-target-prompt2-annotated-only
```

## D.7-D.9 Refactoring Commands

SoftDev refactoring-only scoring reused Prompt 2 predictions:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-file-target-name-prompt2\predictions.json --scope file --validation-mode target-name --match-mode target --evaluate-detection false --evaluate-refactoring true --out out\evaluation\softdevl-file-target-name-prompt2-refactoring-only
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\SoftDevl-Project-Material --predictions out\evaluation\softdevl-diff-target-name-prompt2\predictions.json --scope diff --validation-mode diff-target-name --match-mode target --evaluate-detection false --evaluate-refactoring true --out out\evaluation\softdevl-diff-target-name-prompt2-refactoring-only
```

RefactoringMiner-derived refactoring runs:

```powershell
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\refactoring_cases --scope file --validation-mode target-name --match-mode target --line-tolerance 1000000 --evaluate-detection false --evaluate-refactoring true --out out\evaluation\refactoring-file-target-name-refprompt1
java -cp target\code-smell-bot-0.1.0-SNAPSHOT.jar dev.dimitra.bot.eval.EvaluationMain --dataset evaluation-dataset\refactoring_cases --scope diff --validation-mode diff-target-name --match-mode target --line-tolerance 1000000 --evaluate-detection false --evaluate-refactoring true --out out\evaluation\refactoring-diff-target-name-refprompt1
```

## Notes For Reproduction

- All current dataset JSON fixtures omit real diffs; diff-scope evaluation uses the evaluator's synthetic all-added diff generation.
- `--predictions` scoring runs do not call the LLM. They rescore an existing `predictions.json` file with the requested scoring options.
- `--score-only-annotated-rules true` filters predictions to smell types that are annotated in the dataset before scoring.
- RefactoringMiner cases use `line: 0` labels, so `--line-tolerance 1000000` is used to avoid making source-line location the deciding factor.
