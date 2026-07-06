# Appendix Evaluation Tables

Generated from the current thesis-facing evaluation artifacts under `out/evaluation` and the dataset labels under `evaluation-dataset`. The tables use the audited Figshare subset; raw Figshare and MLCQ quota outputs are treated as intermediate artifacts and are not included here. Metric values are shown to three decimals. Zero-denominator precision, recall, and F1 use the evaluator convention of `0.000`.

Validation status: no inconsistent rows found. For every displayed result row, `predictions = TP + FP`, `precision = TP / (TP + FP)`, `recall = TP / (TP + FN)`, and `F1 = 2 * precision * recall / (precision + recall)`, with the zero-denominator convention above.

## Dataset Inventory

| dataset | cases | detection labels | refactoring labels | smell/refactoring categories | notes |
|---|---:|---:|---:|---|---|
| SmellyCode | 5 | 19 | 0 | Long Method; Long Parameter List; Duplicate Code; Feature Envy; Message Chains | Cleaned Java subset; inline smell comments removed; labels/lines normalized and target metadata added. |
| SACS | 20 | 52 | 0 | Long Method; Large Class; Feature Envy | Positive Java subset from SACS/GCSM; refactoring evaluation disabled. |
| Figshare audited | 9 | 12 | 0 | Long Parameter List; Message Chains | Audited subset; excludes 16 raw Message Chains labels that did not match the bot's expression-level definition. |
| SoftDevl | 19 | 26 | 26 | Long Method; Duplicate Code; Large Class; Message Chains; Extract Method; Extract Class; Move Method; Form Template Method; Parameterize Method; Substitute Algorithm; Hide Delegate | Custom backlog-derived project material with both smell and refactoring labels. |
| Refactoring cases | 37 | 0 | 37 | Extract Method; Extract Class; Move Method; Move Attribute | RefactoringMiner-derived oracle cases; detection disabled; labels use line 0 so source line location is not enforced. |

## Detection Label Counts Per Dataset And Smell

| dataset | Long Method | Long Parameter List | Duplicate Code | Large Class | Feature Envy | Message Chains | total |
|---|---:|---:|---:|---:|---:|---:|---:|
| SmellyCode | 5 | 6 | 5 | 0 | 1 | 2 | 19 |
| SACS | 26 | 0 | 0 | 12 | 14 | 0 | 52 |
| Figshare audited | 0 | 7 | 0 | 0 | 0 | 5 | 12 |
| SoftDevl | 3 | 0 | 18 | 4 | 0 | 1 | 26 |
| Refactoring cases | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## Refactoring Label Counts

| dataset | Extract Method | Extract Class | Move Method | Move Attribute | Form Template Method | Parameterize Method | Substitute Algorithm | Hide Delegate | total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| SmellyCode | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| SACS | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Figshare audited | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| SoftDevl | 1 | 4 | 1 | 0 | 10 | 4 | 5 | 1 | 26 |
| Refactoring cases | 10 | 9 | 9 | 9 | 0 | 0 | 0 | 0 | 37 |

## Prompt 1 Detailed Detection Results

| dataset | scope | matching strategy | TP | FP | FN | predictions | precision | recall | F1 |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SmellyCode | file | strict-line | 12 | 4 | 7 | 16 | 0.750 | 0.632 | 0.686 |
| SmellyCode | file | target-name | 12 | 2 | 7 | 14 | 0.857 | 0.632 | 0.727 |
| SmellyCode | diff | strict-line | 5 | 13 | 14 | 18 | 0.278 | 0.263 | 0.270 |
| SmellyCode | diff | target-name | 12 | 4 | 7 | 16 | 0.750 | 0.632 | 0.686 |
| SACS | file | strict-line | 7 | 15 | 45 | 22 | 0.318 | 0.135 | 0.189 |
| SACS | file | target-name | 7 | 23 | 45 | 30 | 0.233 | 0.135 | 0.171 |
| SACS | diff | strict-line | 0 | 28 | 52 | 28 | 0.000 | 0.000 | 0.000 |
| SACS | diff | target-name | 8 | 12 | 44 | 20 | 0.400 | 0.154 | 0.222 |
| Figshare audited | file | target-name | 2 | 14 | 10 | 16 | 0.125 | 0.167 | 0.143 |
| Figshare audited | diff | target-name | 2 | 14 | 10 | 16 | 0.125 | 0.167 | 0.143 |
| SoftDevl | file | strict-line | 9 | 20 | 17 | 29 | 0.310 | 0.346 | 0.327 |
| SoftDevl | file | target-name | 9 | 18 | 17 | 27 | 0.333 | 0.346 | 0.340 |
| SoftDevl | diff | strict-line | 7 | 18 | 19 | 25 | 0.280 | 0.269 | 0.275 |
| SoftDevl | diff | target-name | 9 | 20 | 17 | 29 | 0.310 | 0.346 | 0.327 |

