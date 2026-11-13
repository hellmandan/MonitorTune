@echo off
setlocal
py -3.11 -m venv .venv
call .venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
pyinstaller --noconfirm --clean --windowed --name MonitorTune --collect-all PySide6 --collect-all PySide6.QtWebEngineCore --collect-all PySide6.QtWebEngineWidgets --collect-all pyqtgraph --collect-all pyttsx3 --add-data "engine_scene.html;." main.py
if not exist "dist\MonitorTune" mkdir "dist\MonitorTune"
copy /Y "engine_scene.html" "dist\MonitorTune\engine_scene.html"
if exist "dist\MonitorTune\_internal" copy /Y "engine_scene.html" "dist\MonitorTune\_internal\engine_scene.html"
if not exist "dist\MonitorTune\engine_scene.html" exit /b 1
echo.
echo Build output: dist\MonitorTune\MonitorTune.exe
pause
