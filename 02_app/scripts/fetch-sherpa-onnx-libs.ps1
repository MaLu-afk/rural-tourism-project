<#
.SYNOPSIS
    Descarga las librerías nativas de Sherpa-ONNX y las copia a app/src/main/jniLibs/<abi>/.

.DESCRIPTION
    El TTS de la app (motor SherpaOnnxTtsEngine) usa el binding Kotlin VENDORIZADO en
    com/k2fsa/sherpa/onnx/Tts.kt, cuyas funciones `external` resuelve la librería nativa
    `libsherpa-onnx-jni.so` (+ `libonnxruntime.so`). Esas .so NO están en el repo (ver
    app/build.gradle.kts) y SIN ELLAS el audio nunca suena: OfflineTts() lanza
    UnsatisfiedLinkError → la síntesis falla → el botón de play se queda en silencio.

    Este script descarga el AAR oficial del RELEASE DE GITHUB (es un .zip), extrae las .so de
    jni/<abi>/ y las copia a app/src/main/jniLibs/<abi>/. La app COMPILA igual sin ejecutarlo;
    solo el TTS deja de fallar una vez copiadas las .so.

    Versión por defecto: 1.13.3 (verificado: sus firmas JNI `external` coinciden EXACTAMENTE con
    el binding vendorizado com/k2fsa/sherpa/onnx/Tts.kt de este repo, así que enlaza sin tocar nada).

.PARAMETER Version
    Versión de Sherpa-ONNX a descargar. DEBE ser compatible con el binding vendorizado
    (com/k2fsa/sherpa/onnx/Tts.kt). Si cambias de versión y tras copiar las .so aparece un
    UnsatisfiedLinkError de un método concreto, re-vendoriza ese Tts.kt desde el MISMO tag:
      https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v<VER>/sherpa-onnx/kotlin-api/Tts.kt
    Releases: https://github.com/k2-fsa/sherpa-onnx/releases (usa tags 'v<X.Y.Z>')

.PARAMETER AarUrl
    URL directa al .aar (sobrescribe la construida desde -Version).

.PARAMETER Abis
    ABIs a copiar. Por defecto SOLO 'arm64-v8a' (móviles modernos), en línea con el abiFilters de
    app/build.gradle.kts. Añade más si las necesitas, p.ej. -Abis arm64-v8a,x86_64 (emulador) o
    -Abis arm64-v8a,armeabi-v7a (ARM 32-bit antiguo). Recuerda añadirlas también en abiFilters.

.PARAMETER Force
    Sobrescribe las .so aunque ya existan.

.EXAMPLE
    pwsh ./fetch-sherpa-onnx-libs.ps1
    pwsh ./fetch-sherpa-onnx-libs.ps1 -Version 1.13.3
    pwsh ./fetch-sherpa-onnx-libs.ps1 -AarUrl https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.3/sherpa-onnx-1.13.3.aar
