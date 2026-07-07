@echo off
REM Holter Monitor AI Pipeline - Installation Script (Windows CMD)
REM Click this file to setup the entire project

setlocal enabledelayedexpansion

echo.
echo ════════════════════════════════════════════════════════════════
echo      HOLTER MONITOR AI PIPELINE - INSTALLATION SETUP
echo ════════════════════════════════════════════════════════════════
echo.

REM Check Java
echo [1/7] Checking Java installation...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found!
    echo Please install Java 17 from:
    echo https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
for /f "tokens=*" %%a in ('java -version 2^>^&1 ^| findstr /r "version"') do (
    echo OK - %%a
)
echo.

REM Check Python
echo [2/7] Checking Python installation...
python --version >nul 2>&1
if errorlevel 1 (
    echo WARNING: Python not found (optional for frontend)
) else (
    for /f "tokens=*" %%a in ('python --version 2^>^&1') do (
        echo OK - %%a
    )
)
echo.

REM Backend Setup
echo [3/7] Setting up Backend...
if not exist backend (
    echo ERROR: Backend directory not found!
    pause
    exit /b 1
)
echo OK - Backend directory found
echo.

REM Frontend Setup
echo [4/7] Setting up Frontend...
if exist frontend\dashboard\requirements.txt (
    echo Installing Python dependencies...
    pip install -q -r frontend\dashboard\requirements.txt
    echo OK - Frontend dependencies installed
) else (
    echo WARNING: Frontend requirements.txt not found
)
echo.

REM Environment Setup
echo [5/7] Setting up Environment Variables...
if not exist .env (
    echo Creating .env file from template...
    if exist .env.example (
        copy .env.example .env >nul
        echo OK - .env file created
        echo WARNING: Add your API keys to .env:
        echo   - ANTHROPIC_API_KEY (Claude)
        echo   - GOOGLE_API_KEY (Gemini)
        echo   - GROQ_API_KEY (Groq)
    )
) else (
    echo OK - .env file exists
)
echo.

REM Backend Config
echo [6/7] Setting up Backend Configuration...
if not exist backend\config.properties (
    echo Creating config.properties...
    (
        echo # Holter Monitor Pipeline Configuration
        echo samplingRateHz=360
        echo thresholdDeviation=3.0
        echo windowSizeMinutes=5.0
        echo gapToleranceSec=5.0
        echo chunkSize=10000
    ) > backend\config.properties
    echo OK - config.properties created
) else (
    echo OK - config.properties exists
)
echo.

REM Create directories
echo [7/7] Creating required directories...
if not exist logs mkdir logs
if not exist data mkdir data
echo OK - Directories created
echo.

REM Summary
echo ════════════════════════════════════════════════════════════════
echo INSTALLATION COMPLETE!
echo ════════════════════════════════════════════════════════════════
echo.
echo NEXT STEPS:
echo.
echo 1. Add API Keys to .env file:
echo    - Edit .env and add your API keys
echo.
echo 2. Start the project:
echo    - Run: START.bat
echo.
echo 3. Access the dashboard:
echo    - Frontend: http://localhost:8501 (Streamlit)
echo.
echo Documentation:
echo   - README.md - Project overview
echo   - PROJECT_STRUCTURE.md - Directory layout
echo   - PROJECT_TECH_STACK.md - Technology details
echo.
pause
