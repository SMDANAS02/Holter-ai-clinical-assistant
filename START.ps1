# Holter Monitor AI Pipeline - Project Starter

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     HOLTER MONITOR AI PIPELINE - PROJECT LAUNCHER             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Yellow
$javaCheck = java -version 2>&1 | Select-Object -First 1
if (-not ($javaCheck -match "version")) {
    Write-Host "❌ Java not found! Please install Java 17+" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Java: OK" -ForegroundColor Green

$pythonCheck = python --version 2>&1
if (-not ($pythonCheck -match "Python")) {
    Write-Host "⚠️  Python not found! Frontend requires Python" -ForegroundColor Yellow
} else {
    Write-Host "✅ Python: OK" -ForegroundColor Green
}
Write-Host ""

# Load environment
if (Test-Path ".env") {
    Write-Host "📁 Loading environment variables from .env..." -ForegroundColor Cyan
    Get-Content .env | ForEach-Object {
        if ($_ -match '^\s*([^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
    Write-Host "✅ Environment loaded" -ForegroundColor Green
} else {
    Write-Host "⚠️  .env file not found. Create it using: .\INSTALL.ps1" -ForegroundColor Yellow
}
Write-Host ""

# Backend Service
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "🖥️  STARTING BACKEND SERVICE" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "1️⃣  Launching ECG Pipeline..." -ForegroundColor Yellow
Write-Host "   • Input: Synthetic ECG data (1 day)" -ForegroundColor Gray
Write-Host "   • Output: findings.json" -ForegroundColor Gray
Write-Host "   • Processing..." -ForegroundColor Gray
Write-Host ""

# Prefer packaged JAR under backend/target
$jarShaded = 'backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar'
$jarPlain = 'backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar'
if (Test-Path $jarShaded) {
    $jarToRun = $jarShaded
} elseif (Test-Path $jarPlain) {
    $jarToRun = $jarPlain
} else {
    Write-Host "No packaged JAR found. Attempting to build with Maven..." -ForegroundColor Yellow
    & mvn -f backend\pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Maven build failed. Ensure Maven is installed or compile backend manually." -ForegroundColor Red
        exit 1
    }
    if (Test-Path $jarShaded) { $jarToRun = $jarShaded } else { $jarToRun = $jarPlain }
}

& java -Xmx2g -jar $jarToRun --input synthetic --output findings.json --days 1

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Backend pipeline completed successfully!" -ForegroundColor Green
    Write-Host "📁 Output: findings.json" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "❌ Backend pipeline failed!" -ForegroundColor Red
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

# Frontend Service
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "🎨 STARTING FRONTEND DASHBOARD" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "2️⃣  Launching Streamlit Dashboard..." -ForegroundColor Yellow
Write-Host ""

# Start Streamlit
Write-Host "📊 Dashboard starting..." -ForegroundColor Cyan
Write-Host "   🌐 URL: http://localhost:8501" -ForegroundColor Green
Write-Host "   • Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

streamlit run frontend/dashboard/st_dashboard.py

Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Yellow
Write-Host "Project stopped" -ForegroundColor Yellow
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Yellow
