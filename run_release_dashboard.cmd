@echo off
setlocal

set "REPO_ROOT=%~dp0"
set "REQUIREMENTS=%REPO_ROOT%tools\release_dashboard\requirements.txt"
set "APP=%REPO_ROOT%tools\release_dashboard\app.py"

python -m pip install -r "%REQUIREMENTS%"
python "%APP%" --host 127.0.0.1 --port 8123
