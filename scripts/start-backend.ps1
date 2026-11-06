param([switch]$InitializeAdmin, [switch]$EnableMockOrders)

$ErrorActionPreference = 'Stop'
$backendPort = 8080
if ($env:SERVER_PORT) {
    if (-not [int]::TryParse($env:SERVER_PORT, [ref]$backendPort) -or $backendPort -lt 1 -or $backendPort -gt 65535) {
        throw 'SERVER_PORT must be an integer between 1 and 65535.'
    }
}
$backendListeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
if ($backendListeners.Port -contains $backendPort) {
    throw "Backend port $backendPort is already in use. Stop the existing backend (Ctrl+C in its terminal) before starting again. To identify it, run: Get-NetTCPConnection -LocalPort $backendPort -State Listen | Select-Object OwningProcess"
}
$previousUsername = $env:BOOTSTRAP_ADMIN_USERNAME
$previousPassword = $env:BOOTSTRAP_ADMIN_PASSWORD
$previousMockOrders = $env:MOCK_ORDERS_ENABLED
if ($EnableMockOrders) { $env:MOCK_ORDERS_ENABLED = 'true' }
if ($InitializeAdmin) {
    $env:BOOTSTRAP_ADMIN_USERNAME = Read-Host 'First admin username (3-50 letters, digits, _, ., -)'
    $securePassword = Read-Host 'First admin password (at least 10 characters)' -AsSecureString
    $env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
}
Push-Location (Join-Path $PSScriptRoot '../backend')
try {
    & mvn.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error. See the Spring Boot error above (APPLICATION FAILED TO START or Caused by) for the cause.' }
} finally {
    $env:BOOTSTRAP_ADMIN_USERNAME = $previousUsername
    $env:BOOTSTRAP_ADMIN_PASSWORD = $previousPassword
    $env:MOCK_ORDERS_ENABLED = $previousMockOrders
    Pop-Location
}
