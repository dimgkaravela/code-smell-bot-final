# Datasets and Benchmarks Notes

These are factual notes from the repository, not thesis prose.

## External Sources Checked

- SmellyCodeDataset: public GitHub repository `HRI-EU/SmellyCodeDataset`, described as intentionally smelly code in Java, Python, JavaScript, and C++ for testing LLM code-smell detection and refactoring: https://github.com/HRI-EU/SmellyCodeDataset
- SmellyCode paper: Ahmed R. Sadik and Siddhata Govind, "Benchmarking LLM for Code Smells Detection: OpenAI GPT-4.0 vs DeepSeek-V3", arXiv 2025 / EASE25 preprint. The paper says the dataset is a restaurant-management system implemented in Java, JavaScript, Python, and C++ and used as ground truth for LLM smell detection: https://arxiv.org/abs/2504.16027
- SACS paper: Hanyu Zhang and Tomoji Kishi, "SACS: A Code Smell Dataset using Semi-automatic Generation Approach", arXiv 2026. It covers Long Method, Large Class, and Feature Envy and reports positive/negative samples: https://arxiv.org/pdf/2602.15342
- SACS repository: `Bankzhy/GCSM_Dataset`, public GitHub repository for a code smell refactoring dataset focused on Long Method, Large Class, and Feature Envy: https://github.com/Bankzhy/GCSM_Dataset
- RefactoringMiner repository: public GitHub repository `tsantalis/RefactoringMiner`, a Java library/API for detecting refactorings in Java project history and supporting Extract Method, Move Method, Move Attribute, Extract Class, and many others: https://github.com/tsantalis/RefactoringMiner

## Repository-Wide Evaluation Format

- Existing dataset directories under `evaluation-dataset/`: `smellycode`, `SACS`, `figshare`, `SoftDevl-Project-Material`, `refactoring_cases`.
- Earlier `micro` cases are mentioned in `evaluation-dataset/README.md` and existed in git history, but no `evaluation-dataset/micro` directory exists in the current working tree.
- Each normal case JSON has `id`, `source`, `repository`, `prNumber`, `files`, and `labels`.
- Each `files[]` entry links to Java source through `fileContentPath`, usually relative to the JSON file's `cases` directory.
- Each label links back to the analyzed file with `file`, `line`, `rule`, `targetType`, and `targetName`; labels may also include `suggestedRefactoring` and `refactoringNote`.
- Current prompt/scorer actually allow six detection smells: `Long Method`, `Long Parameter List`, `Duplicate Code`, `Large Class`, `Feature Envy`, `Message Chains`.
- `EvaluationScorer` also canonicalizes aliases: `Duplicate Code Inside a Method` -> `Duplicate Code`, `God Class` -> `Large Class`, `message chain`/`train wreck` -> `Message Chains`.
- `evaluation-dataset/README.md` still lists an older broader smell set. Treat the current `PromptRenderer` and `EvaluationScorer` code as authoritative.
- `EvaluationDatasetLoader` loads `<dataset>/cases` when that folder exists; otherwise it walks the dataset root. This matters for `SoftDevl-Project-Material`, which has year subdirectories instead of a top-level `cases` directory.
- If `diff` or `diffPath` is missing, the loader creates a synthetic all-added diff from the file content.
- All current dataset case files omit `diff`/`diffPath`, so all current datasets are file-level fixtures. Diff-scope runs use synthetic all-added diffs, not real PR or before/after diffs.
- The default evaluation scope is `file`; default validation for file scope is target-name validation. Default line tolerance is 3.
- Saved output directories under `out/evaluation` show all five current datasets have been run in file/diff and strict-line/target-name variants.

## SmellyCode

### Overview

- Local directory: `evaluation-dataset/smellycode`.
- Original source: `HRI-EU/SmellyCodeDataset`.
- Paper/source context: Sadik and Govind's 2025 LLM code-smell benchmark paper; the dataset is described as a multilingual intentionally smelly restaurant-management system.
- Purpose of original dataset: test LLM ability to detect and refactor common code smells.
- Local contents: 6 cleaned Java files in `files/` (`Cashier`, `Chef`, `Customer`, `Drink`, `Pizza`, `Shop`) and 5 evaluation cases in `cases/`.
- Language used locally: Java only. Original dataset includes Java, Python, JavaScript, and C++.
- Public availability: public GitHub repository.
- Widely used in literature: evidence found for its own 2025 benchmark paper only; no evidence in this repo that it is widely used.

