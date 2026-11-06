$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '../frontend-pc')
try {
    if (-not (Test-Path 'node_modules')) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
    }
    & npm.cmd run dev
    if ($LASTEXITCODE -ne 0) { throw 'Frontend exited with an error.' }
} finally {
    Pop-Location
}
