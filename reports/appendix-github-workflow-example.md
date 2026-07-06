# Appendix G - GitHub Workflow Example

This appendix records the GitHub Actions workflows used to run the code smell bot on pull requests and to manually rerun the bot on a selected pull request.

## G.1 Pull Request Workflow

Workflow file: `.github/workflows/pr-llm-smells.yml`

This workflow runs automatically when a pull request is opened, synchronized, or reopened. It checks out the repository, sets up Java 21, builds the Maven package, runs the bot against the pull request, posts a PR comment, and uploads the generated `out/` directory as an artifact for debugging.

```yaml
name: PR LLM Smell Report

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read
  pull-requests: write

concurrency:
  group: pr-llm-${{ github.event.pull_request.head.repo.full_name }}-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  analyze:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: 'maven'

      - name: Build shaded jar
        run: mvn -q -DskipTests package

      - name: List target
        run: ls -lah target

      - name: Run code smell bot (gemini)
        env:
          GITHUB_TOKEN: ${{ secrets.BOT_PAT }}
          REPOSITORY: ${{ github.repository }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          POST_COMMENT: "true"
          FETCH_CONTENTS: "true"
          FETCH_RELATED_FILES: "true"
          MAX_FILES: "0"

          LLM_PROVIDER: gemini
          GEMINI_MODEL: gemini-2.5-flash-lite
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}

          LLM_MAX_FILES_PER_CHUNK: "4"
          LLM_MAX_PATCH_CHARS: "18000"
          MAX_FILE_CONTENT_CHARS: "12000"
          MAX_RELATED_FILES_PER_FILE: "2"
          MAX_RELATED_FILE_CHARS: "4000"
        run: |
          set -e
          JAR="target/code-smell-bot-0.1.0-SNAPSHOT.jar"
          echo "Running: $JAR"
          java -jar "$JAR"

      - name: Upload bot output artifact (for debugging)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: code-smell-bot-out
          path: out/
          if-no-files-found: warn
```

The workflow does not set `ANALYSIS_SCOPE` or `VALIDATION_MODE`, so the runtime defaults are used: `ANALYSIS_SCOPE=diff` and `VALIDATION_MODE=DIFF_TARGET_NAME`.

## G.2 Manual Rerun Workflow

Workflow file: `.github/workflows/rerun-llm-manual.yml`

This workflow is triggered manually with `workflow_dispatch`. It is used when a pull request needs to be reanalyzed after prompt, model, or configuration changes, or when the bot output should be regenerated without pushing a new commit to the pull request.

```yaml
name: Rerun LLM Smell Report (Manual)

on:
  workflow_dispatch:
    inputs:
      pr_number:
        description: "PR number to analyze"
        required: true
        type: number
      provider:
        description: "LLM provider (gemini or openai)"
        required: false
        default: "gemini"
      model:
        description: "LLM model name"
        required: false
        default: "gemini-2.5-flash-lite"
      max_files:
        description: "Max changed files to analyze (0 means no limit)"
        required: false
        default: "0"
      post_comment:
        description: "Post the generated comment back to the PR"
        required: false
        type: boolean
        default: false
      debug_smells:
        description: "Print the prompt, raw LLM output, and parsed findings"
        required: false
        type: boolean
        default: true

permissions:
  contents: read
  pull-requests: write

jobs:
  rerun:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: 'maven'

      - name: Build jar
        run: mvn -q -DskipTests package

      - name: Run bot on provided PR
        env:
          GITHUB_TOKEN: ${{ secrets.BOT_PAT }}
          REPOSITORY: ${{ github.repository }}
          PR_NUMBER: ${{ inputs.pr_number }}
          POST_COMMENT: ${{ inputs.post_comment }}
          MAX_FILES: ${{ inputs.max_files }}
          FETCH_CONTENTS: "true"
          FETCH_RELATED_FILES: "true"
          LLM_PROVIDER: ${{ inputs.provider }}
          GEMINI_MODEL: ${{ inputs.model }}
          OPENAI_MODEL: ${{ inputs.model }}
          DEBUG_SMELLS: ${{ inputs.debug_smells }}
          LLM_MAX_FILES_PER_CHUNK: "4"
          LLM_MAX_PATCH_CHARS: "18000"
          MAX_FILE_CONTENT_CHARS: "12000"
          MAX_RELATED_FILES_PER_FILE: "2"
          MAX_RELATED_FILE_CHARS: "4000"

          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: |
          set -e

          echo "Contents of target directory:"
          ls -lah target

          JAR="$(ls -1 target/*.jar | head -n1)"

          echo "Running JAR: $JAR"
          java -jar "$JAR"

      - name: Upload bot output artifact (for debugging)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: code-smell-bot-out
          path: out/
          if-no-files-found: warn
```