### Labels

- Original public README also names categories including Large Classes, Long Methods, Primitive Obsession, Feature Envy, Inappropriate Intimacy, Divergent Change, Shotgun Surgery, Duplicate Code, Lazy Class, Data Class, Refused Bequest, and Alternative Classes with Different Interfaces.
- Labels used locally by the bot: 19 labels total.
- Local effective smell counts: Long Method 5; Long Parameter List 6; Duplicate Code 5; Feature Envy 1; Message Chains 2.
- Excluded smells: all original smells outside the current six-smell prompt/scorer set, plus any unclear/non-localized cases noted in the README.
- `Shop.java` is present but has no evaluation case. [TODO: explain why `Shop.java` was retained but not evaluated.]

### Structure

- `evaluation-dataset/smellycode/files/`: cleaned Java files with inline smell comments removed.
- `evaluation-dataset/smellycode/cases/*.json`: one JSON case per analyzed file except `Shop.java`.
- Each case references one Java file through `fileContentPath`.
- Labels use local `src/main/java/example/...` file paths and target metadata.

### How The Pipeline Uses It

- Run with dataset root `evaluation-dataset/smellycode`.
- Loader reads the five JSON files from `smellycode/cases`.
- Loader reads cleaned Java files from `smellycode/files`.
- No real diff is supplied; synthetic all-added diffs are generated.
- Detection is evaluated automatically because labels contain supported smell rules.
- Refactoring is not evaluated automatically because labels have no `suggestedRefactoring`.

### Dataset-Specific Modifications

- Original inline smell comments were removed from the Java source used for evaluation; the clean final repository keeps the cleaned files under `evaluation-dataset/smellycode/files`.
- Labels and line numbers were manually normalized to the cleaned local files; this is stated in `evaluation-dataset/README.md`.
- Cases with unclear, incomplete, unsupported, or non-localized smells were excluded; this is stated in `evaluation-dataset/README.md`.
- The retained local set contains 5 cases and 19 labels.
- Target metadata (`targetType`, `targetName`) and human-written notes were added.
- [TODO: Describe the manual selection process used for SmellyCode.]
- [TODO: State approximately how many original SmellyCode smell annotations were inspected and how many were rejected.]

### Strengths

- Small and easy to inspect.
- Intentionally smelly code with known annotated smell examples.
- Cleaned files avoid giving the LLM inline smell comments.
- Useful for prompt-level detection checks on simple Java examples.

### Weaknesses

- Toy restaurant-management system, not production code.
- Local subset is very small: 5 cases, 19 labels.
- No negative cases in the current local dataset.
- Many original smells are unsupported by the current bot and excluded.
- Manual curation process is not documented beyond the README sentence.
- Not a real diff/PR benchmark.

## SACS

### Overview

- Local directory: `evaluation-dataset/SACS`.
- Original source: SACS / GCSM_Dataset by Hanyu Zhang and Tomoji Kishi.
- Paper: "SACS: A Code Smell Dataset using Semi-automatic Generation Approach" (2026).
- Purpose of original dataset: large-scale code-smell benchmark generated with semi-automatic rules plus manual checking.
- Original smells: Long Method, Large Class, Feature Envy.
- Original corpus: paper reports 16 Java open-source projects and both positive and negative samples.
- Local contents: 20 Java files and 20 JSON cases.
- Language: Java.
- Public availability: public GitHub repository and arXiv paper.
- Widely used in literature: very recent dataset; no evidence found that it is widely used yet.

### Labels

- Original smells included: Long Method, Large Class, Feature Envy.
- Local labels used by the bot: 52 labels total.
- Local smell counts: Long Method 26; Large Class 12; Feature Envy 14.
- Excluded smell types: none of SACS's three smell types are unsupported by the current bot.
- Excluded data: the local repo uses only a tiny positive subset; original negative samples and most original samples are not present.

### Structure

