param(
    [string]$WorkspaceRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$TemplatePath = "C:\Users\zafri\Downloads\PSM 1\User Acceptance Testing.docx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Escape-Xml {
    param([string]$Text)

    if ($null -eq $Text) {
        return ""
    }

    return [System.Security.SecurityElement]::Escape($Text)
}

function Strip-CodeTicks {
    param([string]$Text)

    if ($null -eq $Text) {
        return ""
    }

    return $Text.Replace([string][char]96, "")
}

function New-RunXml {
    param(
        [string]$Text,
        [int]$FontSize = 24,
        [switch]$Bold,
        [switch]$Italic
    )

    $runProperties = New-Object System.Collections.Generic.List[string]
    if ($Bold) {
        $runProperties.Add("<w:b/>")
    }
    if ($Italic) {
        $runProperties.Add("<w:i/>")
    }
    if ($FontSize -gt 0) {
        $runProperties.Add(('<w:sz w:val="{0}"/>' -f $FontSize))
        $runProperties.Add(('<w:szCs w:val="{0}"/>' -f $FontSize))
    }

    $runPropertiesXml = if ($runProperties.Count -gt 0) {
        "<w:rPr>$($runProperties -join '')</w:rPr>"
    } else {
        ""
    }

    return ('<w:r>{0}<w:t xml:space="preserve">{1}</w:t></w:r>' -f $runPropertiesXml, (Escape-Xml $Text))
}

function New-ParagraphXml {
    param(
        [string[]]$Runs,
        [string]$Style = "BodyText",
        [string]$Justification = "left",
        [int]$Before = 0,
        [int]$After = 0
    )

    $paragraphProperties = New-Object System.Collections.Generic.List[string]
    if ($Style) {
        $paragraphProperties.Add(('<w:pStyle w:val="{0}"/>' -f $Style))
    }
    if ($Before -gt 0 -or $After -gt 0) {
        $paragraphProperties.Add(('<w:spacing w:before="{0}" w:after="{1}"/>' -f $Before, $After))
    }
    if ($Justification) {
        $paragraphProperties.Add(('<w:jc w:val="{0}"/>' -f $Justification))
    }

    return "<w:p><w:pPr>$($paragraphProperties -join '')</w:pPr>$($Runs -join '')</w:p>"
}

function New-PageBreakParagraphXml {
    return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'
}

function New-TableCellParagraphXml {
    param(
        [string]$Text,
        [string]$Justification = "left",
        [switch]$Bold
    )

    $runs = @(New-RunXml -Text $Text -FontSize 24 -Bold:$Bold)
    return ('<w:p><w:pPr><w:pStyle w:val="TableParagraph"/><w:jc w:val="{0}"/></w:pPr>{1}</w:p>' -f $Justification, ($runs -join ''))
}

