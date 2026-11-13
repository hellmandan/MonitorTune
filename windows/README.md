# MonitorTune Windows — Buick 3800 / OSE $11P V104

Windows HMI source project. Python 3.11 x64, PySide6 / Qt WebEngine, pyserial and pyttsx3. The interactive 3D renderer loads the Three.js module from jsDelivr, so the first engine-scene load requires an internet connection.

## Build
Run BUILD_WINDOWS.bat on Windows. Output: dist\\MonitorTune\\MonitorTune.exe.

## ECU link
USB ALDL serial, 8192 baud, 8 data bits, no parity, 1 stop bit. Frame decoder follows the project's existing OSE $11P V104 Mode 1 Message 0 definition (F4 8E 01 + 56 data bytes + checksum). Verify against the exact ADX and a known-good log before using readings for tuning.

## Tabs
ENGINE: detailed physically lit 3D Buick V6 cutaway (crankcase, cylinder banks, pistons, connecting rods, valve gear, intake/exhaust, turbocharger and charge plumbing), with piston/crank motion driven from live ECU RPM and turbo wheel speed shown as an illustrative RPM-derived effect. DTC: diagnostic panel; read/clear actions remain disabled until the exact $11P DTC request/response sequence is validated. CALIBRATION: 64 KiB BIN VE table editor using the table addresses and axes in the Android Core.kt project. Saves a modified BIN copy; it does not flash/write the ECU.

Values unavailable from the ECU frame are shown as unavailable. AFR is only shown as valid when the wideband channel is configured and its raw value is plausible. The 3D assembly is a detailed procedural visualisation, not a manufacturer CAD model; component geometry, combustion and turbo speed are illustrative. The ALDL RPM drives the reciprocating/rotating animation. Audible safety alerts cover configurable overboost, lean-under-boost, coolant and knock thresholds; these are driver aids, not engine protection or a substitute for a verified tune. The Windows app does not control the engine or ECU outputs.