- `evaluation-dataset/SACS/files/`: 20 copied Java files from projects such as Freeplane, Groot, JGraphT, jsprit, JUnit4, libGDX, PlantUML, RxJava.
- `evaluation-dataset/SACS/cases/*.json`: one case per Java file.
- Each case has `source: "SACS-derived"`, one analyzed file, and one or more labels.
- Some cases have multiple labels on the same target, e.g. `UGraphicDebug.draw` has repeated Long Method labels and `FunctionPlotter.draw` has both Feature Envy and Long Method.

### How The Pipeline Uses It

- Run with dataset root `evaluation-dataset/SACS`.
- Loader reads only `SACS/cases/*.json`.
- Each case reads a single Java file from `SACS/files`.
- No real diff is supplied; synthetic all-added diffs are generated.
- Detection is evaluated; every case sets `evaluateRefactoring: false`.

### Dataset-Specific Modifications

- A 20-file subset was selected from the much larger SACS dataset.
- Local cases add line numbers, target metadata, severity, and explanatory notes.
- Only positive smell labels are represented locally.
- Negative samples from the original SACS evaluation/training dataset are not represented.
- `evaluateRefactoring` is explicitly disabled in all SACS cases.
- One label has `line: 0` (`AbstractObservableWithUpstream.source`), meaning line location is not enforced for that label in line matching.
- [TODO: explain how the 20 SACS files were selected.]
- [TODO: explain whether SACS line numbers or labels were manually corrected.]

### Strengths

- Comes from a large public Java dataset for three common smells.
- Original paper includes semi-automatic generation plus manual checking.
- Local files are real open-source Java code, often large and complex.
- Covers the bot's Large Class, Long Method, and Feature Envy behavior.

### Weaknesses

- Local subset is small compared with the original dataset.
- No local negative cases.
- Only three smell types.
- No refactoring labels in local cases.
- No real diffs.
- SACS paper itself acknowledges possible erroneous samples and unavoidable subjective inconsistencies.

## Figshare

### Overview

- Local directory: `evaluation-dataset/figshare`.
- Original source according to local JSON: `figshare-CodeSmellsDataset-MessageChains_positive.csv` and `figshare-CodeSmellsDataset-LongParameterList_positive.csv`.
- Exact Figshare article URL, paper, DOI, and repository are not stored in this repo and were not found from the local evidence. [TODO: provide exact Figshare source.]
- Purpose inferred from local source strings: method-level positive examples for Message Chains and Long Parameter List.
- Local contents: 18 Java files and 18 JSON cases.
- Source files include Apache Xerces, Apache Ant, and HSQLDB code according to file headers/packages.
- Language: Java.
- Public availability: [TODO: verify exact public Figshare record.]
- Widely used in literature: [TODO: provide evidence if applicable.]

### Labels

- Original labels represented locally: Message Chains positives and Long Parameter List positives.
- Local raw labels before audit: 28 labels total.
- Local raw smell counts before audit: Message Chains 21; Long Parameter List 7.
- Message Chains audit criterion: keep a Figshare Message Chains label only when the labeled method contains an expression-level receiver chain such as `a.getB().getC()`; one-hop calls, static calls, or separate calls stored in temporaries are not enough.
- Audit result: 5 of 21 Figshare Message Chains labels contain expression-level chains; 16 are mismatched with the bot's Message Chains definition.
- Decision for final taxonomy/evaluation: exclude the mismatched Figshare Message Chains labels, but keep Message Chains in the final taxonomy because valid Message Chains examples remain in Figshare and other local datasets.
- Usable audited Figshare labels for final reporting: Message Chains 5; Long Parameter List 7; total 12 labels across 9 positive cases if empty MC-only cases are excluded.
- Excluded smell types: [TODO: determine whether the original Figshare CodeSmellsDataset contains additional smell CSVs.]
- Refactoring labels: none; all cases set `evaluateRefactoring: false`.

### Message Chains Audit

