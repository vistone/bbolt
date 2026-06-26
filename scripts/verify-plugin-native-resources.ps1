param(
    [Parameter(Mandatory = $true)]
    [string] $ZipPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ZipPath)) {
    throw "Plugin zip not found: $ZipPath"
}

$required = @(
    'native/win32-x86-64/bolt.dll',
    'native/linux-x86-64/libbolt.so',
    'native/darwin-x86-64/libbolt.dylib',
    'native/darwin-aarch64/libbolt.dylib'
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Add-ZipEntries {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ArchivePath,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string] $Prefix,
        [Parameter(Mandatory = $true)]
        [hashtable] $Entries
    )

    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        foreach ($entry in $archive.Entries) {
            $normalized = ($Prefix + $entry.FullName) -replace '\\', '/'
            $Entries[$normalized] = $entry.Length
        }
    }
    finally {
        $archive.Dispose()
    }
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("bbolt-plugin-verify-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempDir | Out-Null

try {
    $entries = @{}
    $resolvedZipPath = (Resolve-Path -LiteralPath $ZipPath).Path
    Add-ZipEntries -ArchivePath $resolvedZipPath -Prefix '' -Entries $entries

    $outer = [System.IO.Compression.ZipFile]::OpenRead($resolvedZipPath)
    try {
        foreach ($entry in $outer.Entries) {
            if (-not $entry.FullName.EndsWith('.jar')) {
                continue
            }

            $jarPath = Join-Path $tempDir ([System.IO.Path]::GetFileName($entry.FullName))
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $jarPath, $true)
            Add-ZipEntries -ArchivePath $jarPath -Prefix ($entry.FullName + '!/') -Entries $entries
        }
    }
    finally {
        $outer.Dispose()
    }

    foreach ($suffix in $required) {
        $match = $entries.Keys | Where-Object { $_.EndsWith($suffix) } | Select-Object -First 1
        if (-not $match) {
            throw "Missing native resource ending with: $suffix"
        }
        if ($entries[$match] -le 0) {
            throw "Native resource is empty: $match"
        }
        Write-Host "Verified $match ($($entries[$match]) bytes)"
    }
}
finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
