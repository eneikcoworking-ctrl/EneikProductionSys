param(
    [string]$Remote = "origin",
    [string]$Branch = "main",
    [string]$BackendContainer = "eneikproductionsys-backend-1",
    [switch]$SkipFetch
)

$ErrorActionPreference = "Stop"

if (-not $SkipFetch) {
    git fetch $Remote $Branch --prune | Out-Null
}

$localHead = (git rev-parse $Branch).Trim()
$remoteHead = (git rev-parse "$Remote/$Branch").Trim()
$aheadBehind = (git rev-list --left-right --count "$Branch...$Remote/$Branch").Trim()
$porcelain = @(git status --porcelain)
$dirty = $porcelain.Count -gt 0

$containerRevision = "unknown"
$containerDirty = "unknown"
$containerRunning = $false
try {
    $containerRunning = ((docker inspect $BackendContainer --format '{{.State.Running}}') -eq "true")
    $labels = docker inspect $BackendContainer --format '{{json .Config.Labels}}' | ConvertFrom-Json
    $containerRevision = [string]$labels.'org.opencontainers.image.revision'
    $containerDirty = [string]$labels.'com.eneik.build.git-dirty'
    if ([string]::IsNullOrWhiteSpace($containerRevision) -or $containerRevision -eq "<no value>") {
        $containerRevision = "unknown"
    }
    if ([string]::IsNullOrWhiteSpace($containerDirty) -or $containerDirty -eq "<no value>") {
        $containerDirty = "unknown"
    }
} catch {
    $containerRunning = $false
}

$commitSynced = $localHead -eq $remoteHead
$runtimeKnown = $containerRevision -ne "unknown"
$runtimeSynced = $runtimeKnown -and $containerRevision -eq $localHead -and $containerDirty -eq "false"
$ok = $commitSynced -and -not $dirty -and $containerRunning -and $runtimeSynced

[pscustomobject]@{
    ok = $ok
    branch = $Branch
    localHead = $localHead
    remoteHead = $remoteHead
    aheadBehind = $aheadBehind
    worktreeDirty = $dirty
    dirtyItems = $porcelain
    backendRunning = $containerRunning
    backendImageRevision = $containerRevision
    backendImageDirty = $containerDirty
    runtimeMatchesLocalHead = $runtimeSynced
} | ConvertTo-Json -Depth 4

if (-not $ok) {
    exit 2
}
