# Run the packaged Spring Boot JAR from project root.
# Resolves project root as parent of deploy/ folder.

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$JarPath = Join-Path $ProjectRoot "oms-backend\target\oms-backend-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $JarPath)) {
    Write-Error "JAR not found: $JarPath. Run deploy\build-and-run.ps1 first."
    exit 1
}

Set-Location $ProjectRoot
java -jar $JarPath
