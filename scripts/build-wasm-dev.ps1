<#
.SYNOPSIS
    Build a local Interfold WASM Docker image tagged :dev.

.DESCRIPTION
    Compiles :webApp:wasmJsBrowserDistribution via Dockerfile.wasm and loads
    the result into Docker Desktop as interfold-wasm:dev so local builds are
    easy to tell apart from GHCR :latest / :sha-* images.

    Native platform only (no multi-arch). First run is a full Gradle/Wasm
    compile inside the builder stage and can take several minutes; later
    runs reuse Docker BuildKit cache mounts.

.PARAMETER Run
    Start the image on http://localhost:8080 after a successful build.

.EXAMPLE
    .\scripts\build-wasm-dev.ps1

.EXAMPLE
    .\scripts\build-wasm-dev.ps1 -Run
#>
[CmdletBinding()]
param(
    [switch]$Run
)

$ErrorActionPreference = "Stop"

$Image = "interfold-wasm:dev"
$RepoRoot = Split-Path -Parent $PSScriptRoot

Set-Location $RepoRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker is not on PATH. Start Docker Desktop and try again."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not reachable. Start Docker Desktop and try again."
}

$dockerfile = Join-Path $RepoRoot "Dockerfile.wasm"
if (-not (Test-Path $dockerfile)) {
    throw "Dockerfile.wasm not found at $dockerfile"
}

Write-Host "Building $Image from Dockerfile.wasm (native platform)..."
$started = Get-Date

$env:DOCKER_BUILDKIT = "1"
docker build -f Dockerfile.wasm -t $Image .
if ($LASTEXITCODE -ne 0) {
    throw "docker build failed with exit code $LASTEXITCODE"
}

$elapsed = (Get-Date) - $started
Write-Host ""
Write-Host ("Built {0} in {1:mm\:ss}." -f $Image, $elapsed)
Write-Host "Run with:  docker run --rm -p 8080:8080 $Image"

if ($Run) {
    Write-Host "Starting $Image on http://localhost:8080 ..."
    docker run --rm -p 8080:8080 $Image
    if ($LASTEXITCODE -ne 0) {
        throw "docker run failed with exit code $LASTEXITCODE"
    }
}
