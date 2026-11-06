param(
    [string]$Server = 'localhost'
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
    throw 'sqlcmd is required. Install SQL Server command-line utilities first.'
}

& sqlcmd -S $Server -E -C -b -f 65001 -i (Join-Path $PSScriptRoot 'sqlserver/00-create-database.sql')
if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }

& sqlcmd -S $Server -E -C -b -f 65001 -i (Join-Path $PSScriptRoot 'sqlserver/01-schema.sql')
if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed.' }

Write-Host 'SQL Server database and six core tables are ready.'
