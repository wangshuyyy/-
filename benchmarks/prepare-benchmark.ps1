$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$VoucherId = 100
$Stock = 1000000

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $dockerExe = $dockerCommand.Source
} else {
    $dockerExe = Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"
}
if (-not (Test-Path -LiteralPath $dockerExe)) {
    throw "Docker CLI not found. Start Docker Desktop first."
}

Get-Content -Raw "$PSScriptRoot/prepare-benchmark.sql" | `
  & $dockerExe compose -f "$projectDir/docker-compose.yml" exec -T mysql mysql -uroot -p123456 hmdp

$beginAt = [DateTimeOffset]::Now.AddDays(-1).ToUnixTimeMilliseconds()
$endAt = [DateTimeOffset]::Now.AddDays(30).ToUnixTimeMilliseconds()
& $dockerExe compose -f "$projectDir/docker-compose.yml" exec -T redis redis-cli MSET `
  "seckill:stock:$VoucherId" "$Stock" `
  "seckill:begin:$VoucherId" "$beginAt" `
  "seckill:end:$VoucherId" "$endAt" | Out-Null
& $dockerExe compose -f "$projectDir/docker-compose.yml" exec -T redis redis-cli DEL "seckill:order:$VoucherId" | Out-Null

Write-Host "Benchmark data prepared: voucher=$VoucherId stock=$Stock"
