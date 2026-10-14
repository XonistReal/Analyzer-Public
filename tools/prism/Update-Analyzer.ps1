# Analyzer's local development updater. Compatible with Windows PowerShell 5.1.
# Prism pre-launch: powershell.exe -NoProfile -ExecutionPolicy Bypass -File
# "$INST_DIR/analyzer-updater/Update-Analyzer.ps1" -InstanceDir "$INST_DIR"
[CmdletBinding()]
param(
    [string] $InstanceDir,
    [switch] $CheckOnly,
    [switch] $Strict
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$script:RecoveryFailure = $false
$script:RecoveryLocation = ''
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-Property($Object, [string] $Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    # Preserve JSON arrays, including one-element arrays, for strict schema checks.
    return ,$property.Value
}

function Get-LocalPath([string] $Path, [string] $BaseDir) {
    if ([string]::IsNullOrWhiteSpace($Path) -or $Path.Length -gt 4096) {
        throw 'A local path is missing or too long.'
    }
    if ($Path.StartsWith('\\') -or $Path.StartsWith('//') -or $Path.Contains(':') -and $Path -notmatch '^[A-Za-z]:[\\/]') {
        throw 'Only local filesystem paths are supported; URLs, UNC paths and drive-relative paths are rejected.'
    }
    if ($Path -match '^[A-Za-z]:' -and $Path.Substring(2).Contains(':')) { throw 'Alternate data stream paths are rejected.' }
    if (-not [IO.Path]::IsPathRooted($Path)) { $Path = [IO.Path]::Combine($BaseDir, $Path) }
    return [IO.Path]::GetFullPath($Path).TrimEnd([char[]] '\/')
}

function Assert-Within([string] $Path, [string] $Parent) {
    $prefix = $Parent.TrimEnd([char[]] '\/') + [IO.Path]::DirectorySeparatorChar
    if (-not $Path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'An instance path resolves outside the selected instance directory.'
    }
}

function Assert-NoReparse([string] $Path) {
    # Also check ancestors, so a symlink/junction in any parent cannot redirect writes.
    $cursor = $Path
    while (-not [string]::IsNullOrEmpty($cursor)) {
        try {
            if (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Symlinks and junctions are not supported by the Analyzer updater.'
            }
        }
        catch [IO.FileNotFoundException] { }
        catch [IO.DirectoryNotFoundException] { }
        $parent = [IO.Path]::GetDirectoryName($cursor)
        if ($parent -eq $cursor) { break }
        $cursor = $parent
    }
}

function Read-SmallJson([string] $Path) {
    Assert-NoReparse $Path
    if (-not [IO.File]::Exists($Path)) { throw "Required JSON file is missing: $Path" }
    $info = [IO.FileInfo] $Path
    if ($info.Length -gt 65536) { throw 'Updater JSON files must be at most 64 KiB.' }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}

function Read-ModMetadata([string] $Path) {
    Assert-NoReparse $Path
    $info = [IO.FileInfo] $Path
    if ($info.Length -le 0 -or $info.Length -gt 134217728) { throw 'Mod JAR size must be between 1 byte and 128 MiB.' }
    $archive = $null
    try {
        $archive = [IO.Compression.ZipFile]::OpenRead($Path)
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq 'fabric.mod.json' })
        if ($entries.Count -eq 0) { return $null }
        if ($entries.Count -ne 1 -or $entries[0].Length -gt 65536) { throw 'JAR metadata is duplicated or larger than 64 KiB.' }
        $stream = $entries[0].Open()
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8, $true)
        try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
        finally { $reader.Dispose(); $stream.Dispose() }
    }
    finally { if ($null -ne $archive) { $archive.Dispose() } }
}

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-Artifact([string] $Path, $Manifest) {
    $artifactInfo = [IO.FileInfo] $Path
    if ($artifactInfo.Length -le 0 -or $artifactInfo.Length -gt 134217728) { throw 'Mod JAR size must be between 1 byte and 128 MiB.' }
    if ((Get-Sha256 $Path) -cne ([string] (Get-Property $Manifest 'sha256')).ToLowerInvariant()) {
        throw 'Artifact SHA-256 does not match the release manifest.'
    }
    $metadata = Read-ModMetadata $Path
    $depends = Get-Property $metadata 'depends'
    $schema = Get-Property $metadata 'schemaVersion'
    foreach ($property in @('id', 'version', 'environment')) {
        if ((Get-Property $metadata $property) -isnot [string]) { throw 'Artifact metadata must use scalar strings for id, version and environment.' }
    }
    foreach ($property in @('minecraft', 'fabricloader', 'java')) {
        if ((Get-Property $depends $property) -isnot [string]) { throw 'Artifact dependencies must use scalar version strings.' }
    }
    if (($schema -isnot [int] -and $schema -isnot [long]) -or $schema -ne 1 -or
        (Get-Property $metadata 'id') -cne 'analyzer' -or
        (Get-Property $metadata 'version') -cne (Get-Property $Manifest 'version') -or
        (Get-Property $metadata 'environment') -cne 'client' -or
        (Get-Property $depends 'minecraft') -cne '1.21.4' -or
        (Get-Property $depends 'fabricloader') -cne '>=0.16.10' -or
        (Get-Property $depends 'java') -cne '>=21') {
        throw 'Artifact metadata does not match Analyzer client / Minecraft 1.21.4 / Java 21 / Fabric Loader >=0.16.10.'
    }
}