function New-LabelValueTableXml {
    param(
        [object[]]$Rows,
        [int]$Indent = 580
    )

    $tableRows = New-Object System.Collections.Generic.List[string]
    foreach ($row in $Rows) {
        $label = Strip-CodeTicks $row.Label
        $value = Strip-CodeTicks $row.Value
        $tableRows.Add(@"
<w:tr>
  <w:tc>
    <w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr>
    $(New-TableCellParagraphXml -Text $label -Bold)
  </w:tc>
  <w:tc>
    <w:tcPr><w:tcW w:w="300" w:type="dxa"/></w:tcPr>
    $(New-TableCellParagraphXml -Text ":" -Justification "center")
  </w:tc>
  <w:tc>
    <w:tcPr><w:tcW w:w="5600" w:type="dxa"/></w:tcPr>
    $(New-TableCellParagraphXml -Text $value)
  </w:tc>
</w:tr>
"@)
    }

    return @"
<w:tbl>
  <w:tblPr>
    <w:tblStyle w:val="TableGrid"/>
    <w:tblW w:w="0" w:type="auto"/>
    <w:tblInd w:w="$Indent" w:type="dxa"/>
    <w:tblLayout w:type="fixed"/>
    <w:tblLook w:val="01E0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="0"/>
  </w:tblPr>
  <w:tblGrid>
    <w:gridCol w:w="2500"/>
    <w:gridCol w:w="300"/>
    <w:gridCol w:w="5600"/>
  </w:tblGrid>
  $($tableRows -join "`n")
</w:tbl>
"@
}

function Get-MarkdownDocument {
    param(
        [string]$Path,
        [string]$Role
    )

    $lines = Get-Content -LiteralPath $Path
    $document = [pscustomobject]@{
        Role = $Role
        MarkdownPath = $Path
        Title = ""
        Date = ""
        Intro = [System.Collections.Generic.List[string]]::new()
        Sections = [System.Collections.Generic.List[object]]::new()
    }

    $currentSection = $null

    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if (-not $trimmed) {
            continue
        }

        if ($trimmed.StartsWith("# ")) {
            $document.Title = Strip-CodeTicks $trimmed.Substring(2).Trim()
            continue
        }

        if ($trimmed.StartsWith("Date:")) {
            $document.Date = Strip-CodeTicks $trimmed.Substring(5).Trim()
            continue
        }

        if ($trimmed.StartsWith("## ")) {
            $section = [pscustomobject]@{
                Heading = Strip-CodeTicks $trimmed.Substring(3).Trim()
                Lines = [System.Collections.Generic.List[string]]::new()
            }
            $document.Sections.Add($section)
            $currentSection = $section
            continue
        }

        if ($null -eq $currentSection) {
            $document.Intro.Add($trimmed)
        } else {
            $currentSection.Lines.Add($trimmed)
        }
    }

    return $document
}

function New-QuestionParagraphXml {
    param([string]$Line)

    if ($Line -match '^(?<number>\d+)\.\s+(?<question>.+?)\s+`(?<answer>[^`]+)`\s*$') {
        $number = $Matches.number
        $question = Strip-CodeTicks $Matches.question
        $answer = Strip-CodeTicks $Matches.answer
        return New-ParagraphXml -Runs @(
            (New-RunXml -Text "$number. $question" -FontSize 24),
            (New-RunXml -Text " [$answer]" -FontSize 24 -Italic)
        ) -Style "BodyText" -Justification "left"
    }

    if ($Line -match '^(?<number>\d+)\.\s+(?<question>.+)$') {
        $number = $Matches.number
        $question = Strip-CodeTicks $Matches.question
        return New-ParagraphXml -Runs @(
            (New-RunXml -Text "$number. $question" -FontSize 24)
        ) -Style "BodyText" -Justification "left"
    }

    return New-ParagraphXml -Runs @(
        (New-RunXml -Text (Strip-CodeTicks $Line) -FontSize 24)
    ) -Style "BodyText" -Justification "left"
}

function New-BulletParagraphXml {
    param([string]$Line)

    $text = if ($Line -match '^-\s+(?<content>.+)$') {
        Strip-CodeTicks $Matches.content
    } else {
        Strip-CodeTicks $Line
    }

    return New-ParagraphXml -Runs @(
        (New-RunXml -Text "- $text" -FontSize 24)
    ) -Style "BodyText" -Justification "left"
}

function New-SectionHeadingXml {
    param([string]$Heading)

    return New-ParagraphXml -Runs @(
        (New-RunXml -Text (Strip-CodeTicks $Heading) -FontSize 28 -Bold)
    ) -Style "BodyText" -Justification "left" -Before 240 -After 80
}

function New-IntroParagraphXml {
    param([string]$Line)

    return New-ParagraphXml -Runs @(
        (New-RunXml -Text (Strip-CodeTicks $Line) -FontSize 24)
    ) -Style "BodyText" -Justification "left"
}

function Get-CoverDateText {
    param([string]$DateText)

    if (-not $DateText) {
        return (Get-Date).ToString("d MMMM yyyy", [System.Globalization.CultureInfo]::InvariantCulture).ToUpperInvariant()
    }

    try {
        return ([datetime]::Parse($DateText, [System.Globalization.CultureInfo]::InvariantCulture)).ToString("d MMMM yyyy", [System.Globalization.CultureInfo]::InvariantCulture).ToUpperInvariant()
    }
    catch {
        return $DateText.ToUpperInvariant()
    }
}

