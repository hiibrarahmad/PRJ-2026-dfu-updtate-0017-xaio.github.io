param(
  [int]$Port = 8123
)

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$requirements = Join-Path $repoRoot "tools\release_dashboard\requirements.txt"
$app = Join-Path $repoRoot "tools\release_dashboard\app.py"

python -m pip install -r $requirements
python $app --host 127.0.0.1 --port $Port