#>
[CmdletBinding()]
param(
    [string]$Version = "1.13.3",
    [string]$AarUrl,
    [string[]]$Abis = @("arm64-v8a"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Las dos .so que necesita el motor.
$RequiredLibs = @("libsherpa-onnx-jni.so", "libonnxruntime.so")

# Raíz del módulo app (este script vive en 02_app/scripts/).
$AppRoot = Resolve-Path (Join-Path $PSScriptRoot "..\app")
$JniLibsRoot = Join-Path $AppRoot "src\main\jniLibs"

if (-not $AarUrl) {
    # Release versionado de GitHub: el TAG lleva 'v' (v1.13.3), el fichero .aar NO (sherpa-onnx-1.13.3.aar).
    $AarUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$Version/sherpa-onnx-$Version.aar"
}

Write-Host "== Sherpa-ONNX .so fetcher ==" -ForegroundColor Cyan
Write-Host "Versión : $Version"
Write-Host "AAR URL : $AarUrl"
Write-Host "Destino : $JniLibsRoot"
Write-Host "ABIs    : $($Abis -join ', ')"
Write-Host ""

# Zona temporal de trabajo.
$WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) ("sherpa-so-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
$AarZip = Join-Path $WorkDir "sherpa-onnx.zip"   # el .aar es un zip; lo guardamos con extensión .zip
$ExtractDir = Join-Path $WorkDir "extracted"

try {
    Write-Host "Descargando AAR..." -ForegroundColor Yellow
    try {
        Invoke-WebRequest -Uri $AarUrl -OutFile $AarZip -UseBasicParsing
    }
    catch {
        Write-Host ""
        Write-Host "ERROR: no se pudo descargar el AAR desde:" -ForegroundColor Red
        Write-Host "  $AarUrl"
        Write-Host ""
        Write-Host "Soluciones:" -ForegroundColor Yellow
        Write-Host "  1) Comprueba la última versión en https://github.com/k2-fsa/sherpa-onnx/releases"
        Write-Host "     y vuelve a ejecutar con  -Version <X.Y.Z>"
        Write-Host "  2) O pasa la URL directa del .aar con  -AarUrl <url>"
        Write-Host "  3) Alternativa manual: descarga el tarball de Android del release, descomprime"
        Write-Host "     y copia jni/<abi>/{libsherpa-onnx-jni.so,libonnxruntime.so} a:"
        Write-Host "     $JniLibsRoot\<abi>\"
        throw
    }

    Write-Host "Extrayendo..." -ForegroundColor Yellow
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path $ExtractDir) { Remove-Item $ExtractDir -Recurse -Force }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($AarZip, $ExtractDir)

    # Dentro del AAR las .so viven en jni/<abi>/.
    $JniSource = Join-Path $ExtractDir "jni"
    if (-not (Test-Path $JniSource)) {
        throw "El AAR no contiene carpeta 'jni/' (layout inesperado). Revisa $ExtractDir"
    }

    $copied = 0
    $missing = @()
    foreach ($abi in $Abis) {
        $srcAbi = Join-Path $JniSource $abi
        if (-not (Test-Path $srcAbi)) {
            Write-Host "  [$abi] no presente en el AAR; se omite." -ForegroundColor DarkYellow
            $missing += $abi
            continue
        }
        $destAbi = Join-Path $JniLibsRoot $abi
        New-Item -ItemType Directory -Path $destAbi -Force | Out-Null
        foreach ($lib in $RequiredLibs) {
            $srcLib = Join-Path $srcAbi $lib
            $destLib = Join-Path $destAbi $lib
            if (-not (Test-Path $srcLib)) {
                Write-Host "  [$abi] FALTA $lib en el AAR" -ForegroundColor Red
                $missing += "$abi/$lib"
                continue
            }
            if ((Test-Path $destLib) -and -not $Force) {
                Write-Host "  [$abi] $lib ya existe (usa -Force para sobrescribir)" -ForegroundColor DarkGray
                continue
            }
            Copy-Item -Path $srcLib -Destination $destLib -Force
            $size = "{0:N1} MB" -f ((Get-Item $destLib).Length / 1MB)
            Write-Host "  [$abi] $lib  ($size)" -ForegroundColor Green
            $copied++
        }
    }

    # Verificación final: cada ABI presente debe tener las dos .so.
    Write-Host ""
    Write-Host "== Verificación ==" -ForegroundColor Cyan
    $allOk = $true
    foreach ($abi in $Abis) {
        $destAbi = Join-Path $JniLibsRoot $abi
        $present = @($RequiredLibs | Where-Object { Test-Path (Join-Path $destAbi $_) })
        if ($present.Count -eq $RequiredLibs.Count) {
            Write-Host "  [$abi] OK (2/2 .so)" -ForegroundColor Green
        }
        else {
            Write-Host "  [$abi] incompleto ($($present.Count)/2 .so)" -ForegroundColor Yellow
            if ($abi -eq "arm64-v8a") { $allOk = $false }
        }
    }

    Write-Host ""
    if ($allOk) {
        Write-Host "Listo: $copied fichero(s) copiado(s). Recompila el módulo app (./gradlew :app:assembleDebug)." -ForegroundColor Green
        Write-Host "Verifica en el dispositivo: descarga y activa una voz, entra a un tip y pulsa play." -ForegroundColor Green
    }
    else {
        Write-Host "AVISO: falta el ABI imprescindible 'arm64-v8a'. El TTS no funcionará en móviles reales." -ForegroundColor Red
        exit 1
    }
}
finally {
    Remove-Item $WorkDir -Recurse -Force -ErrorAction SilentlyContinue
}
