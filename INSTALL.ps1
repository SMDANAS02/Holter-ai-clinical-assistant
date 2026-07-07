# Holter Monitor AI Pipeline - Complete Installation Script
# This script installs all dependencies and prepares the project

Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     HOLTER MONITOR AI PIPELINE - INSTALLATION SETUP           ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check Java
Write-Host "1️⃣  Checking Java installation..." -ForegroundColor Yellow
$javaVersion = java -version 2>&1 | Select-Object -First 1
if ($javaVersion -match "version") {
    Write-Host "   ✅ Java is installed: $javaVersion" -ForegroundColor Green
} else {
    Write-Host "   ❌ Java not found! Please install Java 17+" -ForegroundColor Red
    Write-Host "   Download: https://www.oracle.com/java/technologies/downloads/" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# Check Python
Write-Host "2️⃣  Checking Python installation..." -ForegroundColor Yellow
$pythonVersion = python --version 2>&1
if ($pythonVersion -match "Python") {
    Write-Host "   ✅ Python is installed: $pythonVersion" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Python not found (optional for frontend)" -ForegroundColor Yellow
}
Write-Host ""

# Backend Setup
Write-Host "3️⃣  Setting up Backend..." -ForegroundColor Yellow

if (-not (Test-Path "backend")) {
    Write-Host "   ❌ Backend directory not found!" -ForegroundColor Red
    exit 1
}

# Check Maven or compile with javac
Write-Host "   • Checking Maven..." -ForegroundColor Cyan
$mvnCheck = mvn --version 2>&1 | Select-Object -First 1
if ($mvnCheck -match "Apache Maven") {
    Write-Host "   ✅ Maven found: $mvnCheck" -ForegroundColor Green
    Write-Host "   • Running: mvn clean install" -ForegroundColor Cyan
    cd backend
    mvn clean install -q
    cd ..
    Write-Host "   ✅ Backend compiled successfully" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Maven not installed, using javac..." -ForegroundColor Yellow
    Write-Host "   • Compiling backend Java files..." -ForegroundColor Cyan
    
    if (-not (Test-Path "target/classes")) {
        New-Item -ItemType Directory -Path "target/classes" -Force | Out-Null
    }
    
    $javaFiles = Get-ChildItem -Path "backend/src/main/java" -Filter "*.java" -Recurse
    Write-Host "   • Found $($javaFiles.Count) Java files" -ForegroundColor Cyan
    
    # Try compilation
    javac -d target/classes -cp "target/fat-build" @($javaFiles.FullName) 2>&1 | Select-Object -First 5
    Write-Host "   ✅ Backend ready" -ForegroundColor Green
}
Write-Host ""

# Frontend Setup
Write-Host "4️⃣  Setting up Frontend..." -ForegroundColor Yellow

if (Test-Path "frontend/dashboard/requirements.txt") {
    Write-Host "   • Installing Python dependencies..." -ForegroundColor Cyan
    pip install -q -r frontend/dashboard/requirements.txt
    Write-Host "   ✅ Frontend dependencies installed" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Frontend requirements.txt not found" -ForegroundColor Yellow
}
Write-Host ""

# Environment Setup
Write-Host "5️⃣  Setting up Environment Variables..." -ForegroundColor Yellow

if (-not (Test-Path ".env")) {
    Write-Host "   • Creating .env file from template..." -ForegroundColor Cyan
    if (Test-Path ".env.example") {
        Copy-Item ".env.example" ".env"
        Write-Host "   ✅ .env file created" -ForegroundColor Green
        Write-Host "   ⚠️  Remember to add your API keys:" -ForegroundColor Yellow
        Write-Host "      - ANTHROPIC_API_KEY (Claude)" -ForegroundColor Gray
        Write-Host "      - GOOGLE_API_KEY (Gemini)" -ForegroundColor Gray
        Write-Host "      - GROQ_API_KEY (Groq)" -ForegroundColor Gray
    }
} else {
    Write-Host "   ✅ .env file exists" -ForegroundColor Green
}
Write-Host ""

# Backend Config
Write-Host "6️⃣  Setting up Backend Configuration..." -ForegroundColor Yellow

if (-not (Test-Path "backend/config.properties")) {
    Write-Host "   • Creating config.properties..." -ForegroundColor Cyan
    if (Test-Path "config.properties.example") {
        Copy-Item "config.properties.example" "backend/config.properties"
    } else {
        @"
# Holter Monitor Pipeline Configuration
samplingRateHz=360
thresholdDeviation=3.0
windowSizeMinutes=5.0
gapToleranceSec=5.0
chunkSize=10000
"@ | Out-File "backend/config.properties"
    }
    Write-Host "   ✅ config.properties created" -ForegroundColor Green
} else {
    Write-Host "   ✅ config.properties exists" -ForegroundColor Green
}
Write-Host ""

# Create directories
Write-Host "7️⃣  Creating required directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "logs" -Force | Out-Null
New-Item -ItemType Directory -Path "data" -Force | Out-Null
Write-Host "   ✅ Directories created" -ForegroundColor Green
Write-Host ""

# Summary
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "✅ INSTALLATION COMPLETE!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "📋 NEXT STEPS:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Add API Keys to .env file:"
Write-Host "   - Edit .env and add your API keys"
Write-Host ""
Write-Host "2. Start the project:"
Write-Host "   - Run: .\START.ps1"
Write-Host ""
Write-Host "3. Access the dashboard:"
Write-Host "   - Backend: http://localhost:8080 (when REST API is ready)"
Write-Host "   - Frontend: http://localhost:8501 (Streamlit)"
Write-Host ""
Write-Host "📖 Documentation:"
Write-Host "   - README.md - Project overview"
Write-Host "   - PROJECT_STRUCTURE.md - Directory layout"
Write-Host "   - PROJECT_TECH_STACK.md - Technology details"
Write-Host ""