## Prompt 2 Detailed Target-Based Detection Results

| dataset | scope | TP | FP | FN | predictions | precision | recall | F1 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| SmellyCode | file | 10 | 5 | 9 | 15 | 0.667 | 0.526 | 0.588 |
| SmellyCode | diff | 10 | 5 | 9 | 15 | 0.667 | 0.526 | 0.588 |
| SACS | file | 7 | 25 | 45 | 32 | 0.219 | 0.135 | 0.167 |
| SACS | diff | 11 | 15 | 41 | 26 | 0.423 | 0.212 | 0.282 |
| Figshare audited | file | 1 | 13 | 11 | 14 | 0.071 | 0.083 | 0.077 |
| Figshare audited | diff | 2 | 15 | 10 | 17 | 0.118 | 0.167 | 0.138 |
| SoftDevl | file | 9 | 23 | 17 | 32 | 0.281 | 0.346 | 0.310 |
| SoftDevl | diff | 9 | 22 | 17 | 31 | 0.290 | 0.346 | 0.316 |

## Prompt 2 Rule-Level Results

| dataset | scope | rule | labels | predictions | TP | FP | FN | precision | recall | F1 |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SmellyCode | file | Long Method | 5 | 3 | 3 | 0 | 2 | 1.000 | 0.600 | 0.750 |
| SmellyCode | file | Long Parameter List | 6 | 6 | 5 | 1 | 1 | 0.833 | 0.833 | 0.833 |
| SmellyCode | file | Duplicate Code | 5 | 0 | 0 | 0 | 5 | 0.000 | 0.000 | 0.000 |
| SmellyCode | file | Large Class | 0 | 3 | 0 | 3 | 0 | 0.000 | 0.000 | 0.000 |
| SmellyCode | file | Feature Envy | 1 | 1 | 0 | 1 | 1 | 0.000 | 0.000 | 0.000 |
| SmellyCode | file | Message Chains | 2 | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 |
| SmellyCode | diff | Long Method | 5 | 3 | 3 | 0 | 2 | 1.000 | 0.600 | 0.750 |
| SmellyCode | diff | Long Parameter List | 6 | 5 | 5 | 0 | 1 | 1.000 | 0.833 | 0.909 |
| SmellyCode | diff | Duplicate Code | 5 | 0 | 0 | 0 | 5 | 0.000 | 0.000 | 0.000 |
| SmellyCode | diff | Large Class | 0 | 3 | 0 | 3 | 0 | 0.000 | 0.000 | 0.000 |
| SmellyCode | diff | Feature Envy | 1 | 2 | 0 | 2 | 1 | 0.000 | 0.000 | 0.000 |
| SmellyCode | diff | Message Chains | 2 | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 |
| SACS | file | Long Method | 26 | 20 | 4 | 16 | 22 | 0.200 | 0.154 | 0.174 |
| SACS | file | Long Parameter List | 0 | 1 | 0 | 1 | 0 | 0.000 | 0.000 | 0.000 |
| SACS | file | Duplicate Code | 0 | 4 | 0 | 4 | 0 | 0.000 | 0.000 | 0.000 |
| SACS | file | Large Class | 12 | 2 | 2 | 0 | 10 | 1.000 | 0.167 | 0.286 |
| SACS | file | Feature Envy | 14 | 4 | 1 | 3 | 13 | 0.250 | 0.071 | 0.111 |
| SACS | file | Message Chains | 0 | 1 | 0 | 1 | 0 | 0.000 | 0.000 | 0.000 |
| SACS | diff | Long Method | 26 | 10 | 3 | 7 | 23 | 0.300 | 0.115 | 0.167 |
| SACS | diff | Duplicate Code | 0 | 4 | 0 | 4 | 0 | 0.000 | 0.000 | 0.000 |
| SACS | diff | Large Class | 12 | 6 | 6 | 0 | 6 | 1.000 | 0.500 | 0.667 |
| SACS | diff | Feature Envy | 14 | 6 | 2 | 4 | 12 | 0.333 | 0.143 | 0.200 |
| Figshare audited | file | Long Method | 0 | 9 | 0 | 9 | 0 | 0.000 | 0.000 | 0.000 |
| Figshare audited | file | Long Parameter List | 7 | 1 | 1 | 0 | 6 | 1.000 | 0.143 | 0.250 |
| Figshare audited | file | Duplicate Code | 0 | 2 | 0 | 2 | 0 | 0.000 | 0.000 | 0.000 |
| Figshare audited | file | Large Class | 0 | 2 | 0 | 2 | 0 | 0.000 | 0.000 | 0.000 |
| Figshare audited | file | Message Chains | 5 | 0 | 0 | 0 | 5 | 0.000 | 0.000 | 0.000 |
| Figshare audited | diff | Long Method | 0 | 10 | 0 | 10 | 0 | 0.000 | 0.000 | 0.000 |
| Figshare audited | diff | Long Parameter List | 7 | 2 | 2 | 0 | 5 | 1.000 | 0.286 | 0.444 |
| Figshare audited | diff | Large Class | 0 | 5 | 0 | 5 | 0 | 0.000 | 0.000 | 0.000 |
| Figshare audited | diff | Message Chains | 5 | 0 | 0 | 0 | 5 | 0.000 | 0.000 | 0.000 |
| SoftDevl | file | Long Method | 3 | 8 | 3 | 5 | 0 | 0.375 | 1.000 | 0.545 |
| SoftDevl | file | Long Parameter List | 0 | 2 | 0 | 2 | 0 | 0.000 | 0.000 | 0.000 |
| SoftDevl | file | Duplicate Code | 18 | 12 | 4 | 8 | 14 | 0.333 | 0.222 | 0.267 |
| SoftDevl | file | Large Class | 4 | 4 | 1 | 3 | 3 | 0.250 | 0.250 | 0.250 |
| SoftDevl | file | Feature Envy | 0 | 2 | 0 | 2 | 0 | 0.000 | 0.000 | 0.000 |
| SoftDevl | file | Message Chains | 1 | 4 | 1 | 3 | 0 | 0.250 | 1.000 | 0.400 |
| SoftDevl | diff | Long Method | 3 | 8 | 3 | 5 | 0 | 0.375 | 1.000 | 0.545 |
| SoftDevl | diff | Duplicate Code | 18 | 11 | 4 | 7 | 14 | 0.364 | 0.222 | 0.276 |
| SoftDevl | diff | Large Class | 4 | 3 | 1 | 2 | 3 | 0.333 | 0.250 | 0.286 |
| SoftDevl | diff | Feature Envy | 0 | 6 | 0 | 6 | 0 | 0.000 | 0.000 | 0.000 |
| SoftDevl | diff | Message Chains | 1 | 3 | 1 | 2 | 0 | 0.333 | 1.000 | 0.500 |

