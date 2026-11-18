param(
    [string]$McRoot = "",
    [switch]$SkipRemap
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$versionDir = Split-Path -Parent $root
$versionName = Split-Path -Leaf $versionDir
if (-not $McRoot) {
    $McRoot = (Resolve-Path (Join-Path $root "../../..")).Path
}

$versionJson = Join-Path $versionDir "$versionName.json"
$vanillaJar = Join-Path $versionDir "$versionName.jar"
$libraries = Join-Path $McRoot "libraries"

if (-not (Test-Path $versionJson)) {
    throw "Version json not found: $versionJson"
}

$json = Get-Content -Raw -Encoding UTF8 $versionJson | ConvertFrom-Json
$neoForgeVersion = "21.1.250"
if ($json.arguments -and $json.arguments.jvm) {
    for ($i = 0; $i -lt $json.arguments.jvm.Count; $i++) {
        if ($json.arguments.jvm[$i] -eq "--fml.neoForgeVersion") {
            $neoForgeVersion = $json.arguments.jvm[$i + 1]
        }
    }
}
$neoClient = Join-Path $libraries "net/neoforged/neoforge/$neoForgeVersion/neoforge-$neoForgeVersion-client.jar"
$neoUniversal = Join-Path $libraries "net/neoforged/neoforge/$neoForgeVersion/neoforge-$neoForgeVersion-universal.jar"
if (-not (Test-Path $neoClient)) {
    throw "NeoForge artifact not found: $neoClient"
}

$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$resources = Join-Path $root "src/main/resources"
$mojmapJar = Join-Path $buildDir "mc-mojmap-1.21.1.jar"

# ---------- 1. build mojmap compile jar ----------
if (-not $SkipRemap -and -not (Test-Path $mojmapJar)) {
    Write-Host "[1/3] Building Mojang-mapped compile jar (first run only)..." -ForegroundColor Cyan
    $mappings = Get-ChildItem (Join-Path $libraries "net/minecraft/client") -Recurse -Filter "*-mappings.txt" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $mappings) {
        throw "Official mappings not found (libraries/net/minecraft/client/*-mappings.txt)"
    }
    $asmRoot = Join-Path $libraries "org/ow2/asm"
    $asmCp = @(
        (Join-Path $asmRoot "asm/9.10.1/asm-9.10.1.jar"),
        (Join-Path $asmRoot "asm-tree/9.10.1/asm-tree-9.10.1.jar"),
        (Join-Path $asmRoot "asm-commons/9.10.1/asm-commons-9.10.1.jar"),
        (Join-Path $asmRoot "asm-analysis/9.10.1/asm-analysis-9.10.1.jar")
    ) -join ";"
    $remapClasses = Join-Path $buildDir "remap-classes"
    New-Item -ItemType Directory -Force -Path $remapClasses | Out-Null
    & javac --release 21 -encoding UTF-8 -cp $asmCp -d $remapClasses (Join-Path $root "tools/RemapTool.java")
    if ($LASTEXITCODE -ne 0) { throw "Remap tool compile failed" }
    & java -cp "$asmCp;$remapClasses" RemapTool $mappings.FullName $vanillaJar $mojmapJar
    if ($LASTEXITCODE -ne 0) { throw "Remap failed" }
}

# ---------- 2. compile mod ----------
Write-Host "[2/3] Compiling mod..." -ForegroundColor Cyan
$classpath = @($neoClient, $mojmapJar, $neoUniversal)

$nsukJar = Get-ChildItem (Join-Path $versionDir "mods") -Filter "New-Sim-U-Kraft-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.disabled" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($nsukJar) {
    $classpath += $nsukJar.FullName
} else {
    Write-Warning "New-Sim-U-Kraft jar not found in mods/"
}
$mixinJar = Join-Path $libraries "net/fabricmc/sponge-mixin/0.15.2+mixin.0.8.7/sponge-mixin-0.15.2+mixin.0.8.7.jar"
if (Test-Path $mixinJar) { $classpath += $mixinJar }
$mixinExtrasJar = Join-Path $libraries "io/github/llamalad7/mixinextras-common/0.5.0/mixinextras-common-0.5.0.jar"
if (Test-Path $mixinExtrasJar) { $classpath += $mixinExtrasJar }

$ldlibJar = Get-ChildItem (Join-Path $versionDir "mods") -Filter "ldlib2-*.jar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($ldlibJar) {
    $classpath += $ldlibJar.FullName
    $jarjarDir = Join-Path $buildDir "ldlib-jarjar"
    New-Item -ItemType Directory -Force -Path $jarjarDir | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $ldZip = [System.IO.Compression.ZipFile]::OpenRead($ldlibJar.FullName)
    try {
        foreach ($ldEntry in $ldZip.Entries) {
            if ($ldEntry.FullName -like "META-INF/jarjar/*.jar") {
                $target = Join-Path $jarjarDir (Split-Path -Leaf $ldEntry.FullName)
                if (-not (Test-Path $target)) {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($ldEntry, $target, $true)
                }
                $classpath += $target
            }
        }
    } finally {
        $ldZip.Dispose()
    }
}

foreach ($lib in $json.libraries) {
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) {
        $p = Join-Path $libraries ($lib.downloads.artifact.path -replace '/', '\')
        if (Test-Path $p) { $classpath += $p }
    }
}
$cp = $classpath -join ";"
if (Test-Path $classesDir) { Remove-Item -Recurse -Force $classesDir }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
$sources = Get-ChildItem (Join-Path $root "src/main/java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& javac --release 21 -encoding UTF-8 -proc:none -cp $cp -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw "Mod compilation failed" }

# ---------- 3. package ----------
Write-Host "[3/3] Packaging..." -ForegroundColor Cyan
$libsDir = Join-Path $buildDir "libs"
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null
$outJar = Join-Path $libsDir "nsukcropapi-1.1.1.jar"
if (Test-Path $outJar) { Remove-Item -Force $outJar }
& jar --create --file $outJar -C $classesDir . -C $resources .
if ($LASTEXITCODE -ne 0) { throw "Packaging failed" }

Write-Host "Done: $outJar" -ForegroundColor Green
