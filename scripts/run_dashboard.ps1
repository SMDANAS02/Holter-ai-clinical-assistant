$projectDir = (Get-Location).Path
$scriptPath = Join-Path $projectDir 'dashboard\st_dashboard.py'
Write-Host "Starting Streamlit dashboard from $projectDir"
Start-Process -FilePath 'python' -ArgumentList '-m','streamlit','run',$scriptPath,'--server.port','8501','--server.headless','true' -WorkingDirectory $projectDir
Write-Host 'Streamlit process launched.'
