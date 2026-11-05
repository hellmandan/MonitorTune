# MonitorTune — OSE $11P V104 ALDL

A dedicated Android live-data and VE-capture workspace for the user's GM Delco OSE $11P V104 3-bar setup.

## App focus
- USB raw serial ALDL connection only (8192 baud, 8N1).
- OSE $11P Mode 1 Message 0 live data.
- Full-screen VE capture/table workspace.
- Live RPM, boost and coolant gauges.
- IAC gauge placeholder is shown but intentionally marked unavailable until the exact IAC byte offset and scaling are verified against the matching ADX definition.
- Existing engine alarms, warnings, audible alerts and mute control remain.
- VE BIN loading, sampling, fill/smoothing and export remain available.

## Not included in the user interface
Generic OBD-II / ELM327, other vehicle makes, multi-vehicle profiles, workshop tools, spark table workspace, dyno tools, AI/Gemini diagnosis and the separate V4 lab are removed from the app navigation. The connection action is restricted to the USB ALDL link.

## Protocol and safety
The live link uses the OSE $11P V104 3-bar Mode 1 Message 0 frame definition in `Core.kt`. Do not use this build with a different ECM/OS without checking the protocol and data layout. No live ECU calibration writing or flashing is implemented. VE changes are exported as a BIN file for separate verification and programming.

The IAC display is not populated with a guessed byte. Supply the exact matching `OSE_$11P V104 3Bar.adx` definition to map its correct field and scaling.

## Build
Open `OSE11P_Android` in Android Studio, sync Gradle and build the debug APK. Test the serial interface and all alarm thresholds on the vehicle before relying on live warnings.
