param(
    [Parameter(Mandatory = $true)][string]$ProjectDir,
    [string[]]$Versions = @("1.20.1", "1.20.4", "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5")
)

# Windows port of build_mod.sh: builds each Minecraft version in its own copy of the project
# (parallel/consecutive builds must not share build/ outputs) and collects the jars in dist_builds.
$ErrorActionPreference = "Continue"
$dist = Join-Path $ProjectDir "dist_builds"
$logs = Join-Path $ProjectDir "build_logs"
$work = Join-Path $env:TEMP ("magic_build_" + (Split-Path $ProjectDir -Leaf))
New-Item -ItemType Directory -Force $dist, $logs, $work | Out-Null

$props = Get-Content (Join-Path $ProjectDir "gradle.properties")
$modVersion = (($props | Where-Object { $_ -match '^mod_version=' }) -split '=', 2)[1]
$summary = @()

foreach ($ver in $Versions) {
    $dir = Join-Path $work $ver
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
    robocopy $ProjectDir $dir /E /NFL /NDL /NJH /NJS /NP /XD .git .gradle .idea build build_logs dist_builds run Minecraft-Transit-Railway-master Transport-Simulation-Core-master | Out-Null

    $log = Join-Path $logs "build-$ver.log"
    Write-Host "BUILDING $ver"
    Push-Location $dir
    & .\gradlew.bat build "-Pminecraft_version=$ver" --no-daemon --console=plain *> $log
    $code = $LASTEXITCODE
    Pop-Location

    $jar = Get-ChildItem (Join-Path $dir "build\libs") -Filter *.jar -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch '-sources' } | Select-Object -First 1
    if ($code -eq 0 -and $jar) {
        $out = Join-Path $dist "MAGIC-$modVersion-$ver.jar"
        Copy-Item $jar.FullName $out -Force
        $summary += "$ver SUCCESS $out"
        Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        $summary += "$ver FAILED (exit $code) see $log"
    }
    Write-Host $summary[-1]
}

$summary | Set-Content (Join-Path $logs "summary.txt")
$summary
