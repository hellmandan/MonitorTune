# MonitorTune Windows — Buick 3800 / OSE $11P V104

Windows HMI source project. Python 3.11 x64, PySide6, pyserial, pyqtgraph.

## Build
Run BUILD_WINDOWS.bat on Windows. Output: dist\\MonitorTune\\MonitorTune.exe.

## ECU link
USB ALDL serial, 8192 baud, 8 data bits, no parity, 1 stop bit. Frame decoder follows the project's existing OSE $11P V104 Mode 1 Message 0 definition (F4 8E 01 + 56 data bytes + checksum). Verify against the exact ADX and a known-good log before using readings for tuning.

## Tabs
ENGINE: live telemetry HMI. DTC: diagnostic panel; read/clear actions remain disabled until the exact $11P DTC request/response sequence is validated. CALIBRATION: 64 KiB BIN VE table editor using the table addresses and axes in the Android Core.kt project. Saves a modified BIN copy; it does not flash/write the ECU.

Values unavailable from the ECU frame are shown as unavailable. AFR is only shown as valid when the wideband channel is configured and its raw value is plausible. Turbo/valvetrain visual motion is an illustrative synchronised animation, not a physical simulation or measured component speed.