## Annotated-Only Scoring Results

| dataset | scope | prompt version | precision | recall | F1 |
|---|---|---|---:|---:|---:|
| SmellyCode | file | Prompt 1 | 0.923 | 0.632 | 0.750 |
| SmellyCode | diff | Prompt 1 | 0.857 | 0.632 | 0.727 |
| SmellyCode | file | Prompt 2 | 0.833 | 0.526 | 0.645 |
| SmellyCode | diff | Prompt 2 | 0.833 | 0.526 | 0.645 |
| SACS | file | Prompt 1 | 0.259 | 0.135 | 0.177 |
| SACS | diff | Prompt 1 | 0.471 | 0.154 | 0.232 |
| SACS | file | Prompt 2 | 0.269 | 0.135 | 0.179 |
| SACS | diff | Prompt 2 | 0.500 | 0.212 | 0.297 |
| Figshare audited | file | Prompt 1 | 1.000 | 0.167 | 0.286 |
| Figshare audited | diff | Prompt 1 | 1.000 | 0.167 | 0.286 |
| Figshare audited | file | Prompt 2 | 1.000 | 0.083 | 0.154 |
| Figshare audited | diff | Prompt 2 | 1.000 | 0.167 | 0.286 |
| SoftDevl | file | Prompt 1 | 0.409 | 0.346 | 0.375 |
| SoftDevl | diff | Prompt 1 | 0.360 | 0.346 | 0.353 |
| SoftDevl | file | Prompt 2 | 0.321 | 0.346 | 0.333 |
| SoftDevl | diff | Prompt 2 | 0.360 | 0.346 | 0.353 |

## Refactoring Results

| dataset | scope | TP | FP | FN | predictions | precision | recall | F1 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| SoftDevl | file | 4 | 28 | 22 | 32 | 0.125 | 0.154 | 0.138 |
| SoftDevl | diff | 4 | 27 | 22 | 31 | 0.129 | 0.154 | 0.140 |
| Refactoring cases | file | 11 | 27 | 26 | 38 | 0.289 | 0.297 | 0.293 |
| Refactoring cases | diff | 12 | 28 | 25 | 40 | 0.300 | 0.324 | 0.312 |
