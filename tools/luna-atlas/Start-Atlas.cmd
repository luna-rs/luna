@echo off
setlocal
set "ATLAS_PYTHON=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\pythonw.exe"
if not exist "%ATLAS_PYTHON%" (
  echo Luna Atlas needs Python 3.12 with Pillow. The bundled Codex runtime was not found.
  echo Run launch.py using your Python installation after installing requirements.txt.
  pause
  exit /b 1
)
start "" "%ATLAS_PYTHON%" "%~dp0launch.py"