function Find-InstalledAnalyzer([string] $ModsDir) {
    $result = @()
    if (-not [IO.Directory]::Exists($ModsDir)) { return $result }
    foreach ($file in @(Get-ChildItem -LiteralPath $ModsDir -File -Filter '*.jar' | Sort-Object FullName)) {
        Assert-NoReparse $file.FullName
        try { $metadata = Read-ModMetadata $file.FullName }
        catch {
            if ($file.Name -match '^analyzer([-.]|$)') { throw "An existing Analyzer-named JAR cannot be inspected: $($file.Name)" }
            continue
        }
        $id = Get-Property $metadata 'id'
        if ($id -is [string] -and $id -ceq 'analyzer') { $result += $file.FullName }
    }
    return $result
}

function New-Result([string] $Status, [string] $Message, [string] $Version = '', [string] $BackupDir = '') {
    return [ordered] @{ status = $Status; message = $Message; version = $Version; backupDir = $BackupDir }
}

function Retire-LegacyBaritone([string] $Instance, [string] $ModsDir, [string] $StateDir) {
    # This independent, one-file operation runs only after a verified Analyzer install commits.
    # A move failure does not roll back the already committed Analyzer transaction.
    $knownHash = 'bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05'
    $retired = @()
    $skipped = @()
    if (-not [IO.Directory]::Exists($ModsDir)) { return @{ retired = $retired; skipped = $skipped } }
    foreach ($file in @(Get-ChildItem -LiteralPath $ModsDir -File -Filter '*.jar' | Sort-Object FullName)) {
        $metadata = $null
        try {
            Assert-Within $file.FullName $Instance
            Assert-NoReparse $file.FullName
            $metadata = Read-ModMetadata $file.FullName
            $id = Get-Property $metadata 'id'
            if ($id -isnot [string] -or $id -cne 'baritone') { continue }
            $legacyVersion = Get-Property $metadata 'version'
            if ($legacyVersion -isnot [string] -or $legacyVersion -cne '1.13.1') {
                $skipped += @{ name = $file.Name; reason = 'Different Baritone version; only the previously bundled 1.13.1 is retired.' }
                continue
            }
            if ((Get-Sha256 $file.FullName) -cne $knownHash) {
                $skipped += @{ name = $file.Name; reason = 'Baritone bytes do not match the previously bundled official JAR; preserved unchanged.' }
                continue
            }
            if (-not (Test-OriginalJarName $file.Name)) { throw 'The original mod filename could not be safely preserved.' }
            $backupRoot = [IO.Path]::Combine($StateDir, 'retired-mods')
            $operationId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8) + '-' + $knownHash.Substring(0, 12)
            $backupDir = [IO.Path]::Combine($backupRoot, $operationId)
            $backup = [IO.Path]::Combine($backupDir, $file.Name)
            foreach ($path in @($backupRoot, $backupDir, $backup)) { Assert-Within $path $Instance; Assert-NoReparse $path }
            [IO.Directory]::CreateDirectory($backupDir) | Out-Null
            # Recheck immediately before the atomic same-instance move; no file is deleted or overwritten.
            if ((Get-Sha256 $file.FullName) -cne $knownHash) { throw 'The legacy JAR changed while the updater inspected it.' }
            [IO.File]::Move($file.FullName, $backup)
            $retired += @{ modId = 'baritone'; version = '1.13.1'; name = $file.Name; sha256 = $knownHash; backupPath = $backup }
        }
        catch {
            # Only identifiable legacy files are relevant; unrelated unreadable JARs remain untouched.
            if ($file.Name -match 'baritone' -or $null -ne $metadata -and (Get-Property $metadata 'id') -ceq 'baritone') {
                $skipped += @{ name = $file.Name; reason = 'Retirement skipped; the original file remains active: ' + $_.Exception.Message }
            }
        }
    }
    return @{ retired = $retired; skipped = $skipped }
}