| Case | Label | Does it contain expression-level chain? | Keep / exclude / uncertain | Reason |
|---|---|---|---|---|
| `figshare-mc-regexparser` | `RegexParser.processStar` | No | Exclude | Uses `this.next()`, `this.read()`, and `Token.create...` one-hop/static calls only. |
| `figshare-mc-1-2-1-regexparser` | `RegexParser.processStar` | No | Exclude | Same method pattern as unversioned `RegexParser.processStar`; no returned receiver chain. |
| `figshare-mc-1-4-1-rangetoken` | `RangeToken.intersectRanges` | No | Exclude | Calls `sortRanges()` and `compactRanges()` separately and mostly manipulates arrays; no chained call expression. |
| `figshare-mc-available` | `Available.checkResource` | Yes | Keep | Contains `this.getClass().getClassLoader()`. |
| `figshare-mc-available` | `Available.checkClass` | Yes | Keep | Contains `this.getClass().getClassLoader()`. |
| `figshare-mc-1-4-available` | `Available.checkResource` | Yes | Keep | Versioned duplicate of the valid `checkResource` chain. |
| `figshare-mc-1-4-available` | `Available.checkClass` | Yes | Keep | Versioned duplicate of the valid `checkClass` chain. |
| `figshare-mc-2-2-1-lobmanager` | `LobManager.initialiseLobSpace` | No | Exclude | Builds parameters and calls `sysLobSession.executeCompiledStatement(...)`; no expression-level chain. |
| `figshare-mc-2-2-6-lobmanager` | `LobManager.setLength` | No | Exclude | Stores `updateLobLength.getParametersMetaData()` in `meta` before calling `meta.getColumnCount()`; no chained expression. |
| `figshare-mc-2-2-8-lobmanager` | `LobManager.divideBlockAddresses` | No | Exclude | Uses the same temporary-variable pattern as other `LobManager` labels; no chained expression. |
| `figshare-mc-covbase` | `CovBase.findCoverageJar` | No | Exclude | Uses simple `fu.resolveFile(...)` and `canRead()` calls; no receiver chain. |
| `figshare-mc-ditableinfo` | `DITableInfo.getColSqlDataType` | Yes | Keep | Contains `table.getColumn(i).getDIType()`. |
| `figshare-mc-elementimpl` | `ElementImpl.removeAttributeNS` | No | Exclude | Calls `attributes.getNamedItemNS(...)` and `attributes.removeNamedItemNS(...)`; both are one-hop calls. |
| `figshare-mc-elementimpl` | `ElementImpl.setAttributeNodeNS` | No | Exclude | Has nested argument calls such as `na.getNamespaceURI()`, but no receiver chain like `x.y().z()`. |

Full Message Chains disposition after grouped duplicate review:

- Keep: `Available.checkResource` and `Available.checkClass` in both unversioned and `1.4` copies, plus `DITableInfo.getColSqlDataType` (5 labels).
- Exclude: all `RegexParser.processStar` copies, `RangeToken.intersectRanges`, all versioned `LobManager.initialiseLobSpace`, `LobManager.setLength`, `LobManager.divideBlockAddresses`, `CovBase.findCoverageJar`, and both `ElementImpl` labels (16 labels).

### Structure

- `evaluation-dataset/figshare/files/`: copied Java files. Some are in version subdirectories such as `1.2.1`, `1.4`, `1.4.1`, `2.2.1`, `2.2.6`, `2.2.8`, `2.3.0`; some are unversioned.
- `evaluation-dataset/figshare/cases/*.json`: one case per copied Java file.
- Case `source` names the original CSV used.
- Labels point to a method with `targetType: METHOD`, `targetName`, line, rule, and a note such as "MessageChains_positive.csv labels X.Y as a positive Message Chains method."

### How The Pipeline Uses It

- Run with dataset root `evaluation-dataset/figshare`.
- Loader reads `figshare/cases/*.json`.
- Each case reads one Java file from `figshare/files`.
- No real diff is supplied; synthetic all-added diffs are generated.
- Detection is evaluated; refactoring is explicitly disabled.

### Dataset-Specific Modifications

- Positive CSV method labels were converted into bot evaluation JSON.
- Local Java files were copied into the repo and organized by version where applicable.
- Line numbers and target metadata were added/aligned to local source files.
- Human-readable notes were added.
- Only positive cases are present locally.
- [TODO: explain how the Java files were obtained and matched to CSV rows.]
- Message Chains labels were audited manually against the local copied Java source; final reporting should exclude the 16 mismatched Figshare Message Chains labels listed above.

