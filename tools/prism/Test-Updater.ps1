# Self-contained updater integration tests; no installed Prism instances are touched.
[CmdletBinding()]
param(
    [string] $UpdaterPath,
    [string] $WorkDir,
    [string] $PowerShellPath,
    [string] $LegacyBaritonePath
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
# Windows PowerShell initializes PSScriptRoot after evaluating parameter defaults.
if ([string]::IsNullOrWhiteSpace($UpdaterPath)) { $UpdaterPath = Join-Path $PSScriptRoot 'Update-Analyzer.ps1' }
if ([string]::IsNullOrWhiteSpace($WorkDir)) { $WorkDir = Join-Path $PSScriptRoot '../../../../work/updater-tests' }
if ([string]::IsNullOrWhiteSpace($PowerShellPath)) { $PowerShellPath = Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe' }
if ([string]::IsNullOrWhiteSpace($LegacyBaritonePath)) { $LegacyBaritonePath = Join-Path $PSScriptRoot '../../third-party/baritone/baritone-api-fabric-1.13.1.jar' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$testDir = [IO.Path]::Combine([IO.Path]::GetFullPath($WorkDir), [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($testDir) | Out-Null
$checks = 0
$savedInstanceEnv = $env:INST_DIR
$savedMinecraftEnv = $env:INST_MC_DIR
$env:INST_DIR = $null
$env:INST_MC_DIR = $null

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
    $script:checks++
}

function Write-Json([string] $Path, $Object) {
    [IO.File]::WriteAllText($Path, ($Object | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
}

function New-Mod([string] $Path, [string] $Version, [string] $Id = 'analyzer', [string] $Minecraft = '1.21.4', [string] $Payload = 'test', [hashtable] $Overrides = @{}) {
    $metadata = @{ schemaVersion = 1; id = $Id; version = $Version; environment = 'client';
        depends = @{ minecraft = $Minecraft; fabricloader = '>=0.16.10'; java = '>=21' } }
    foreach ($key in $Overrides.Keys) { $metadata[$key] = $Overrides[$key] }
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew)
    $archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($pair in @(@('fabric.mod.json', ($metadata | ConvertTo-Json -Depth 8)), @('payload.txt', $Payload))) {
            $entry = $archive.CreateEntry($pair[0])
            $writer = New-Object IO.StreamWriter($entry.Open(), (New-Object Text.UTF8Encoding($false)))
            try { $writer.Write($pair[1]) } finally { $writer.Dispose() }
        }
    }
    finally { $archive.Dispose(); $stream.Dispose() }
}

function New-Fixture([string] $Name) {
    $root = [IO.Path]::Combine($testDir, $Name)
    $instance = [IO.Path]::Combine($root, 'instance')
    $mods = [IO.Path]::Combine($instance, 'minecraft', 'mods')
    $feed = [IO.Path]::Combine($root, 'feed')
    [IO.Directory]::CreateDirectory($mods) | Out-Null
    [IO.Directory]::CreateDirectory($feed) | Out-Null
    $manifest = [IO.Path]::Combine($feed, 'release.json')
    Write-Json ([IO.Path]::Combine($instance, 'analyzer-updater.json')) @{ manifestPath = $manifest }
    return @{ Instance = $instance; Mods = $mods; Feed = $feed; Manifest = $manifest }
}

function Set-Release($Fixture, [string] $Version = '0.1.1', [string] $Id = 'analyzer', [string] $Minecraft = '1.21.4', [string] $Payload = 'new build', [hashtable] $Overrides = @{}) {
    $artifactName = 'analyzer-' + $Version + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8) + '.jar'
    $artifact = [IO.Path]::Combine($Fixture.Feed, $artifactName)
    New-Mod $artifact $Version $Id $Minecraft $Payload $Overrides
    $manifest = @{ schemaVersion = 1; modId = 'analyzer'; minecraftVersion = '1.21.4'; version = $Version;
        artifact = $artifactName; sha256 = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant() }
    Write-Json $Fixture.Manifest $manifest
    return $manifest
}

function Invoke-Updater($Fixture, [switch] $CheckOnly, [switch] $FailSoft, [switch] $UseEnvironment) {
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $UpdaterPath)
    if (-not $UseEnvironment) { $arguments += @('-InstanceDir', $Fixture.Instance) }
    else { $env:INST_DIR = $Fixture.Instance }
    if ($CheckOnly) { $arguments += '-CheckOnly' }
    if (-not $FailSoft) { $arguments += '-Strict' }
    try {
        $lines = @(& $PowerShellPath @arguments)
        $code = $LASTEXITCODE
        $json = @($lines | Where-Object { $_ -is [string] -and $_.TrimStart().StartsWith('{') })
        if ($json.Count -ne 1) { throw "Updater did not return one JSON result: $($lines -join ' ')" }
        return @{ ExitCode = $code; Result = ($json[0] | ConvertFrom-Json) }
    }
    finally { if ($UseEnvironment) { $env:INST_DIR = $null } }
}

function Snapshot-Instance($Fixture) {
    return (@(Get-ChildItem -LiteralPath $Fixture.Instance -File -Recurse | Sort-Object FullName | ForEach-Object {
        $_.FullName.Substring($Fixture.Instance.Length) + '=' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }) -join '|')
}

try {
    $fixture = New-Fixture 'install-and-replace'
    $release = Set-Release $fixture
    $check = Invoke-Updater $fixture -CheckOnly
    Assert-True ($check.ExitCode -eq 0 -and $check.Result.status -eq 'update-available') 'check validates an install without writing'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $fixture.Instance '.analyzer-updater'))) 'check creates no updater state'
    Assert-True (@(Get-ChildItem -LiteralPath $fixture.Mods -File).Count -eq 0) 'check leaves mods empty'
    $install = Invoke-Updater $fixture -UseEnvironment
    $firstJar = Join-Path $fixture.Mods $release.artifact
    Assert-True ($install.Result.status -eq 'installed' -and $install.ExitCode -eq 0) 'initial install using Prism INST_DIR'
    Assert-True ((Get-FileHash -LiteralPath $firstJar -Algorithm SHA256).Hash.ToLowerInvariant() -eq $release.sha256) 'installed bytes match manifest'
    $unchanged = Invoke-Updater $fixture
    Assert-True ($unchanged.Result.status -eq 'up-to-date') 'unchanged release does not reinstall'

    $worlds = Join-Path $fixture.Instance 'minecraft/saves/KeepWorld'
    $config = Join-Path $fixture.Instance 'minecraft/config/analyzer'
    [IO.Directory]::CreateDirectory($worlds) | Out-Null
    [IO.Directory]::CreateDirectory($config) | Out-Null
    [IO.File]::WriteAllText((Join-Path $worlds 'level.dat'), 'world sentinel')
    [IO.File]::WriteAllText((Join-Path $config 'config.json'), '{"keep":true}')
    $otherJar = Join-Path $fixture.Mods 'other-mod.jar'
    New-Mod $otherJar '1.0.0' 'othermod'
    $otherHash = (Get-FileHash -LiteralPath $otherJar).Hash
    $secondRelease = Set-Release $fixture '0.2.0'
    $replace = Invoke-Updater $fixture
    Assert-True ($replace.Result.status -eq 'installed') 'version replacement installs'
    Assert-True (-not (Test-Path -LiteralPath $firstJar)) 'old Analyzer is removed from active mods'
    Assert-True (Test-Path -LiteralPath (Join-Path $replace.Result.backupDir $release.artifact)) 'old JAR is backed up outside mods'
    Assert-True ((Get-FileHash -LiteralPath $otherJar).Hash -eq $otherHash) 'other mods are preserved byte for byte'
    Assert-True ([IO.File]::ReadAllText((Join-Path $worlds 'level.dat')) -eq 'world sentinel') 'worlds are preserved'
    Assert-True ([IO.File]::ReadAllText((Join-Path $config 'config.json')) -eq '{"keep":true}') 'Analyzer config is preserved'
    $thirdRelease = Set-Release $fixture '0.2.0' 'analyzer' '1.21.4' 'changed bytes, same version'
    $sameVersion = Invoke-Updater $fixture
    Assert-True ($sameVersion.Result.status -eq 'installed') 'same-version development builds update by hash'
    Assert-True (Test-Path -LiteralPath (Join-Path $sameVersion.Result.backupDir $secondRelease.artifact)) 'same-version predecessor is backed up'

    $tampered = Set-Release $fixture '0.3.0'
    [IO.File]::AppendAllText((Join-Path $fixture.Feed $tampered.artifact), 'tampered')
    $tamperResult = Invoke-Updater $fixture -FailSoft
    Assert-True ($tamperResult.ExitCode -eq 0 -and $tamperResult.Result.status -eq 'error') 'tampered release fails softly for Prism'
    Assert-True (Test-Path -LiteralPath (Join-Path $fixture.Mods $thirdRelease.artifact)) 'tamper preserves installed Analyzer'
    foreach ($case in @(@('metadata-id', 'unrelated', '1.21.4'), @('metadata-version', 'analyzer', '1.21.3'))) {
        $bad = New-Fixture $case[0]
        $null = Set-Release $bad '0.1.1' $case[1] $case[2]
        $badResult = Invoke-Updater $bad
        Assert-True ($badResult.ExitCode -eq 1 -and $badResult.Result.status -eq 'error') $case[0]
        Assert-True (@(Get-ChildItem -LiteralPath $bad.Mods -File).Count -eq 0) 'invalid metadata leaves mods empty'
    }
    $versionMismatch = New-Fixture 'embedded-version-mismatch'
    $versionManifest = Set-Release $versionMismatch
    $versionManifest.version = '0.2.0'
    Write-Json $versionMismatch.Manifest $versionManifest
    Assert-True ((Invoke-Updater $versionMismatch).Result.status -eq 'error') 'embedded mod version must match release version'
    $stringSchema = New-Fixture 'string-schema'
    $schemaManifest = Set-Release $stringSchema
    $schemaManifest.schemaVersion = '1'
    Write-Json $stringSchema.Manifest $schemaManifest
    Assert-True ((Invoke-Updater $stringSchema).Result.status -eq 'error') 'schema version must be a number'
    foreach ($property in @('id', 'version', 'environment', 'schemaVersion', 'depends')) {
        $badScalar = New-Fixture ('array-metadata-' + $property)
        $overrides = @{}
        switch ($property) {
            'id' { $overrides.id = @('analyzer') }
            'version' { $overrides.version = @('0.1.1') }
            'environment' { $overrides.environment = @('client') }
            'schemaVersion' { $overrides.schemaVersion = @(1) }
            'depends' { $overrides.depends = @{ minecraft = @('1.21.4'); java = '>=21'; fabricloader = '>=0.16.10' } }
        }
        $null = Set-Release $badScalar -Overrides $overrides
        Assert-True ((Invoke-Updater $badScalar).Result.status -eq 'error') ('Fabric metadata requires scalar ' + $property)
    }

    $traversal = New-Fixture 'artifact-traversal'
    $badManifest = Set-Release $traversal
    $badManifest.artifact = '../escape.jar'
    Write-Json $traversal.Manifest $badManifest
    Assert-True ((Invoke-Updater $traversal).Result.status -eq 'error') 'artifact path traversal is rejected'
    $configEscape = New-Fixture 'config-traversal'
    $null = Set-Release $configEscape
    Write-Json (Join-Path $configEscape.Instance 'analyzer-updater.json') @{ manifestPath = '../feed/release.json' }
    Assert-True ((Invoke-Updater $configEscape).Result.status -eq 'error') 'relative manifest escape is rejected'

    $conflict = New-Fixture 'other-mod-conflict'
    $conflictRelease = Set-Release $conflict
    New-Mod (Join-Path $conflict.Mods $conflictRelease.artifact) '1.0.0' 'unrelated'
    $conflictResult = Invoke-Updater $conflict
    Assert-True ($conflictResult.Result.status -eq 'error') 'another mod with target filename is never overwritten'

    $locked = New-Fixture 'updater-lock'
    $null = Set-Release $locked
    $lockDir = Join-Path $locked.Instance '.analyzer-updater'
    [IO.Directory]::CreateDirectory($lockDir) | Out-Null
    $lock = [IO.File]::Open((Join-Path $lockDir 'update.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    try { $busyResult = Invoke-Updater $locked -FailSoft }
    finally { $lock.Dispose() }
    Assert-True ($busyResult.Result.status -eq 'busy' -and $busyResult.ExitCode -eq 1) 'concurrent updater prevents launch until the active transaction finishes'

    $rollback = New-Fixture 'rollback'
    $null = Set-Release $rollback '0.4.0'
    $oldA = Join-Path $rollback.Mods 'analyzer-a.jar'
    $oldB = Join-Path $rollback.Mods 'analyzer-b.jar'
    New-Mod $oldA '0.1.0'
    New-Mod $oldB '0.2.0'
    $oldAHash = (Get-FileHash -LiteralPath $oldA).Hash
    $oldBHash = (Get-FileHash -LiteralPath $oldB).Hash
    # Reads are allowed, but Windows will refuse to move B after A has been backed up.
    $lockedJar = [IO.File]::Open($oldB, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try { $rollbackResult = Invoke-Updater $rollback }
    finally { $lockedJar.Dispose() }
    Assert-True ($rollbackResult.Result.status -eq 'error') 'a locked old JAR causes controlled rollback'
    Assert-True ((Get-FileHash -LiteralPath $oldA).Hash -eq $oldAHash -and (Get-FileHash -LiteralPath $oldB).Hash -eq $oldBHash) 'both original JARs survive partial-move rollback'
    Assert-True (@(Get-ChildItem -LiteralPath $rollback.Mods -File).Count -eq 2) 'failed update leaves no additional active Analyzer'
    Assert-True (@(Get-ChildItem -LiteralPath (Join-Path $rollback.Instance '.analyzer-updater') -Filter 'staged-*.jar').Count -eq 0) 'failed update cleans its staging file'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $rollback.Instance '.analyzer-updater/pending.json'))) 'caught rollback clears its journal'

    foreach ($crashPoint in @('old-moved', 'new-installed', 'same-target-installed', 'renamed-old-moved')) {
        $crashed = New-Fixture ('interrupted-' + $crashPoint)
        $crashRelease = Set-Release $crashed '0.5.0'
        $oldName = 'analyzer-old.jar'
        if ($crashPoint -eq 'same-target-installed') { $oldName = $crashRelease.artifact }
        if ($crashPoint -eq 'renamed-old-moved') { $oldName = 'My Analyzer ' + [char] 0x65E7 + ' (1).jar' }
        $oldPath = Join-Path $crashed.Mods $oldName
        New-Mod $oldPath '0.1.0'
        $oldHash = (Get-FileHash -LiteralPath $oldPath).Hash.ToLowerInvariant()
        $operationId = '20261008T120000000Z-1234abcd'
        $crashState = Join-Path $crashed.Instance '.analyzer-updater'
        $crashBackup = Join-Path $crashState ('backups/' + $operationId)
        [IO.Directory]::CreateDirectory($crashBackup) | Out-Null
        Move-Item -LiteralPath $oldPath -Destination (Join-Path $crashBackup $oldName)
        $copyPath = Join-Path $crashed.Mods $crashRelease.artifact
        if ($crashPoint -in @('old-moved', 'renamed-old-moved')) { $copyPath = Join-Path $crashState ('staged-' + $operationId + '.jar') }
        Copy-Item -LiteralPath (Join-Path $crashed.Feed $crashRelease.artifact) -Destination $copyPath
        Write-Json (Join-Path $crashState 'pending.json') @{ schemaVersion = 1; operationId = $operationId; minecraftDir = 'minecraft';
            targetName = $crashRelease.artifact; sha256 = $crashRelease.sha256; originals = @(@{ name = $oldName; sha256 = $oldHash }) }
        # Recovery must work before touching a now-unavailable release feed.
        Move-Item -LiteralPath $crashed.Manifest -Destination ($crashed.Manifest + '-offline')
        $beforeRecovery = Invoke-Updater $crashed -CheckOnly
        Assert-True ($beforeRecovery.Result.status -eq 'recovery-pending') 'check-only reports interruption without changing files'
        $crashResult = Invoke-Updater $crashed -FailSoft
        Assert-True ($crashResult.Result.status -eq 'error' -and $crashResult.ExitCode -eq 0) 'offline feed remains fail-soft after recovery'
        Assert-True ((Get-FileHash -LiteralPath $oldPath).Hash.ToLowerInvariant() -eq $oldHash) ('previous mod is recovered at crash point ' + $crashPoint)
        Assert-True (@(Get-ChildItem -LiteralPath $crashed.Mods -File).Count -eq 1) 'interruption recovery leaves one original active mod'
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $crashState 'pending.json'))) 'completed interruption recovery clears journal'
        Assert-True (@(Get-ChildItem -LiteralPath $crashState -Filter 'staged-*.jar').Count -eq 0) 'interruption recovery cleans known staging file'
    }
    $damagedRecovery = New-Fixture 'damaged-recovery'
    $damagedRelease = Set-Release $damagedRecovery '0.6.0'
    $damagedState = Join-Path $damagedRecovery.Instance '.analyzer-updater'
    $damagedOperation = '20261008T120000000Z-abcdef12'
    $damagedBackup = Join-Path $damagedState ('backups/' + $damagedOperation)
    [IO.Directory]::CreateDirectory($damagedBackup) | Out-Null
    $backupOriginal = Join-Path $damagedBackup 'analyzer-original.jar'
    New-Mod $backupOriginal '0.1.0'
    $expectedOriginalHash = (Get-FileHash -LiteralPath $backupOriginal).Hash.ToLowerInvariant()
    [IO.File]::AppendAllText($backupOriginal, 'corrupted recovery evidence')
    $corruptEvidenceHash = (Get-FileHash -LiteralPath $backupOriginal).Hash
    $damagedTarget = Join-Path $damagedRecovery.Mods $damagedRelease.artifact
    Copy-Item -LiteralPath (Join-Path $damagedRecovery.Feed $damagedRelease.artifact) -Destination $damagedTarget
    Write-Json (Join-Path $damagedState 'pending.json') @{ schemaVersion = 1; operationId = $damagedOperation; minecraftDir = 'minecraft';
        targetName = $damagedRelease.artifact; sha256 = $damagedRelease.sha256; originals = @(@{ name = 'analyzer-original.jar'; sha256 = $expectedOriginalHash }) }
    $damagedResult = Invoke-Updater $damagedRecovery -FailSoft
    Assert-True ($damagedResult.ExitCode -eq 1 -and $damagedResult.Result.status -eq 'recovery-error') 'unsafe interrupted recovery stops launch even without Strict'
    Assert-True ($damagedResult.Result.backupDir -eq $damagedState) 'unsafe recovery identifies evidence directory'
    Assert-True ((Get-FileHash -LiteralPath $backupOriginal).Hash -eq $corruptEvidenceHash) 'unsafe recovery preserves damaged backup evidence'
    Assert-True ((Get-FileHash -LiteralPath $damagedTarget).Hash.ToLowerInvariant() -eq $damagedRelease.sha256) 'unsafe recovery leaves target bytes unchanged'
    Assert-True (Test-Path -LiteralPath (Join-Path $damagedState 'pending.json')) 'unsafe recovery keeps its journal'

    $dotMinecraft = New-Fixture 'dot-minecraft'
    Move-Item -LiteralPath (Join-Path $dotMinecraft.Instance 'minecraft') -Destination (Join-Path $dotMinecraft.Instance '.minecraft')
    $null = Set-Release $dotMinecraft
    Assert-True ((Invoke-Updater $dotMinecraft).Result.status -eq 'installed') 'legacy .minecraft instance directory works'
    $outsideMinecraft = New-Fixture 'outside-minecraft'
    $null = Set-Release $outsideMinecraft
    $env:INST_MC_DIR = $outsideMinecraft.Feed
    try { Assert-True ((Invoke-Updater $outsideMinecraft).Result.status -eq 'error') 'INST_MC_DIR outside instance is rejected' }
    finally { $env:INST_MC_DIR = $null }

    $junction = New-Fixture 'junction'
    $null = Set-Release $junction
    $outside = Join-Path $testDir 'junction-external'
    [IO.Directory]::CreateDirectory($outside) | Out-Null
    # The empty mods folder is moved, not deleted, before a local NTFS junction is created.
    Move-Item -LiteralPath $junction.Mods -Destination ($junction.Mods + '-original')
    New-Item -ItemType Junction -Path $junction.Mods -Target $outside | Out-Null
    Assert-True ((Invoke-Updater $junction).Result.status -eq 'error') 'mods junction cannot redirect writes outside instance'
    Assert-True (@(Get-ChildItem -LiteralPath $outside -File).Count -eq 0) 'external junction target stays untouched'
    $disabled = New-Fixture 'disabled'
    Move-Item -LiteralPath (Join-Path $disabled.Instance 'analyzer-updater.json') -Destination (Join-Path $disabled.Instance 'config-disabled.json')
    Assert-True ((Invoke-Updater $disabled).Result.status -eq 'disabled') 'no feed gracefully disables updates'
    $paused = New-Fixture 'paused'
    Write-Json (Join-Path $paused.Instance 'analyzer-updater.json') @{ schemaVersion = 1; enabled = $false; manifestPath = 'missing-release.json' }
    Assert-True ((Invoke-Updater $paused).Result.status -eq 'disabled') 'enabled false pauses updates before reading the feed'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $paused.Instance '.analyzer-updater'))) 'paused updates create no state or lock'
    Write-Json (Join-Path $paused.Instance 'analyzer-updater.json') @{ schemaVersion = 1; enabled = 'false'; manifestPath = 'missing-release.json' }
    Assert-True ((Invoke-Updater $paused).Result.status -eq 'error') 'enabled must be a JSON boolean'
    Write-Json (Join-Path $paused.Instance 'analyzer-updater.json') @{ schemaVersion = '1'; enabled = $false; manifestPath = 'missing-release.json' }
    Assert-True ((Invoke-Updater $paused).Result.status -eq 'error') 'configuration schema version must be numeric'

    # The actual official bytes prove the retirement acceptance path; unknown synthetic JARs remain protected.
    $legacyHash = 'bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05'
    if (Test-Path -LiteralPath $LegacyBaritonePath -PathType Leaf) {
        Assert-True ((Get-FileHash -LiteralPath $LegacyBaritonePath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $legacyHash) 'archived official retirement fixture has the independently pinned SHA-256'

        $keepLegacy = New-Fixture 'legacy-retirement-missing-or-disabled'
        $keepRelease = Set-Release $keepLegacy '0.10.0'
        $keepPath = Join-Path $keepLegacy.Mods 'my-old-bot.jar'
        Copy-Item -LiteralPath $LegacyBaritonePath -Destination $keepPath
        $keepResult = Invoke-Updater $keepLegacy
        Assert-True ($keepResult.Result.status -eq 'installed' -and (Test-Path -LiteralPath $keepPath)) 'older manifests without retirement flag preserve the exact legacy JAR'
        $keepRelease.retireLegacyBaritone = $false; Write-Json $keepLegacy.Manifest $keepRelease
        $keepResult = Invoke-Updater $keepLegacy
        Assert-True ($keepResult.Result.status -eq 'up-to-date' -and (Test-Path -LiteralPath $keepPath)) 'explicit retirement false also preserves the legacy dependency'
        $keepRelease.retireLegacyBaritone = 'true'; Write-Json $keepLegacy.Manifest $keepRelease
        $badRetirement = Invoke-Updater $keepLegacy
        Assert-True ($badRetirement.ExitCode -eq 1 -and $badRetirement.Result.status -eq 'error') 'retirement directive requires a scalar JSON boolean'
        Assert-True ((Get-FileHash -LiteralPath $keepPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $legacyHash) 'invalid directive changes no legacy bytes'

        $retirement = New-Fixture 'current-analyzer-retirement'
        $retirementRelease = Set-Release $retirement '0.10.0'
        $retirementRelease.retireLegacyBaritone = $true; Write-Json $retirement.Manifest $retirementRelease
        $currentPath = Join-Path $retirement.Mods $retirementRelease.artifact
        Copy-Item -LiteralPath (Join-Path $retirement.Feed $retirementRelease.artifact) -Destination $currentPath
        $legacyPath = Join-Path $retirement.Mods 'Renamed Official Bot (1).jar'
        Copy-Item -LiteralPath $LegacyBaritonePath -Destination $legacyPath
        $unknownPath = Join-Path $retirement.Mods 'baritone-custom.jar'; New-Mod $unknownPath '1.13.1' 'baritone' '1.21.4' 'unrecognized custom build'
        $differentPath = Join-Path $retirement.Mods 'baritone-other-version.jar'; New-Mod $differentPath '1.13.2' 'baritone'
        $unrelatedPath = Join-Path $retirement.Mods 'unrelated-mod.jar'; New-Mod $unrelatedPath '1.0.0' 'othermod'
        $unknownHash = (Get-FileHash -LiteralPath $unknownPath).Hash
        $differentHash = (Get-FileHash -LiteralPath $differentPath).Hash
        $unrelatedHash = (Get-FileHash -LiteralPath $unrelatedPath).Hash
        $beforeRetireCheck = Snapshot-Instance $retirement
        $retireCheck = Invoke-Updater $retirement -CheckOnly
        Assert-True ($retireCheck.Result.status -eq 'up-to-date') 'check-only recognizes the current Analyzer with retirement enabled'
        Assert-True ((Snapshot-Instance $retirement) -ceq $beforeRetireCheck) 'check-only never writes or retires any file, even for a same-hash current release'
        $retireResult = Invoke-Updater $retirement
        Assert-True ($retireResult.ExitCode -eq 0 -and $retireResult.Result.status -eq 'up-to-date') 'retirement succeeds without reinstalling a matching Analyzer'
        Assert-True (@($retireResult.Result.retiredMods).Count -eq 1 -and -not (Test-Path -LiteralPath $legacyPath)) 'only the exact recognized legacy mod is removed from active mods'
        $retiredPath = $retireResult.Result.retiredMods[0].backupPath
        Assert-True ((Test-Path -LiteralPath $retiredPath) -and (Get-FileHash -LiteralPath $retiredPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $legacyHash) 'atomic retirement preserves exact bytes in a recoverable backup'
        Assert-True ($retiredPath.StartsWith((Join-Path $retirement.Instance '.analyzer-updater/retired-mods'),[StringComparison]::OrdinalIgnoreCase)) 'retired backup stays inside this instance outside active mods'
        Assert-True ((Get-FileHash -LiteralPath $currentPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $retirementRelease.sha256) 'retirement leaves the verified Analyzer unchanged'
        Assert-True ((Get-FileHash -LiteralPath $unknownPath).Hash -ceq $unknownHash -and (Get-FileHash -LiteralPath $differentPath).Hash -ceq $differentHash) 'unknown hashes and other Baritone versions are preserved'
        Assert-True ((Get-FileHash -LiteralPath $unrelatedPath).Hash -ceq $unrelatedHash) 'unrelated mods are preserved during legacy retirement'
        Assert-True (@($retireResult.Result.skippedRetirements).Count -eq 2) 'unknown or different legacy candidates have explicit skip reasons'
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $retirement.Instance '.analyzer-updater/pending.json'))) 'legacy maintenance never creates or changes the Analyzer recovery journal'
        $repeatRetire = Invoke-Updater $retirement
        Assert-True (@($repeatRetire.Result.retiredMods).Count -eq 0 -and (Test-Path -LiteralPath $retiredPath)) 'repeat current-release checks do not duplicate or consume backups'

        $installRetirement = New-Fixture 'retirement-after-analyzer-commit'
        $installRetirementRelease = Set-Release $installRetirement '0.10.0'
        $installRetirementRelease.retireLegacyBaritone = $true; Write-Json $installRetirement.Manifest $installRetirementRelease
        $oldAnalyzer = Join-Path $installRetirement.Mods 'analyzer-0.9.0.jar'; New-Mod $oldAnalyzer '0.9.0'
        $oldAnalyzerHash = (Get-FileHash -LiteralPath $oldAnalyzer).Hash
        $commitLegacy = Join-Path $installRetirement.Mods 'legacy.jar'; Copy-Item -LiteralPath $LegacyBaritonePath -Destination $commitLegacy
        $committed = Invoke-Updater $installRetirement
        Assert-True ($committed.Result.status -eq 'installed' -and @($committed.Result.retiredMods).Count -eq 1) 'successful Analyzer replacement commits before optional legacy retirement'
        Assert-True ((Get-FileHash -LiteralPath (Join-Path $committed.Result.backupDir 'analyzer-0.9.0.jar')).Hash -ceq $oldAnalyzerHash) 'the original Analyzer recovery backup remains intact'
        Assert-True ((Get-FileHash -LiteralPath (Join-Path $installRetirement.Mods $installRetirementRelease.artifact) -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $installRetirementRelease.sha256) 'new Analyzer remains installed after retirement'

        $lockedRetirement = New-Fixture 'locked-retirement-postcommit'
        $lockedRelease = Set-Release $lockedRetirement '0.10.0'
        $lockedRelease.retireLegacyBaritone = $true; Write-Json $lockedRetirement.Manifest $lockedRelease
        $lockedLegacyPath = Join-Path $lockedRetirement.Mods 'baritone-locked.jar'; Copy-Item -LiteralPath $LegacyBaritonePath -Destination $lockedLegacyPath
        $legacyLock = [IO.File]::Open($lockedLegacyPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
        try { $lockRetireResult = Invoke-Updater $lockedRetirement }
        finally { $legacyLock.Dispose() }
        Assert-True ($lockRetireResult.Result.status -eq 'installed' -and @($lockRetireResult.Result.skippedRetirements).Count -eq 1) 'a locked legacy JAR reports a skip without rolling back committed Analyzer'
        Assert-True ((Get-FileHash -LiteralPath $lockedLegacyPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $legacyHash) 'failed atomic move leaves the original legacy JAR intact'
        Assert-True ((Get-FileHash -LiteralPath (Join-Path $lockedRetirement.Mods $lockedRelease.artifact) -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $lockedRelease.sha256) 'retirement failure never enters the Analyzer transaction rollback'
        Assert-True (@((Invoke-Updater $lockedRetirement).Result.retiredMods).Count -eq 1) 'an unlocked subsequent launch can safely finish deferred retirement'

        $tamperedRetirement = New-Fixture 'retirement-needs-verified-analyzer'
        $tamperedRelease = Set-Release $tamperedRetirement '0.10.0'
        $tamperedRelease.retireLegacyBaritone = $true; Write-Json $tamperedRetirement.Manifest $tamperedRelease
        $untouchedLegacy = Join-Path $tamperedRetirement.Mods 'baritone.jar'; Copy-Item -LiteralPath $LegacyBaritonePath -Destination $untouchedLegacy
        [IO.File]::AppendAllText((Join-Path $tamperedRetirement.Feed $tamperedRelease.artifact),'tampered')
        $tamperedRetireResult = Invoke-Updater $tamperedRetirement
        Assert-True ($tamperedRetireResult.Result.status -eq 'error' -and (Test-Path -LiteralPath $untouchedLegacy)) 'unverified Analyzer releases cannot retire any existing dependency'
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $tamperedRetirement.Instance '.analyzer-updater/retired-mods'))) 'failed verification creates no retirement backup paths'
    } else {
        Write-Output 'SKIP: Positive pinned-Baritone retirement fixtures need the archived official JAR; no positive retirement checks were counted.'
    }

    Write-Output "Updater integration tests passed: $checks checks. Runtime: $PowerShellPath. Fixtures: $testDir"
}
finally {
    $env:INST_DIR = $savedInstanceEnv
    $env:INST_MC_DIR = $savedMinecraftEnv
}
