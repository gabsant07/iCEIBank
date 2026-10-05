$project = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $project "frontend"

& (Join-Path $PSScriptRoot "start-agencies.ps1")

Start-Sleep -Seconds 5
Start-Process powershell -WindowStyle Hidden -ArgumentList "-NoExit", "-Command", "Set-Location '$frontend'; npm install; npm run dev"

Write-Host "Backend: http://localhost:8080, 8081 e 8082"
Write-Host "Frontend: http://localhost:5173"