function Add-RetirementReport($Result, $Report) {
    $Result['retiredMods'] = @($Report.retired)
    $Result['skippedRetirements'] = @($Report.skipped)
    if ($Report.retired.Count -gt 0) { $Result['message'] += ' The previously bundled Baritone was moved to a recoverable backup outside active mods.' }
    if ($Report.skipped.Count -gt 0) { $Result['message'] += ' Some legacy retirement candidates were preserved; inspect skippedRetirements.' }
    return $Result
}

function Complete-WithLegacyRetirement($Result, [string] $Instance, [string] $ModsDir, [string] $StateDir) {
    try { return (Add-RetirementReport $Result (Retire-LegacyBaritone $Instance $ModsDir $StateDir)) }
    catch {
        # Post-commit maintenance never enters the Analyzer transaction's rollback path.
        $Result['retiredMods'] = @()
        $Result['skippedRetirements'] = @(@{ name = ''; reason = 'Legacy retirement could not complete safely: ' + $_.Exception.Message })
        $Result['message'] += ' Analyzer remains verified; legacy retirement needs review and no candidate was deleted.'
        return $Result
    }
}

function Test-JarName($Name) {
    return ($Name -is [string] -and $Name -match '^[A-Za-z0-9][A-Za-z0-9._+-]{0,159}\.jar$' -and
        $Name -notmatch '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)')
}

function Test-OriginalJarName($Name) {
    # Installed JARs may have been renamed by the user or browser (spaces/Unicode).
    return ($Name -is [string] -and $Name.Length -le 255 -and $Name.Length -gt 4 -and
        [IO.Path]::GetFileName($Name) -ceq $Name -and $Name.EndsWith('.jar', [StringComparison]::OrdinalIgnoreCase) -and
        $Name -notmatch '[<>:"/\\|?*\x00-\x1f]' -and
        $Name -notmatch '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)')
}

function Assert-AnalyzerRecoveryFile([string] $Path) {
    $metadata = Read-ModMetadata $Path
    $id = Get-Property $metadata 'id'
    if ($id -isnot [string] -or $id -cne 'analyzer') { throw 'A recovery file is not an Analyzer mod; files were left untouched.' }
}

