@echo off
REM Holter Monitor AI Pipeline - Simple Launcher (Windows CMD)
REM Click this file to run the entire project

setlocal enabledelayedexpansion

echo.
echo ════════════════════════════════════════════════════════════════
echo      HOLTER MONITOR AI PIPELINE - PROJECT LAUNCHER
echo ════════════════════════════════════════════════════════════════
echo.

REM Check Java
echo Checking Java installation...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found! Please install Java 17+
    echo Download: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
echo OK - Java found
echo.

REM Load .env if exists
if exist .env (
    echo Loading environment variables...
    for /f "tokens=*" %%a in (.env) do (
        set "%%a"
    )
    echo OK - Environment loaded
) else (
    echo WARNING: .env file not found. Run INSTALL.bat first.
)
echo.

REM Backend Phase
echo ════════════════════════════════════════════════════════════════
echo STARTING BACKEND PIPELINE
echo ════════════════════════════════════════════════════════════════
echo.

echo [1/2] Running ECG Analysis Pipeline...
echo        Input: Synthetic 1-day ECG data
echo        Output: findings.json
echo        Processing...
echo.

REM Prefer packaged JAR in backend/target
if exist backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar (
    set JAR=backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar
) else if exist backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar (
    set JAR=backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar
) else (
    echo No packaged JAR found. Attempting to build with Maven...
    mvn -f backend\pom.xml -DskipTests package
    if errorlevel 1 (
        echo ERROR: Maven build failed. Ensure Maven is installed or compile backend manually.
        pause
        exit /b 1
    )
    if exist backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar (
        set JAR=backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar
    ) else (
        set JAR=backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar
    )
)

java -Xmx2g -jar %JAR% --input synthetic --output findings.json --days 1

if errorlevel 1 (
    echo ERROR: Backend pipeline failed!
    pause
    exit /b 1
)

echo.
echo OK - Backend pipeline completed!
echo Output file: findings.json
echo.

REM Frontend Phase
echo ════════════════════════════════════════════════════════════════
echo STARTING FRONTEND DASHBOARD
echo ════════════════════════════════════════════════════════════════
echo.

echo [2/2] Launching Streamlit Dashboard...
echo.
echo     Access at: http://localhost:8501
echo     Press Ctrl+C to stop
echo.

streamlit run frontend\dashboard\st_dashboard.py

echo.
echo Project stopped
echo.
pause
