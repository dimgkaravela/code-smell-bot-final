$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$datasetRoot = Join-Path $repoRoot "evaluation-dataset\mlcq_selected_cases"
$metadataPath = Join-Path $datasetRoot "metadata.json"
$casesDir = Join-Path $datasetRoot "cases"

New-Item -ItemType Directory -Force -Path $casesDir | Out-Null

function Convert-Severity {
    param([string] $Severity)

    $safeSeverity = if ($null -eq $Severity) { "" } else { $Severity }
    switch ($safeSeverity.ToLowerInvariant()) {
        "critical" { "Major" }
        "major" { "Major" }
        "minor" { "Minor" }
        default { "Major" }
    }
}

function Get-RelativeOutputPath {
    param($Item)

    $relativePath = [string] $Item.output_file
    if ($relativePath.StartsWith("mlcq_selected_cases", [System.StringComparison]::OrdinalIgnoreCase)) {
        $relativePath = $relativePath.Substring("mlcq_selected_cases".Length).TrimStart([char[]] @("\", "/"))
    }
    return $relativePath.Replace("\", "/")
}

function Get-TargetName {
    param($Item)

    $codeName = [string] $Item.code_name
    if ([string] $Item.type -eq "class") {
        return ($codeName -split "\.")[-1]
    }

    if ($codeName.Contains("#")) {
        $target = $codeName.Substring($codeName.IndexOf("#") + 1)
        return (($target -split "\s+", 2)[0]).Trim()
    }

    $target = ($codeName -split "\s+", 2)[0]
    return (($target -split "\.")[-1]).Trim()
}

function Find-TargetLine {
    param(
        [string] $FilePath,
        [string] $TargetType,
        [string] $TargetName,
        [int] $ExpectedLine
    )

    $lines = Get-Content -LiteralPath $FilePath
    $escapedName = [regex]::Escape($TargetName)
    if ($TargetType -eq "CLASS") {
        $pattern = "\b(class|interface|enum|record)\s+$escapedName\b"
    } else {
        $pattern = "\b$escapedName\s*\("
    }

    $candidates = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) {
            $lineNumber = $i + 1
            $candidates += [pscustomobject]@{
                Line = $lineNumber
                Distance = [Math]::Abs($lineNumber - $ExpectedLine)
            }
        }
    }

    if ($candidates.Count -eq 0) {
        return $ExpectedLine
    }

    return [int] (($candidates | Sort-Object Distance, Line | Select-Object -First 1).Line)
}

function New-CaseId {
    param([string] $RelativePath)

    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($RelativePath).ToLowerInvariant()
    $slug = ($baseName -replace "[^a-z0-9]+", "-").Trim("-")
    return "mlcq-$slug"
}

function New-Note {
    param($Item)

    return "MLCQ id $($Item.mlcq_id) labels $($Item.code_name) as $($Item.smell) ($($Item.type)) at original lines $($Item.start_line)-$($Item.end_line); converted to $($Item.thesis_rule). Source: $($Item.link)"
}

$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$categories = @("large_class", "long_method", "feature_envy")
$caseCount = 0

foreach ($category in $categories) {
    foreach ($item in $metadata.$category) {
        $relativePath = Get-RelativeOutputPath $item
        $localFile = Join-Path $datasetRoot ($relativePath.Replace("/", [string] [System.IO.Path]::DirectorySeparatorChar))
        if (-not (Test-Path -LiteralPath $localFile)) {
            throw "Referenced MLCQ source file does not exist: $localFile"
        }

        $targetType = if ([string] $item.type -eq "class") { "CLASS" } else { "METHOD" }
        $targetName = Get-TargetName $item
        $expectedLocalLine = [int] $item.start_line + 12
        $line = Find-TargetLine $localFile $targetType $targetName $expectedLocalLine
        $caseId = New-CaseId $relativePath
        $evaluationPath = "evaluation-dataset/mlcq_selected_cases/$relativePath"

        $case = [ordered]@{
            id = $caseId
            source = "MLCQ-derived"
            repository = "evaluation/MLCQ"
            prNumber = 1
            files = @(
                [ordered]@{
                    filename = $evaluationPath
                    status = "added"
                    fileContentPath = "../$relativePath"
                }
            )
            labels = @(
                [ordered]@{
                    file = $evaluationPath
                    line = $line
                    rule = [string] $item.thesis_rule
                    targetType = $targetType
                    targetName = $targetName
                    severity = Convert-Severity $item.severity
                    note = New-Note $item
                }
            )
            evaluateRefactoring = $false
        }

        $casePath = Join-Path $casesDir "$caseId.json"
        $json = $case | ConvertTo-Json -Depth 20
        $utf8NoBom = New-Object System.Text.UTF8Encoding -ArgumentList $false
        [System.IO.File]::WriteAllText($casePath, $json + [Environment]::NewLine, $utf8NoBom)
        $caseCount++
    }
}

Write-Host "Generated $caseCount MLCQ evaluation cases in $casesDir"
