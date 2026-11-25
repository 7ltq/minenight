$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
python scripts\build.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
python scripts\export-standalone.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