function Get-TemplateParts {
    param([string]$Path)

    $stream = [System.IO.FileStream]::new(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Read, $false)
    try {
        $documentEntry = $archive.GetEntry("word/document.xml")
        $reader = [System.IO.StreamReader]::new($documentEntry.Open())
        try {
            $documentXml = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $archive.Dispose()
        $stream.Dispose()
    }

    $document = [xml]$documentXml
    $namespaceManager = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
    $namespaceManager.AddNamespace("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main")

    $imageParagraph = $document.SelectSingleNode("//w:body/w:p[w:r/w:drawing]", $namespaceManager)
    if ($null -eq $imageParagraph) {
        throw "Template image paragraph was not found in $Path"
    }

    return [pscustomobject]@{
        ImageParagraphXml = $imageParagraph.OuterXml
    }
}

function Copy-TemplateFile {
    param(
        [string]$SourcePath,
        [string]$DestinationPath
    )

    $inputStream = [System.IO.FileStream]::new(
        $SourcePath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    $outputStream = [System.IO.FileStream]::new(
        $DestinationPath,
        [System.IO.FileMode]::Create,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )

    try {
        $inputStream.CopyTo($outputStream)
    }
    finally {
        $outputStream.Dispose()
        $inputStream.Dispose()
    }
}

function New-CorePropertiesXml {
    param(
        [string]$Title,
        [string]$Role
    )

    $now = [datetime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ", [System.Globalization.CultureInfo]::InvariantCulture)
    $safeTitle = Escape-Xml $Title
    $safeSubject = Escape-Xml ("User Acceptance Testing - $Role")

    return @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>$safeTitle</dc:title>
  <dc:subject>$safeSubject</dc:subject>
  <dc:creator>GitHub Copilot</dc:creator>
  <cp:keywords>UAT, Taska Attendance System, $Role</cp:keywords>
  <dc:description>$safeSubject</dc:description>
  <cp:lastModifiedBy>GitHub Copilot</cp:lastModifiedBy>
  <cp:revision>1</cp:revision>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>
"@
}

function New-DocumentXml {
    param(
        [pscustomobject]$MarkdownDocument,
        [pscustomobject]$TemplateParts
    )

    $content = New-Object System.Collections.Generic.List[string]

    $content.Add($TemplateParts.ImageParagraphXml)
    $content.Add((New-ParagraphXml -Runs @(
        (New-RunXml -Text "USER ACCEPTANCE TESTING" -FontSize 32 -Bold)
    ) -Style "BodyText" -Justification "center" -Before 120 -After 40))
    $content.Add((New-ParagraphXml -Runs @(
        (New-RunXml -Text ("{0} QUESTIONNAIRE" -f $MarkdownDocument.Role.ToUpperInvariant()) -FontSize 30 -Bold)
    ) -Style "BodyText" -Justification "center" -After 40))
    $content.Add((New-ParagraphXml -Runs @(
        (New-RunXml -Text "TASKA ZURAH STUDENT MANAGEMENT SYSTEM" -FontSize 28 -Bold)
    ) -Style "BodyText" -Justification "center" -After 160))

    $coverRows = @(
        [pscustomobject]@{ Label = "DOCUMENT TYPE"; Value = "User Acceptance Testing" },
        [pscustomobject]@{ Label = "TARGET USER"; Value = $MarkdownDocument.Role },
        [pscustomobject]@{ Label = "SYSTEM"; Value = "Taska Zurah Student Management System" },
        [pscustomobject]@{ Label = "DATE"; Value = (Get-CoverDateText -DateText $MarkdownDocument.Date) },
        [pscustomobject]@{ Label = "SOURCE FILE"; Value = [System.IO.Path]::GetFileName($MarkdownDocument.MarkdownPath) }
    )
    $content.Add((New-LabelValueTableXml -Rows $coverRows))
    $content.Add((New-PageBreakParagraphXml))

    $content.Add((New-ParagraphXml -Runs @(
        (New-RunXml -Text $MarkdownDocument.Title -FontSize 32 -Bold)
    ) -Style "BodyText" -Justification "left" -After 80))

    if ($MarkdownDocument.Date) {
        $content.Add((New-ParagraphXml -Runs @(
            (New-RunXml -Text ("Date: {0}" -f $MarkdownDocument.Date) -FontSize 24)
        ) -Style "BodyText" -Justification "left" -After 120))
    }

    foreach ($introLine in $MarkdownDocument.Intro) {
        $content.Add((New-IntroParagraphXml -Line $introLine))
    }

    foreach ($section in $MarkdownDocument.Sections) {
        $content.Add((New-SectionHeadingXml -Heading $section.Heading))

        if ($section.Heading -eq "Tester Details") {
            $rows = New-Object System.Collections.Generic.List[object]
            foreach ($line in $section.Lines) {
                if ($line -match '^-\s+(?<label>[^:]+):\s*(?<value>.*)$') {
                    $rows.Add([pscustomobject]@{
                        Label = $Matches.label.Trim()
                        Value = (Strip-CodeTicks $Matches.value.Trim())
                    })
                }
            }

            if ($rows.Count -gt 0) {
                $content.Add((New-LabelValueTableXml -Rows $rows -Indent 0))
            }
            continue
        }

        foreach ($line in $section.Lines) {
            if ($line -match '^\d+\.\s+') {
                $content.Add((New-QuestionParagraphXml -Line $line))
                continue
            }

            if ($line -match '^-\s+') {
                $content.Add((New-BulletParagraphXml -Line $line))
                continue
            }

            $content.Add((New-IntroParagraphXml -Line $line))
        }
    }

    $sectionProperties = '<w:sectPr><w:footerReference w:type="default" r:id="rId9"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="2268" w:header="709" w:footer="709" w:gutter="0"/><w:pgNumType w:start="1"/><w:cols w:space="708"/><w:docGrid w:linePitch="360"/></w:sectPr>'

    return @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing" mc:Ignorable="w14 wp14">
  <w:body>
    $($content -join "`n")
    $sectionProperties
  </w:body>
</w:document>
"@
}

function Set-ZipEntryText {
    param(
        [System.IO.Compression.ZipArchive]$Archive,
        [string]$EntryPath,
        [string]$Content
    )

    $existing = $Archive.GetEntry($EntryPath)
    if ($null -ne $existing) {
        $existing.Delete()
    }

    $entry = $Archive.CreateEntry($EntryPath)
    $stream = $entry.Open()
    $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
    try {
        $writer.Write($Content)
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $TemplatePath)) {
    throw "Template not found: $TemplatePath"
}

$templateParts = Get-TemplateParts -Path $TemplatePath

$documents = @(
    [pscustomobject]@{
        Role = "Admin"
        Markdown = Join-Path $WorkspaceRoot "doc\uat-admin-questionnaire-2026-05-05.md"
        Output = Join-Path $WorkspaceRoot "doc\uat-admin-questionnaire-2026-05-05.docx"
    },
    [pscustomobject]@{
        Role = "Teacher"
        Markdown = Join-Path $WorkspaceRoot "doc\uat-teacher-questionnaire-2026-05-05.md"
        Output = Join-Path $WorkspaceRoot "doc\uat-teacher-questionnaire-2026-05-05.docx"
    },
    [pscustomobject]@{
        Role = "Parent"
        Markdown = Join-Path $WorkspaceRoot "doc\uat-parent-questionnaire-2026-05-05.md"
        Output = Join-Path $WorkspaceRoot "doc\uat-parent-questionnaire-2026-05-05.docx"
    }
)

foreach ($document in $documents) {
    if (-not (Test-Path -LiteralPath $document.Markdown)) {
        throw "Markdown file not found: $($document.Markdown)"
    }

    $markdownDocument = Get-MarkdownDocument -Path $document.Markdown -Role $document.Role
    $documentXml = New-DocumentXml -MarkdownDocument $markdownDocument -TemplateParts $templateParts
    $coreXml = New-CorePropertiesXml -Title $markdownDocument.Title -Role $document.Role

    Copy-TemplateFile -SourcePath $TemplatePath -DestinationPath $document.Output

    $archive = [System.IO.Compression.ZipFile]::Open($document.Output, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        Set-ZipEntryText -Archive $archive -EntryPath "word/document.xml" -Content $documentXml
        Set-ZipEntryText -Archive $archive -EntryPath "docProps/core.xml" -Content $coreXml
    }
    finally {
        $archive.Dispose()
    }

    Write-Output "Created $($document.Output)"
}