function Repair-PendingUpdate([string] $Instance, [switch] $LockHeld) {
    $stateDir = [IO.Path]::Combine($Instance, '.analyzer-updater')
    $journalPath = [IO.Path]::Combine($stateDir, 'pending.json')
    Assert-NoReparse $journalPath
    if (-not [IO.File]::Exists($journalPath)) { return 'none' }
    $lockPath = [IO.Path]::Combine($stateDir, 'update.lock')
    Assert-NoReparse $lockPath
    $lock = $null
    try {
        if (-not $LockHeld) {
            try { $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
            catch { return 'busy' }
        }
        if (-not [IO.File]::Exists($journalPath)) { return 'none' }
        $journal = Read-SmallJson $journalPath
        $schema = Get-Property $journal 'schemaVersion'
        $operationId = Get-Property $journal 'operationId'
        $minecraftRelative = Get-Property $journal 'minecraftDir'
        $targetName = Get-Property $journal 'targetName'
        $releaseHash = Get-Property $journal 'sha256'
        $originalsValue = Get-Property $journal 'originals'
        if (($schema -isnot [int] -and $schema -isnot [long]) -or $schema -ne 1 -or
            $operationId -isnot [string] -or $operationId -notmatch '^\d{8}T\d{9}Z-[a-f0-9]{8}$' -or
            $minecraftRelative -isnot [string] -or [IO.Path]::IsPathRooted($minecraftRelative) -or
            -not (Test-JarName $targetName) -or $releaseHash -isnot [string] -or $releaseHash -notmatch '^[a-f0-9]{64}$' -or
            $originalsValue -isnot [array] -or $originalsValue.Count -gt 64) {
            throw 'Pending updater journal is invalid; recovery backups were left untouched.'
        }
        $minecraftDir = Get-LocalPath $minecraftRelative $Instance
        Assert-Within $minecraftDir $Instance
        $modsDir = [IO.Path]::Combine($minecraftDir, 'mods')
        $backupDir = [IO.Path]::Combine($stateDir, 'backups', $operationId)
        $target = [IO.Path]::Combine($modsDir, $targetName)
        $staged = [IO.Path]::Combine($stateDir, 'staged-' + $operationId + '.jar')
        foreach ($path in @($modsDir, $backupDir, $target, $staged)) { Assert-Within $path $Instance; Assert-NoReparse $path }
        $names = @()
        $entries = @()
        foreach ($original in $originalsValue) {
            $name = Get-Property $original 'name'
            $hash = Get-Property $original 'sha256'
            if (-not (Test-OriginalJarName $name) -or $names -contains $name -or $hash -isnot [string] -or $hash -notmatch '^[a-f0-9]{64}$') {
                throw 'Pending journal contains invalid or duplicate original names; recovery backups were left untouched.'
            }
            $names += $name
            $originalPath = [IO.Path]::Combine($modsDir, $name)
            $backupPath = [IO.Path]::Combine($backupDir, $name)
            foreach ($path in @($originalPath, $backupPath)) { Assert-NoReparse $path }
            $originalExists = [IO.File]::Exists($originalPath)
            $backupExists = [IO.File]::Exists($backupPath)
            if ($backupExists) {
                if ((Get-Sha256 $backupPath) -cne $hash) { throw 'Recovery backup checksum is invalid; files were left untouched.' }
                Assert-AnalyzerRecoveryFile $backupPath
            }
            if (-not $originalExists -and -not $backupExists) { throw 'An interrupted update is missing an original and its backup; manual recovery is required.' }
            if ($originalExists) {
                Assert-AnalyzerRecoveryFile $originalPath
                $currentHash = Get-Sha256 $originalPath
                if ($currentHash -cne $hash -and -not ($backupExists -and $originalPath -eq $target -and $currentHash -ceq $releaseHash)) {
                    throw 'An original mod changed after an interrupted update; files were left untouched.'
                }
            }
            $entries += @{ Original = $originalPath; Backup = $backupPath; Hash = $hash; BackupExists = $backupExists }
        }
        $targetIsOriginal = $names -contains $targetName
        if (-not $targetIsOriginal -and [IO.File]::Exists($target) -and (Get-Sha256 $target) -cne $releaseHash) {
            throw 'The interrupted update target changed; recovery backups were left untouched.'
        }
        if ([IO.File]::Exists($target)) { Assert-AnalyzerRecoveryFile $target }
        if ([IO.File]::Exists($staged) -and (Get-Sha256 $staged) -cne $releaseHash) {
            throw 'The interrupted update staging file changed; recovery backups were left untouched.'
        }
        if ([IO.File]::Exists($staged)) { Assert-AnalyzerRecoveryFile $staged }
        # All paths and hashes are checked before recovery mutates any files.
        if (-not $targetIsOriginal -and [IO.File]::Exists($target)) { [IO.File]::Delete($target) }
        foreach ($entry in $entries) {
            if ($entry.BackupExists) {
                if ([IO.File]::Exists($entry.Original)) {
                    if ((Get-Sha256 $entry.Original) -cne $entry.Hash) { [IO.File]::Delete($entry.Original) }
                    else { continue }
                }
                [IO.File]::Move($entry.Backup, $entry.Original)
            }
        }
        if ([IO.File]::Exists($staged)) { [IO.File]::Delete($staged) }
        [IO.File]::Delete($journalPath)
        return 'recovered'
    }
    catch {
        $script:RecoveryFailure = $true
        $script:RecoveryLocation = $stateDir
        throw "An interrupted Analyzer update could not be safely recovered. Launch is stopped; inspect recovery files in $stateDir. $($_.Exception.Message)"
    }
    finally { if ($null -ne $lock) { $lock.Dispose() } }
}

function Invoke-AnalyzerUpdate {
    $instance = $InstanceDir
    if ([string]::IsNullOrWhiteSpace($instance)) {
        if (-not [string]::IsNullOrWhiteSpace($env:INST_DIR)) { $instance = $env:INST_DIR }
        else { $instance = Split-Path -Parent $PSScriptRoot }
    }
    $instance = Get-LocalPath $instance $PSScriptRoot
    Assert-NoReparse $instance
    if (-not [IO.Directory]::Exists($instance)) { throw 'The selected Prism instance directory does not exist.' }
    if (-not $CheckOnly) {
        if ((Repair-PendingUpdate $instance) -eq 'busy') { return (New-Result 'busy' 'Another update is running. Launch again when it finishes.') }
    }
    elseif ([IO.File]::Exists([IO.Path]::Combine($instance, '.analyzer-updater', 'pending.json'))) {
        return (New-Result 'recovery-pending' 'An interrupted update needs recovery on the next normal launch. CheckOnly leaves files unchanged.')
    }
    $configPath = [IO.Path]::Combine($instance, 'analyzer-updater.json')
    if (-not [IO.File]::Exists($configPath)) {
        return (New-Result 'disabled' 'No local release feed is configured. Existing mods are unchanged.')
    }
    $config = Read-SmallJson $configPath
    $configSchema = Get-Property $config 'schemaVersion'
    if ($null -ne $config.PSObject.Properties['schemaVersion'] -and
        (($configSchema -isnot [int] -and $configSchema -isnot [long]) -or $configSchema -ne 1)) {
        throw 'Updater configuration schemaVersion must be the number 1.'
    }
    if ($null -ne $config.PSObject.Properties['enabled']) {
        $enabled = Get-Property $config 'enabled'
        if ($enabled -isnot [bool]) { throw 'Updater configuration enabled must be true or false.' }
        if (-not $enabled) { return (New-Result 'disabled' 'Local updates are paused in analyzer-updater.json. Existing mods are unchanged.') }
    }
    $manifestPathValue = Get-Property $config 'manifestPath'
    if ($manifestPathValue -isnot [string]) { throw 'manifestPath must be a local path string.' }
    $manifestPath = Get-LocalPath $manifestPathValue $instance
    if (-not [IO.Path]::IsPathRooted($manifestPathValue)) { Assert-Within $manifestPath $instance }
    $manifest = Read-SmallJson $manifestPath
    $version = Get-Property $manifest 'version'
    $artifactName = Get-Property $manifest 'artifact'
    $checksum = Get-Property $manifest 'sha256'
    $schema = Get-Property $manifest 'schemaVersion'
    if (($schema -isnot [int] -and $schema -isnot [long]) -or $schema -ne 1 -or
        (Get-Property $manifest 'modId') -cne 'analyzer' -or
        (Get-Property $manifest 'minecraftVersion') -cne '1.21.4' -or
        $version -isnot [string] -or $version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?$' -or $version.Length -gt 80 -or
        $artifactName -isnot [string] -or $artifactName -notmatch '^[A-Za-z0-9][A-Za-z0-9._+-]{0,159}\.jar$' -or
        $artifactName -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)' -or
        $checksum -isnot [string] -or $checksum -notmatch '^[A-Fa-f0-9]{64}$') {
        throw 'Release manifest schema, mod ID, Minecraft version, version, artifact filename or SHA-256 is invalid.'
    }
    $retireLegacy = $false
    if ($null -ne $manifest.PSObject.Properties['retireLegacyBaritone']) {
        $retirementFlag = Get-Property $manifest 'retireLegacyBaritone'
        if ($retirementFlag -isnot [bool]) { throw 'Release retireLegacyBaritone must be a JSON boolean when present.' }
        $retireLegacy = $retirementFlag
    }
    $feedDir = [IO.Path]::GetDirectoryName($manifestPath)
    $artifact = Get-LocalPath $artifactName $feedDir
    Assert-Within $artifact $feedDir
    Assert-NoReparse $artifact
    if (-not [IO.File]::Exists($artifact)) { throw 'Release artifact is missing from the local feed.' }
    Assert-Artifact $artifact $manifest

    if (-not [string]::IsNullOrWhiteSpace($env:INST_MC_DIR)) {
        $minecraftDir = Get-LocalPath $env:INST_MC_DIR $instance
        Assert-Within $minecraftDir $instance
    }
    else {
        $candidates = @('minecraft', '.minecraft' | ForEach-Object {
            $candidate = [IO.Path]::Combine($instance, $_)
            if ([IO.Directory]::Exists($candidate)) { $candidate }
        })
        if ($candidates.Count -ne 1) { throw 'Expected one minecraft or .minecraft folder inside the instance. Set INST_MC_DIR if the location is ambiguous.' }
        $minecraftDir = $candidates[0]
    }
    Assert-NoReparse $minecraftDir
    if (-not [IO.Directory]::Exists($minecraftDir)) { throw 'The instance Minecraft directory does not exist.' }
    $modsDir = [IO.Path]::Combine($minecraftDir, 'mods')
    $stateDir = [IO.Path]::Combine($instance, '.analyzer-updater')
    $target = [IO.Path]::Combine($modsDir, $artifactName)
    foreach ($path in @($modsDir, $stateDir, $target)) { Assert-Within $path $instance; Assert-NoReparse $path }
    if ([IO.Directory]::Exists($target)) { throw 'The target JAR path is occupied by a directory.' }

    $lock = $null
    $staged = $null
    $moved = @()
    $newInstalled = $false
    $backupDir = ''
    $journalOwned = $false
    $journalPath = [IO.Path]::Combine($stateDir, 'pending.json')
    try {
        if (-not $CheckOnly) {
            [IO.Directory]::CreateDirectory($stateDir) | Out-Null
            $lockPath = [IO.Path]::Combine($stateDir, 'update.lock')
            Assert-NoReparse $lockPath
            try { $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
            catch { return (New-Result 'busy' 'Another update is running. Launch again when it finishes.' $version) }
            # Another updater may have stopped after our initial recovery check.
            $null = Repair-PendingUpdate $instance -LockHeld
        }
        $installed = @(Find-InstalledAnalyzer $modsDir)
        if ($installed.Count -gt 64) { throw 'More than 64 Analyzer JARs are installed; resolve duplicates before updating.' }
        if ([IO.File]::Exists($target) -and $installed -notcontains $target) {
            throw 'The target filename belongs to another mod; refusing to overwrite it.'
        }
        if ($installed.Count -eq 1 -and (Get-Sha256 $installed[0]) -ceq $checksum.ToLowerInvariant()) {
            $result = New-Result 'up-to-date' 'The installed Analyzer already matches the local release.' $version
            if ($retireLegacy -and -not $CheckOnly) { return (Complete-WithLegacyRetirement $result $instance $modsDir $stateDir) }
            return $result
        }
        if ($CheckOnly) { return (New-Result 'update-available' 'The verified local release is ready to install.' $version) }

        [IO.Directory]::CreateDirectory($modsDir) | Out-Null
        $operationId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $staged = [IO.Path]::Combine($stateDir, 'staged-' + $operationId + '.jar')
        Assert-NoReparse $staged
        [IO.File]::Copy($artifact, $staged, $false)
        # Verify the copy too: a build/publisher changing the feed cannot install a partial JAR.
        Assert-Artifact $staged $manifest
        $originalRecords = @($installed | ForEach-Object { @{ name = [IO.Path]::GetFileName($_); sha256 = Get-Sha256 $_ } })
        $minecraftRelative = $minecraftDir.Substring($instance.Length).TrimStart([char[]] '\/')
        $journal = @{ schemaVersion = 1; operationId = $operationId; minecraftDir = $minecraftRelative;
            targetName = $artifactName; sha256 = $checksum.ToLowerInvariant(); originals = $originalRecords }
        $journalTemp = [IO.Path]::Combine($stateDir, 'pending-' + $operationId + '.tmp')
        $journalBytes = (New-Object Text.UTF8Encoding($false)).GetBytes(($journal | ConvertTo-Json -Depth 8))
        $journalStream = [IO.File]::Open($journalTemp, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $journalStream.Write($journalBytes, 0, $journalBytes.Length); $journalStream.Flush($true) }
        finally { $journalStream.Dispose() }
        [IO.File]::Move($journalTemp, $journalPath)
        $journalOwned = $true
        if ($installed.Count -gt 0) {
            $backupRoot = [IO.Path]::Combine($stateDir, 'backups')
            Assert-NoReparse $backupRoot
            $backupDir = [IO.Path]::Combine($backupRoot, $operationId)
            Assert-Within $backupDir $instance
            [IO.Directory]::CreateDirectory($backupDir) | Out-Null
            foreach ($oldPath in $installed) {
                $backupPath = [IO.Path]::Combine($backupDir, [IO.Path]::GetFileName($oldPath))
                [IO.File]::Move($oldPath, $backupPath)
                $moved += @{ Original = $oldPath; Backup = $backupPath }
            }
        }
        [IO.File]::Move($staged, $target)
        $staged = $null
        $newInstalled = $true
        [IO.File]::Delete($journalPath)
        $journalOwned = $false
        $result = New-Result 'installed' 'Analyzer was installed from the verified local release feed.' $version $backupDir
        if ($retireLegacy) { return (Complete-WithLegacyRetirement $result $instance $modsDir $stateDir) }
        return $result
    }
    catch {
        $failure = $_.Exception.Message
        $rollbackErrors = @()
        if ($newInstalled -and [IO.File]::Exists($target)) {
            try { [IO.File]::Delete($target) } catch { $rollbackErrors += $_.Exception.Message }
        }
        for ($index = $moved.Count - 1; $index -ge 0; $index--) {
            try { [IO.File]::Move($moved[$index].Backup, $moved[$index].Original) }
            catch { $rollbackErrors += $_.Exception.Message }
        }
        if ($rollbackErrors.Count -gt 0) {
            $script:RecoveryFailure = $true
            $script:RecoveryLocation = $stateDir
            throw "Update failed: $failure Recovery files remain in $backupDir. Rollback errors: $($rollbackErrors -join '; ')"
        }
        if ($journalOwned -and [IO.File]::Exists($journalPath)) { [IO.File]::Delete($journalPath) }
        throw "Update failed; original mods were preserved: $failure"
    }
    finally {
        if ($null -ne $staged -and [IO.File]::Exists($staged)) {
            try { [IO.File]::Delete($staged) } catch { Write-Warning 'A staged file could not be removed; it is outside the mods directory.' }
        }
        if ($null -ne $lock) { $lock.Dispose() }
    }
}

try {
    $result = Invoke-AnalyzerUpdate
    $result | ConvertTo-Json -Compress
    # Never start a second game while the first updater is replacing active JARs.
    if ($result.status -eq 'busy') { exit 1 }
    exit 0
}
catch {
    if ($script:RecoveryFailure) {
        (New-Result 'recovery-error' $_.Exception.Message '' $script:RecoveryLocation) | ConvertTo-Json -Compress
        exit 1
    }
    (New-Result 'error' $_.Exception.Message) | ConvertTo-Json -Compress
    # A local feed problem must not block Prism from launching the existing client.
    if ($Strict) { exit 1 }
    exit 0
}
