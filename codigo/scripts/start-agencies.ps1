$project = Split-Path -Parent $PSScriptRoot
if (-not $env:RABBITMQ_URL) { throw "Defina RABBITMQ_URL com a URL AMQP do broker antes de iniciar." }
$jar = Join-Path $project "target\iceibank-0.0.1-SNAPSHOT.jar"
$runtime = Join-Path $project ".runtime"

if (-not (Test-Path $jar)) {
    & (Join-Path $project "mvnw.cmd") clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed"
    }
}

New-Item -ItemType Directory -Force -Path $runtime | Out-Null

0..2 | ForEach-Object {
    $agency = $_
    $port = 8080 + $agency
    $process = Start-Process powershell -WindowStyle Hidden -PassThru -ArgumentList "-NoExit", "-File", (Join-Path $PSScriptRoot "run-agency.ps1"), "-AgencyId", $agency, "-Port", $port
    Set-Content -Path (Join-Path $runtime "agency-$agency.pid") -Value $process.Id
}

Write-Host "Agencies started on ports 8080, 8081 and 8082."