Manual inputs:

| input | default | purpose |
|---|---|---|
| `pr_number` | none | Pull request number to analyze. |
| `provider` | `gemini` | LLM provider passed to `LLM_PROVIDER`. |
| `model` | `gemini-2.5-flash-lite` | Model name passed to `GEMINI_MODEL` and `OPENAI_MODEL`. |
| `max_files` | `0` | Maximum changed files to analyze; `0` means no limit. |
| `post_comment` | `false` | Whether the bot posts the generated report back to the PR. |
| `debug_smells` | `true` | Whether prompt, raw LLM response, and parsed findings are printed. |

## G.3 Required Secrets and Environment Variables

GitHub repository secrets:

| secret | required | used by | purpose |
|---|---|---|---|
| `BOT_PAT` | yes | PR workflow and manual workflow | Personal access token used as `GITHUB_TOKEN` so the bot can read PR data and post comments. |
| `GEMINI_API_KEY` | yes for Gemini | PR workflow and manual workflow | API key for Gemini requests. |
| `OPENAI_API_KEY` | only for OpenAI manual reruns | manual workflow | API key for OpenAI-compatible runs when `provider=openai`. |

Runtime environment variables:

| variable | example value | purpose |
|---|---|---|
| `GITHUB_TOKEN` | `${{ secrets.BOT_PAT }}` | Authenticates GitHub API requests. |
| `REPOSITORY` | `${{ github.repository }}` | Repository in `owner/repo` format. |
| `PR_NUMBER` | `${{ github.event.pull_request.number }}` or `${{ inputs.pr_number }}` | Pull request number to analyze. |
| `POST_COMMENT` | `true` or `false` | Controls whether the bot posts a PR comment. |
| `FETCH_CONTENTS` | `true` | Fetches full file contents for context. |
| `FETCH_RELATED_FILES` | `true` | Fetches related files for additional context. |
| `MAX_FILES` | `0` | Limits changed files; `0` means no limit. |
| `LLM_PROVIDER` | `gemini` | Selects the LLM adapter. |
| `GEMINI_MODEL` | `gemini-2.5-flash-lite` | Gemini model used by the workflow. |
| `GEMINI_API_KEY` | `${{ secrets.GEMINI_API_KEY }}` | Gemini API authentication. |
| `OPENAI_MODEL` | `${{ inputs.model }}` | OpenAI model for manual reruns. |
| `OPENAI_API_KEY` | `${{ secrets.OPENAI_API_KEY }}` | OpenAI API authentication. |
| `DEBUG_SMELLS` | `true` | Emits prompts, raw responses, and parsed findings for debugging. |
| `LLM_MAX_FILES_PER_CHUNK` | `4` | Maximum changed files sent in one LLM chunk. |
| `LLM_MAX_PATCH_CHARS` | `18000` | Maximum patch text per chunk. |
| `MAX_FILE_CONTENT_CHARS` | `12000` | Maximum fetched content per file. |
| `MAX_RELATED_FILES_PER_FILE` | `2` | Maximum related files fetched per changed file. |
| `MAX_RELATED_FILE_CHARS` | `4000` | Maximum content per related file. |
| `ANALYSIS_SCOPE` | `diff` | Optional; default for PR mode is `diff`. |
| `VALIDATION_MODE` | `DIFF_TARGET_NAME` | Optional; default for diff scope is target-name validation. |

No real API key, personal access token, or other secret should be committed to the repository.