### Strengths

- Uses real Java source files from established open-source projects.
- Provides method-level labels for two smells supported by the bot.
- Versioned source folders make some historical file versions explicit.

### Weaknesses

- Exact upstream Figshare source is not documented locally.
- Small local subset: 18 raw cases and 28 raw labels; after Message Chains audit, only 12 labels across 9 positive cases should be used for final reporting.
- Only two smell types.
- Only positive examples; no negative cases.
- No refactoring labels.
- No real diffs.
- Some source files are old, based on headers from 1999-2011-era projects.
- Most raw Figshare Message Chains positives do not match the bot's expression-level Message Chains definition.

## SoftDevl-Project-Material

### Overview

- Local directory: `evaluation-dataset/SoftDevl-Project-Material`.
- This is the custom dataset in the repository.
- Source fields indicate `SoftDevII-Project-2021-Backlog-derived`, `SoftDevII-Project-2022-Backlog-derived`, `SoftDevII-Project-2024-Backlog-derived`, and `SoftDevII-Project-2025-Backlog-derived`.
- Academic years covered locally: 2021, 2022, 2024, 2025.
- Local contents: 20 Java files and 19 evaluation cases.
- One Java file has no case: `2024/files/FileAppender.java`.
- Language: Java.
- Public availability: [TODO: state whether this custom dataset can be published.]
- Widely used in literature: no, this is a local/custom dataset.

### Labels

- Local smell labels: 26 total.
- Local raw smell counts: Duplicate Code 14; Duplicate Code Inside a Method 4; Large Class 4; Long Method 3; Message Chains 1.
- Scorer canonicalizes raw `Duplicate Code Inside a Method` to `Duplicate Code`, so the effective duplicate-code label count is 18.
- Refactoring labels: 26 total, because each label has `suggestedRefactoring`.
- Refactoring counts: Form Template Method 10; Substitute Algorithm 5; Extract Class 4; Parameterize Method 4; Extract Method 1; Hide Delegate 1; Move Method 1.
- Year distribution: 2021 has 3 cases/6 labels; 2022 has 4 cases/7 labels; 2024 has 6 cases/7 labels; 2025 has 6 cases/6 labels.
- Excluded smells: none are documented locally. [TODO: state whether any backlog smells were excluded.]

### Structure

- `2021/files` and `2021/cases`: tax/receipt management code (`Taxpayer`, `InputSystem`, `OutputSystem`).
- `2022/files` and `2022/cases`: LaTeX editor code (`DocumentManager`, `LatexEditorController`, `MainWindow`, `VersionsManager`).
- `2024/files` and `2024/cases`: sales/input/output GUI code (`Agent`, `FileAppenderTXT`, `FileAppenderXML`, `SelectionWindow`, `TXTInput`, `XMLInput`; unreferenced `FileAppender`).
- `2025/files` and `2025/cases`: Social Bookstore code (`UserController`, recommendation strategies, search strategies).
- There is no top-level `cases` folder, so the loader walks the full dataset root and finds nested year case files.
- Each case references one Java file in the same year's `files` folder.

### How The Pipeline Uses It

- Run with dataset root `evaluation-dataset/SoftDevl-Project-Material`.
- Loader walks nested directories and loads all JSON files under `*/cases`.
- Each case reads one Java file from the matching year folder.
- No real diff is supplied; synthetic all-added diffs are generated.
- Detection is evaluated automatically because labels contain supported/canonicalizable smell rules.
- Refactoring is evaluated automatically because labels contain `suggestedRefactoring`.

### Dataset-Specific Modifications

- Project files were copied into year-specific local folders.
- Backlog-derived smell/refactoring expectations were converted into evaluation JSON.
- Labels include target metadata, line numbers, severity, notes, `suggestedRefactoring`, and `refactoringNote`.
- Some labels use legacy/raw duplicate-code names, relying on scorer aliasing rather than the exact prompt label `Duplicate Code`.
- `FileAppender.java` is present as context/source material but not linked by any case.
- [TODO: explain where the original project material came from.]
- [TODO: explain whether the projects are student projects, course assignments, or another source.]
- [TODO: explain why these projects/years were selected and why 2023 is absent.]
- [TODO: explain the annotation process for smell labels.]
- [TODO: explain the annotation process for refactoring labels.]
- [TODO: explain whether labels were validated by another person or tool.]

