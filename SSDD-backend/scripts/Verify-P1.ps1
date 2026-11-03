#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$JavaExecutable,
    [int]$Port = 3000
)

$ErrorActionPreference = 'Stop'
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar = Join-Path $backendRoot 'target/SSDD-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the application JAR first.' }
$reportDir = Join-Path $backendRoot 'docs/p1'
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-SmokeSession([string]$Name, [string]$Profile, [bool]$DocsEnabled) {
    # Refuse to probe or stop an unrelated process already occupying this port.
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } finally { $listener.Stop() }
    $outLog = Join-Path $backendRoot "target/p1-$Name.stdout.log"
    $errLog = Join-Path $backendRoot "target/p1-$Name.stderr.log"
    $process = Start-Process -FilePath $JavaExecutable -ArgumentList @(
        '-jar', "`"$jar`"", "--spring.profiles.active=$Profile", "--server.port=$Port", '--server.address=127.0.0.1'
    ) -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    try {
        $ready = $false
        for ($attempt = 0; $attempt -lt 120; $attempt++) {
            $process.Refresh()
            if ($process.HasExited) { throw "Smoke process $Name exited; see target/p1-$Name.*.log" }
            try {
                $health = Invoke-WebRequest "http://127.0.0.1:$Port/health" -TimeoutSec 2 -SkipHttpErrorCheck
                if ($health.StatusCode -eq 200) { $ready = $true; break }
            } catch { }
            Start-Sleep -Milliseconds 250
        }
        Assert-Condition $ready "Smoke process $Name did not become healthy."
        $healthJson = $health.Content | ConvertFrom-Json
        Assert-Condition ($healthJson.status -eq 'ok') 'Invalid /health body.'
        Assert-Condition (-not ($healthJson.PSObject.Properties.Name -contains 'success')) '/health must not be wrapped.'
        Assert-Condition ([string]$health.Headers['X-Request-Id'] -match '^[a-zA-Z0-9._-]+$') 'Missing request ID.'
        $readiness = Invoke-WebRequest "http://127.0.0.1:$Port/actuator/health/readiness" -SkipHttpErrorCheck
        Assert-Condition ($readiness.StatusCode -eq 200) 'Database readiness is not UP.'
        $liveness = Invoke-WebRequest "http://127.0.0.1:$Port/actuator/health/liveness" -SkipHttpErrorCheck
        Assert-Condition ($liveness.StatusCode -eq 200) 'Liveness is not UP.'
        $apiDocs = Invoke-WebRequest "http://127.0.0.1:$Port/v3/api-docs" -SkipHttpErrorCheck
        $swagger = Invoke-WebRequest "http://127.0.0.1:$Port/swagger-ui.html" -SkipHttpErrorCheck
        $expected = if ($DocsEnabled) { 200 } else { 404 }
        Assert-Condition ($apiDocs.StatusCode -eq $expected) 'Unexpected OpenAPI exposure.'
        Assert-Condition ($swagger.StatusCode -eq $expected) 'Unexpected Swagger UI exposure.'
        foreach ($path in @('/api/not-implemented', '/api/contract/page', '/models/Unet_densenet201_v2_cleaned.onnx', '/actuator/env', '/actuator/configprops')) {
            $missing = Invoke-WebRequest "http://127.0.0.1:$Port$path" -SkipHttpErrorCheck
            Assert-Condition ($missing.StatusCode -eq 404) "Unexpected endpoint or file exposure: $path"
            $missingJson = $missing.Content | ConvertFrom-Json
            Assert-Condition ($missingJson.success -eq $false -and $missingJson.error -eq '资源不存在') 'Non-uniform error response.'
        }
        return [ordered]@{
            name = $Name
            profile = $Profile
            health = [int]$health.StatusCode
            version = $healthJson.version
            readiness = [int]$readiness.StatusCode
            liveness = [int]$liveness.StatusCode
            openapi = [int]$apiDocs.StatusCode
            swagger = [int]$swagger.StatusCode
            unknown_api_is_404 = $true
            test_controller_not_packaged = $true
            model_file_not_public = $true
            status = 'passed'
        }
    } finally {
        $process.Refresh()
        if (-not $process.HasExited) {
            # This object is the process started above, never a process found by port or name.
            Stop-Process -InputObject $process -Force
            $process.WaitForExit(10000) | Out-Null
        }
        $process.Dispose()
    }
}

$sessions = @()
$sessions += Invoke-SmokeSession 'dev-initial' 'dev' $true
$sessions += Invoke-SmokeSession 'dev-restart' 'dev' $true
$sessions += Invoke-SmokeSession 'prod' 'prod' $false
$report = [ordered]@{
    status = 'passed'
    checked_at = [DateTime]::UtcNow.ToString('o')
    java = (& $JavaExecutable --version | Select-Object -First 1)
    jar_sha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    sessions = $sessions
    temporary_application_processes_stopped = $true
}
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $reportDir 'packaged-smoke.json') -Encoding utf8
$report | ConvertTo-Json -Depth 8
