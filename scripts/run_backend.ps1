# Run backend with environment vars loaded from .env
if (Test-Path '.env') {
    $lines = Get-Content -Path ".env" -ErrorAction SilentlyContinue
    foreach ($line in $lines) {
        if ($line -match '^[^#\s].+=.*') {
            $parts = $line -split '=',2
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()
            [System.Environment]::SetEnvironmentVariable($name,$value,'Process')
        }
    }
    Write-Host 'Environment variables loaded for this process'
} else {
    Write-Host '.env not found; continuing without loading env file' -ForegroundColor Yellow
}

# Prefer backend JAR in backend/target
$jarShaded = 'backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar'
$jarPlain = 'backend\target\holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar'
if (Test-Path $jarShaded) {
    & java -Xmx2g -jar $jarShaded --input synthetic --output findings.json --days 1
} elseif (Test-Path $jarPlain) {
    & java -Xmx2g -jar $jarPlain --input synthetic --output findings.json --days 1
} else {
    Write-Host 'No packaged JAR found. Building with Maven (backend)...' -ForegroundColor Yellow
    & mvn -f backend\pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Maven build failed; cannot run backend.' -ForegroundColor Red
        exit 1
    }
    if (Test-Path $jarShaded) { & java -Xmx2g -jar $jarShaded --input synthetic --output findings.json --days 1 }
    else { & java -Xmx2g -jar $jarPlain --input synthetic --output findings.json --days 1 }
}