### Strengths

- Custom dataset directly aligned with the thesis bot and its refactoring-output format.
- Contains both detection labels and expected refactoring labels.
- Uses real multi-file project material rather than only synthetic examples.
- Covers repeated design issues across different academic-year projects.
- Valuable compared with public smell-only datasets because it evaluates suggested refactorings as well as detection.

### Weaknesses

- Small: 19 cases, 26 labels.
- Not publicly established or literature-used.
- Annotation protocol and selection rationale are not documented in the repo.
- No negative cases.
- No real diffs.
- Some labels rely on scorer aliases (`Duplicate Code Inside a Method`) rather than exact allowed prompt names.

## Refactoring Cases

### Overview

- Local directory: `evaluation-dataset/refactoring_cases`.
- Original source: local metadata says `RefactoringMiner oracle`.
- Related repository/tool: `tsantalis/RefactoringMiner`.
- Purpose: evaluate whether the bot suggests the expected refactoring type, not smell detection.
- Local contents: 37 evaluation cases, 37 copied pre-refactoring Java files, and an `origin/` tree with before/after files and metadata.
- Language: Java.
- Public availability: RefactoringMiner is public; this exact 37-case subset is local unless published separately.
- Widely used in literature: RefactoringMiner is widely used in refactoring research; no evidence that this exact subset is widely used.

### Labels

- No smell labels are used. Every converted case has `rule: ""`.
- Refactoring labels used: 37 total.
- Refactoring counts: Extract Method 10; Extract Class 9; Move Method 9; Move Attribute 9.
- Detection is explicitly disabled in every converted case.
- Refactoring is explicitly enabled in every converted case.

### Structure

- `origin/manifest.json`: source metadata for all 37 cases, including repository, commit, parent commit, commit URL, before file, after file, expected refactoring, and description.
- `origin/<refactoring-type>/case-XX/before.java`: pre-refactoring file.
- `origin/<refactoring-type>/case-XX/after.java`: post-refactoring file.
- `origin/<refactoring-type>/case-XX/case.json`: original metadata for that case.
- `files/*.java`: copied pre-refactoring Java files used by the bot.
- `cases/*.json`: bot evaluation wrappers. They reference `../files/<case-id>.java`, set `status: "modified"`, set detection false/refactoring true, and label the expected refactoring.

### How The Pipeline Uses It

- Run with dataset root `evaluation-dataset/refactoring_cases`.
- Loader reads only `refactoring_cases/cases/*.json`; it does not read `origin/manifest.json` because a top-level `cases` folder exists.
- Each converted case analyzes the copied pre-refactoring Java file.
- The `after.java` files in `origin/` are preserved but not loaded by the current evaluation pipeline.
- No real diff is supplied, despite `status: "modified"`; synthetic all-added diffs are generated from the pre-refactoring file.
- Detection scoring is skipped.
- Refactoring scoring matches `suggestedRefactoring`, file path, and line. Since labels use `line: 0`, line position is effectively not enforced.

### Dataset-Specific Modifications

- Real before/after refactoring metadata was converted into bot-compatible JSON.
- Only the pre-refactoring file was copied to `files/` and analyzed.
- Commit metadata is preserved in `origin/manifest.json` and `origin/*/case.json`, but not copied into the converted `cases/*.json` except for repository and textual notes.
- Target metadata was extracted into `targetType` and `targetName`.
- Labels use `line: 0`, meaning no exact source line is required.
- [TODO: explain how these 37 RefactoringMiner cases were selected.]
- [TODO: explain whether any RefactoringMiner cases were filtered out.]
- [TODO: explain why line numbers were not localized.]

### Strengths

- Uses real open-source commits from many repositories.
- Preserves before/after source files and commit URLs in `origin/`.
- Provides explicit expected refactoring labels.
- Covers four important refactoring types relevant to smell remediation.

### Weaknesses

- Not a smell-detection dataset.
- The current evaluator does not analyze the actual before/after diff.
- Refactoring labels use line 0, so location accuracy is not evaluated.
- Selection process is undocumented.
- No evidence that refactorings were caused by code smells.
- Some repositories/files appear in more than one case, so the subset is not necessarily independent or representative.

