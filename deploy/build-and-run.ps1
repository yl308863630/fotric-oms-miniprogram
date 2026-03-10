# Build frontend, then backend, and print JAR path.
# Run from project root or any directory; script resolves project root from deploy/ folder.

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent

Write-Host "=== Building frontend (oms-frontend) ===" -ForegroundColor Cyan
Set-Location (Join-Path $ProjectRoot "oms-frontend")
npm run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`n=== Building backend (oms-backend) ===" -ForegroundColor Cyan
Set-Location (Join-Path $ProjectRoot "oms-backend")
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$JarPath = Join-Path $ProjectRoot "oms-backend\target\oms-backend-0.0.1-SNAPSHOT.jar"
Write-Host "`n=== Build complete ===" -ForegroundColor Green
Write-Host "JAR path: $JarPath"
Write-Host "Run the application with: java -jar `"$JarPath`""
