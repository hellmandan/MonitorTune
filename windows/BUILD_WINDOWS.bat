@echo off
setlocal
py -3.11 -m venv .venv
call .venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
pyinstaller --noconfirm --clean --windowed --name MonitorTune --collect-all PySide6 --collect-all PySide6.QtWebEngineCore --collect-all PySide6.QtWebEngineWidgets --collect-all pyqtgraph --add-data "engine_scene.html;." main.py
echo.
echo Build output: dist\MonitorTune\MonitorTune.exe
pause