## Summary Table

| Dataset | Source | Language | Original purpose | Smells used locally | Refactorings | Cases / labels | Custom modifications | Used for |
|---|---|---:|---|---|---|---:|---|---|
| SmellyCode | HRI-EU/SmellyCodeDataset; Sadik and Govind 2025 | Java locally; original multilingual | LLM code-smell detection/refactoring benchmark | Long Method, Long Parameter List, Duplicate Code, Feature Envy, Message Chains | None | 5 / 19 | Removed inline smell comments, normalized labels/lines, selected supported localized smells, added target metadata | Detection |
| SACS | Zhang and Kishi SACS / Bankzhy GCSM_Dataset | Java | Large code-smell dataset for Long Method, Large Class, Feature Envy | Long Method, Large Class, Feature Envy | None | 20 / 52 | Selected small positive subset, copied source files, added JSON labels/target metadata, disabled refactoring | Detection |
| Figshare | Local source strings: Figshare CodeSmellsDataset CSVs | Java | Positive method-level smell labels | Long Parameter List, Message Chains | None | 18 / 28 raw; 9 / 12 after MC audit | Converted CSV positives to JSON, copied/versioned Java files, added line/target metadata, disabled refactoring; exclude 16 mismatched MC labels from final reporting | Detection |
| SoftDevl-Project-Material | Custom SoftDevII project/backlog-derived material | Java | Thesis custom smell/refactoring benchmark | Duplicate Code, Large Class, Long Method, Message Chains | Extract Class, Extract Method, Form Template Method, Hide Delegate, Move Method, Parameterize Method, Substitute Algorithm | 19 / 26 smell labels and 26 refactoring labels | Organized by year, converted backlog-derived labels to JSON, added refactoring labels and notes | Detection + refactoring |
| refactoring_cases | RefactoringMiner oracle, many OSS commits | Java | Refactoring suggestion/evaluation benchmark | None | Extract Method, Extract Class, Move Method, Move Attribute | 37 / 37 refactoring labels | Converted before/after oracle cases to bot JSON, analyzes only before file, uses line 0, keeps origin metadata separately | Refactoring only |

## Information That Only The Thesis Author Can Provide

- [TODO: Describe the manual selection process used for SmellyCode.]
- [TODO: State how many SmellyCode original annotations/cases were inspected, retained, and rejected.]
- [TODO: Explain whether SmellyCode false positives were manually removed and give examples if applicable.]
- [TODO: Explain why `Shop.java` is present but has no SmellyCode evaluation case.]
- [TODO: Provide the exact Figshare article/DOI/repository/paper for `CodeSmellsDataset`.]
- [TODO: State whether the Figshare source contains additional smells or negative samples not used locally.]
- [TODO: Explain how Figshare Java files were downloaded and matched to CSV labels.]
- [TODO: Explain whether Figshare line numbers were manually fixed.]
- [TODO: Explain how the 20 SACS cases were selected from the original large dataset.]
- [TODO: Explain whether any SACS labels/line numbers were manually corrected.]
- [TODO: Explain where the SoftDevl project material came from and whether it can be published.]
- [TODO: Explain which course/assignment/project each SoftDevl year corresponds to.]
- [TODO: Explain why years 2021, 2022, 2024, and 2025 were selected and why 2023 is absent.]
- [TODO: Explain how SoftDevl smell labels were created.]
- [TODO: Explain how SoftDevl refactoring labels were created.]
- [TODO: Explain whether SoftDevl labels were independently validated.]
- [TODO: Explain why `2024/files/FileAppender.java` has no case.]
- [TODO: Explain how the 37 RefactoringMiner/refactoring cases were selected.]
- [TODO: Explain whether any RefactoringMiner cases were filtered out or manually cleaned.]
- [TODO: Explain why refactoring-case labels use `line: 0` instead of localized source lines.]
- [TODO: Decide whether thesis results should include diff-scope runs, because current diff runs use synthetic all-added diffs rather than real PR diffs.]
- [TODO: Decide whether to update the README's older supported-smell list to match the current six-smell prompt/scorer.]
