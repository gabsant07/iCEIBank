param(
    [int]$BasePort = 18080,
    [string]$ManagementUrl = "",
    [string]$ManagementUser = "guest",
    [string]$ManagementPassword = "guest"
)
$ErrorActionPreference = "Stop"
if (-not $env:RABBITMQ_URL) { throw "Defina RABBITMQ_URL" }
$project = Split-Path -Parent $PSScriptRoot
$root = Split-Path -Parent $project
$run = Join-Path $project ("target/sprint2-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$evidence = Join-Path $root 'evidencias/sprint2'
New-Item -ItemType Directory -Force $run,$evidence | Out-Null
$jar = Join-Path $project 'target/iceibank-0.0.1-SNAPSHOT.jar'
$processes = @{}
$tokens = @{}
function Start-Agency([int]$id) {
    $port = $BasePort + $id
    $arguments = @('-jar', $jar, "--server.port=$port", "--bank.agency-id=$id",
        "--spring.datasource.url=jdbc:h2:file:$run/bank-$id;WRITE_DELAY=0", "--bank.event-directory=$run")
    $processes[$id] = Start-Process java -ArgumentList $arguments -WorkingDirectory $project -PassThru -WindowStyle Hidden -RedirectStandardOutput "$run/agency-$id.log" -RedirectStandardError "$run/agency-$id.err"
    $ready = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        if ($processes[$id].HasExited) { throw "Agência $id encerrou. Consulte $run" }
        try {
            $login = Invoke-RestMethod "http://localhost:$port/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
            $tokens[$id] = $login.token
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw "Agência $id não iniciou" }
}
function Call-Api([int]$id,[string]$path,[string]$method='Get',$body=$null) {
    $parameters = @{Uri="http://localhost:$($BasePort+$id)/api/$path"; Method=$method; Headers=@{Authorization="Bearer $($tokens[$id])"}}
    if ($null -ne $body) { $parameters.ContentType='application/json'; $parameters.Body=($body|ConvertTo-Json -Depth 8) }
    Invoke-RestMethod @parameters
}
function Require($condition,[string]$message) { if(-not $condition){throw $message}; Write-Output "PASS: $message" }
function Wait-Balance([int]$id,[long]$account,[decimal]$expected) {
    for($attempt=0;$attempt -lt 30;$attempt++) {
        $value=Call-Api $id "accounts/$account"
        if([decimal]$value.balance -eq $expected){return}
        Start-Sleep -Milliseconds 500
    }
    throw "Saldo esperado $expected; recebido $($value.balance)"
}
Start-Transcript -Path "$evidence/validacao-integrada.txt" -Force | Out-Null
try {
    Get-Date
    0..2 | ForEach-Object { Start-Agency $_ }
    $denied=$false
    try { Invoke-RestMethod "http://localhost:$BasePort/api/accounts" | Out-Null } catch { $denied=([int]$_.Exception.Response.StatusCode -eq 401) }
    Require $denied 'JWT obrigatório sem token'
    Call-Api 0 'accounts/3/deposits' Post @{amount=1} | Out-Null
    Call-Api 2 'accounts/5/deposits' Post @{amount=1} | Out-Null
    $transfer=Call-Api 0 'transfers' Post @{sourceAccount=3;destinationAccount=4;amount=25}
    Require ($transfer.status -eq 'PUBLISHED') 'Transferência assíncrona publicada'
    Wait-Balance 1 4 1275
    Write-Output ($transfer|ConvertTo-Json)
    Call-Api 1 'accounts' Post @{accountNumber=100;holderName='Resiliência';initialBalance=100} | Out-Null
    Copy-Item "$run/agency-1.log" "$evidence/agency-1-antes-reinicio.log"
    Stop-Process -Id $processes[1].Id
    $processes[1].WaitForExit()
    $transfer=Call-Api 0 'transfers' Post @{sourceAccount=3;destinationAccount=100;amount=30}
    Require ($transfer.status -eq 'PUBLISHED') 'Destino desligado: publicação continua aceita'
    Start-Agency 1
    Wait-Balance 1 100 130
    Require $true 'Após reinício H2 preservou conta e fila entregou crédito'
    $invalid=Call-Api 0 'transfers' Post @{sourceAccount=3;destinationAccount=1000;amount=5}
    Require ($invalid.status -eq 'PUBLISHED') 'Conta ausente: publicação aceita pelo broker'
    if($ManagementUrl) {
        $encoded=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${ManagementUser}:${ManagementPassword}"))
        $headers=@{Authorization="Basic $encoded"}
        $found=$false
        for($attempt=0;$attempt -lt 30;$attempt++) {
            $queue=Invoke-RestMethod "$ManagementUrl/api/queues/%2F/fila-agencia-1.dlq" -Headers $headers
            if($queue.messages_ready -gt 0 -and ((Get-Content "$run/agency-1.log" -Raw) -match 'CREDIT_REJECTED')){$found=$true;break}
            Start-Sleep -Seconds 1
        }
        Require $found 'Crédito para conta inexistente preservado na DLQ'
        Write-Output ($queue|Select-Object name,messages_ready|ConvertTo-Json)
    }
    $timeline = & node (Join-Path $PSScriptRoot 'mesclar-logs.js') $run
    $timeline | Set-Content "$evidence/linha-do-tempo-causal.txt"
    Require ([bool]($timeline -match 'CONCORRENTES')) 'Linha do tempo identifica eventos concorrentes reais'
    Require ([bool]($timeline -match 'ANTES')) 'Linha do tempo identifica causalidade real'
    Write-Output "Logs reais: $run"
    Copy-Item "$run/agency-*.log" $evidence
} finally {
    foreach($process in $processes.Values) { if(-not $process.HasExited){Stop-Process -Id $process.Id} }
    Stop-Transcript | Out-Null
}
