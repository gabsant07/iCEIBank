param(
    [Parameter(Mandatory = $true)][ValidateSet(0, 1, 2)][int]$AgencyId,
    [Parameter(Mandatory = $true)][int]$Port
)

$project = Split-Path -Parent $PSScriptRoot
if (-not $env:RABBITMQ_URL) { throw "Defina RABBITMQ_URL antes de iniciar a agência." }
$jar = Join-Path $project "target\iceibank-0.0.1-SNAPSHOT.jar"
$env:AGENCY_ID = $AgencyId
$env:SERVER_PORT = $Port
Set-Location $project
java -jar $jar
