MONITORTUNE v3.0 — WORKSHOP EDITION
===================================

This release adds a visible Workshop tab with persistent vehicle profiles, profile navigation, add/delete controls, ECU capability/status cards and exportable plain-text diagnostic reports. Reports include the selected profile, latest generic OBD-II VIN/link information, stored/pending/permanent DTCs supplied by the connected ECU, and sensor plausibility findings.

New diagnostic core:
- StandardPidDecoder: additional selected SAE Mode 01 PID decoding. Unsupported PIDs remain unavailable.
- DtcDecoder: two-byte SAE DTC decoding with stored/pending/permanent status labels.
- EcuRegistry: explicit capability registry. Only the existing GM Delco $11P V104 profile is identified as the project-specific calibration path; 808, $12P and OEM-enhanced entries are placeholders requiring validated definitions.
- TuneGuard: fail-closed preflight for compatible 64 KB $11P BIN editing, checksum/OS verification, backup, calibrated wideband and independent knock monitoring.
- DynoMath: validated longitudinal power/torque estimate with explicit low-confidence warnings.
- DiagnosticReportText: workshop report generation.
- JUnit tests for core decoding, guard rails and dyno calculations.

Workshop tab use:
1. Open Workshop and add a vehicle profile.
2. Fill make/model/year, engine, ECU/calibration, fuel, mass and modification notes.
3. Connect to the vehicle and wait for live readings / DTC polling.
4. Generate Diagnostic Report and choose where to save the TXT report.

The report reflects only data actually supplied by the connected path. At present the standard DTC/VIN capture is from generic OBD-II; an ALDL session will not magically provide OBD-II VIN/DTCs. Profiles are stored locally in app-private storage.

BUILD / TEST
------------
- Android Studio: open this project folder, sync Gradle, run app on Android 7.0+.
- Run app unit tests: app > Tasks > verification > testDebugUnitTest.
- This source handoff was not built into an APK here because no Android SDK/Gradle wrapper was available in the execution environment.

MONITORTUNE v2.0 — UPGRADE NOTES
================================

This release retains the original OSE $11P V104 USB ALDL tuning workflow and adds
an expanded generic OBD-II diagnostic layer for Bluetooth Classic ELM327 adapters.
The two connection modes remain deliberately separate: generic OBD-II data must
never be used to edit the OSE BIN tables.

NEW IN v2.0
-----------
- OBD-II stored (Mode 03), pending (Mode 07) and permanent (Mode 0A) DTC retrieval.
- DTC lists are refreshed periodically during an ELM327 session and shown in the
  AI/diagnostic readout, separately by status.
- VIN request using Mode 09 PID 02 (single-frame responses supported by this parser).
- Freeze-frame service response check (Mode 02 PID 02 frame 00); this is a support
  check only, not a complete decoded freeze-frame viewer.
- Existing protocol auto-detection and supported-PID discovery retained.
- App version updated to 2.0.

DIAGNOSTIC LIMITATIONS — READ BEFORE USE
---------------------------------------
- Generic OBD-II is emissions-related standard data only. It is not universal
  manufacturer enhanced diagnostics, ABS/SRS/body module scanning, bidirectional
  controls, coding, or programming.
- ELM327 adapter clones vary substantially. Slow or non-compliant adapters can
  time out, return malformed data, or interrupt live polling.
- VIN parsing currently supports a single-frame Mode 09 PID 02 response. Some
  vehicles require ISO-TP multi-frame assembly and may show VIN unavailable.
- Freeze-frame currently checks whether a response is received; it does not yet
  decode the freeze-frame PID set into labelled values.
- DTC responses that are unsupported, blocked, or malformed appear as no codes;
  this must not be interpreted as proof that the vehicle has no faults.
- DTC descriptions are not yet vehicle/manufacturer-specific. A code identifier
  is not a diagnosis; verify with OEM service information before repairs.
- No automatic code clearing is performed. No ECU write, adaptation reset,
  actuator test, or coding command is sent by this upgrade.
- Standard PID support depends on the ECU. A PID missing from the supported list
  must be treated as unavailable, not inferred from another sensor.
- Generic OBD-II mode intentionally disables OSE VE Fill and Knock/Spark BIN edits.

RETAINED OSE $11P FUNCTIONALITY
-------------------------------
USB ALDL remains the dedicated OSE $11P V104 3-bar path: 8192 baud, original frame
layout, logging, VE Fill, Knock/Spark and BIN import/export are preserved. Only use
those table editors with a verified compatible 64 KB OSE BIN. Keep an untouched
backup and validate every change on a suitable dyno with independent wideband and
knock monitoring. The app does not replace a professional calibration process.

OSE $11P V104 3 bar - Android (FTDI USB, OTG)

BUILD: Android Studio -> Open this folder -> let Gradle sync (accept any plugin/Gradle upgrade prompt) -> Build > Build APK(s).
Install the APK, plug FTDI ALDL cable via USB-OTG adapter, tap Connect, allow USB permission.
Tick "Simulate" to test the screens with no cable.

Tabs: Log (Start Log / Stop Log + Save -> choose CSV or TSV, then Android file dialog), Dash (RPM, km/h, boost psi, coolant, spark),
VE Fill (Load 64 KB BIN -> drive -> Write BIN), Knock/Spark (retard-only correction; load VE-tuned BIN to combine).

Same protocol, offsets, tables and rules as the Windows version (see its README):
8192 baud 8N1, request F4 57 01 00 B4, Mode 1 Message 0, 60-byte frame.
VE: 20-100kPa @0x6E06 (25x12) and Boost @0x6F35 (25x11), VE% = raw x 0.390625.
Spark: 20-100kPa @0x7534+0x7613 (20x11, Econ/Power copies) and Boost @0x76F2+0x77D1 (20x11), deg = raw x 0.351565.
Keep the app open while logging (screen stays on); no background service.
XLSX export is not included on Android.

UI (v1.1): dark theme; animated arc gauges on Dash (smooth needle, colour shifts to red zone: RPM from 6000, boost from 24 psi,
coolant from 105 C); Log tab tiles with Wideband AFR colour (green <0.3, amber <0.8, red >=0.8 AFR from commanded);
VE and Knock/Spark tabs are colour heat maps, cells flash when updated; cyan border = sampled VE cell, red border = proposed retard.
Tabs fade/slide in, buttons press-animate, status dot pulses when connected, Log button pulses while recording.

Diagnostics (v1.1): log has a WBRaw column (raw wideband byte, AFR = raw/10, no clamping); link auto re-syncs after 5 missed frames.

VE Fill options: "Fill gaps" spreads measured corrections to nearby unsampled cells (inverse-distance weighted, radius 3);
"Smooth" (50%) blurs touched cells over both VE tables as one grid so the 100 kPa seam has no step. Far cells are never changed.

Capture window: logging and VE sampling run only while wideband AFR is 11-18 (Cfg.WB_MIN / WB_MAX in Core.kt). Outside it the Log tab
shows PAUSED and nothing is recorded (Time_s keeps running); capture resumes automatically. Knock/Spark sampling is unaffected.

Min hits: default 3, adjust with the -/+ buttons in the VE tab. Cells with fewer hits still feed Fill gaps at reduced weight.

AI ENGINE DOCTOR (v1.2)
----------------------
New AI Doctor tab provides continuous on-device monitoring from each decoded ALDL sample.
It checks coolant temperature, boost pressure, wideband AFR under boost, knock retard,
engine speed and sample freshness. Alerts are displayed as CRITICAL (red), CAUTION (amber)
or INFO (blue). Thresholds are editable with Alert Settings and are saved on the device.
The monitor continues to run while other tabs are selected. If ALDL data stops for more
than 1.5 seconds, the screen explicitly marks data as stale; old alerts must not be treated
as current readings.

IMPORTANT LIMITATIONS / SAFETY
- Live warnings remain an offline deterministic rule-based monitor and do not require internet.
- Optional Gemini diagnosis is available from AI Doctor using the Set Gemini API Key and
  Analyse Live Data buttons. It sends one current ALDL snapshot and current local alerts to
  Google's Gemini generateContent API only when Analyse Live Data is pressed; it does not
  continuously stream telemetry or run Gemini on every ALDL frame.
- The Gemini API key is held in app memory only for the current app session and is not written
  into the APK or preferences. It must be entered again after the app is closed. For a public
  or distributed app, replace direct client API access with an authenticated backend/proxy;
  a key entered on a device is still exposed to that device and should be restricted/rotated.
- Gemini output is advisory only and may be wrong. It cannot prevent engine damage, control the ECU, or modify
  BIN/fuel/spark tables. It must not replace a calibrated wideband, proper knock validation,
  mechanical gauges, dyno testing or driver judgement. Stop safely if a serious warning occurs.
- Default alert thresholds are generic starting values, NOT safe tuning targets. Review and
  set them for the actual engine, fuel, sensors, boost control and mechanical limits before use.
- Current ALDL decoder does not provide verified fuel pressure, oil pressure, IAT or battery
  voltage. These are explicitly not monitored by this release. Additional verified ALDL offsets
  or external sensor hardware would be needed to add them.
- Validate the ALDL frame offsets/scales and wideband calibration against the exact $11P V104
  ADX definition and installed wideband controller. A plausible-looking value is not proof of
  sensor accuracy.


MULTI-VEHICLE GENERIC OBD-II — BLUETOOTH ELM327
================================================
The app now offers two separate vehicle connection modes from the Connect button:
1) USB ALDL — the original OSE $11P V104-specific functions (VE Fill, Knock/Spark, ALDL logging).
2) Bluetooth ELM327 — generic SAE J1979 / OBD-II live data for vehicles that expose the standard emissions-related OBD-II services.

Bluetooth setup:
- Use a Bluetooth Classic ELM327 adapter with the Serial Port Profile (SPP / RFCOMM).
- Pair it first in Android Settings. Common adapter PINs are 1234 or 0000 (check the adapter label/manual).
- Enable Bluetooth, tap Connect, select Bluetooth ELM327, grant the Android Nearby devices permission, then select the paired adapter.
- The app sends ELM initialisation commands and ATSP0 to request automatic vehicle protocol detection. It then reads the ECU's supported PID bitmaps and polls only PIDs reported as supported.
- The protocol reported by ATDPN is shown in the connection status / AI Doctor panel.

ELM327 standard protocol families negotiated by ATSP0 (adapter/vehicle dependent):
- SAE J1850 PWM and VPW
- ISO 9141-2
- ISO 14230-4 KWP2000 (slow init and fast init)
- ISO 15765-4 CAN (11-bit / 29-bit identifiers, 250 / 500 kbit/s)
- SAE J1939 where supported by the particular ELM-compatible adapter

Generic live PIDs queried when the vehicle advertises them:
- Engine RPM (01 0C), road speed (01 0D), coolant temperature (01 05)
- Intake manifold pressure (01 0B), intake air temperature (01 0F), throttle position (01 11)
- Timing advance (01 0E), calculated load (01 04), MAF (01 10)
- Barometric pressure (01 33), control module voltage (01 42), fuel rail pressure (01 23)

Generic OBD-II capabilities:
- Live readings are shown in AI Doctor; unavailable PIDs are shown as N/A rather than being presented as measured zero values.
- Generic OBD-II logs can be saved as CSV or TSV. Unsupported values are written as NaN.
- Gemini can analyse a generic OBD-II snapshot. It is told which values are missing and must not assume manufacturer-specific data exists.
- Generic live alerts are deliberately limited to broadly interpretable values such as coolant temperature, reported module voltage and intake temperature. Universal OBD-II does not provide a dependable generic wideband AFR or knock-retard PID, so those OSE-specific alerts are disabled in ELM mode.

IMPORTANT COMPATIBILITY LIMITS:
- This is generic standards-based OBD-II, not literally every make/model/module/protocol. A vehicle must implement compatible standard OBD-II services and expose the requested PIDs. Standard OBD-II generally covers powertrain/emissions data, not every ABS, airbag, transmission, body, EV battery or manufacturer-specific module.
- OEM-enhanced diagnostics, proprietary data PIDs, coding, adaptations, immobiliser functions and module programming require make/model-specific protocol definitions and often a more capable interface/software. They are not included by this generic ELM327 mode.
- A standard ELM327 interface does not support every modern network. Many inexpensive clones have incomplete command sets, poor timing or incorrect protocol claims. Some newer vehicles use CAN-FD or Ethernet diagnostics and need a different interface; this app's ELM mode is Bluetooth Classic SPP, not BLE-only GATT.
- No DTC read/clear workflow is included in this build. Gemini analysis is based on the live Mode 01 snapshot only, not stored/pending/permanent trouble codes.
- Fuel rail pressure PID 0x23 is queried only when advertised; it is not the same as every vehicle's low-side or direct-injection rail pressure measurement. Oil pressure is not a universal standard PID.
- Hybrid/EV high-voltage battery, inverter and charging-system diagnostics are not included.
- The existing VE Fill and Knock/Spark table editing remains specific to the OSE $11P BIN and must not be used as a generic ECU tuner.

Safety and security:
- Generic ELM live data is read-only in this release. No ECU writes, coding or control commands are sent.
- Gemini is on-demand, advisory only. It does not receive a continuous telemetry stream and cannot prevent mechanical damage.
- Confirm values with a capable professional scan tool and calibrated instruments. Do not treat missing PIDs or an absence of alerts as proof of vehicle health.
- The Gemini API key is session-only in the app. For a distributed product, use a secured backend/proxy and per-user authentication; never ship a shared API key inside an APK. Rotate any API key previously pasted into chats or otherwise exposed.

Build / test note:
- Project source was updated and the standalone ELM327 transport file passed a Kotlin compiler syntax/type check against Bluetooth API stubs.
- A full Android Gradle build and physical ELM327/vehicle test have not been performed in this environment. Build in Android Studio and test with a known-good adapter and vehicle before relying on readings.


MONITORTUNE UI UPDATE (v1.4)
- App renamed MonitorTune. Engine-bay photo is the background (res/drawable-nodpi/bg_engine.jpg) under a dark scrim; cards are semi-transparent.
- Dash is the home screen (first tab, selected on launch).
- Rotation: portrait and landscape (follows the phone's rotation lock). Layouts re-flow on rotate without restarting the link or log.
  Portrait: Dash = RPM full width + 2x2 gauges; Log tiles 2 per row. Landscape/tablet: 5 gauges in a row, 4 tiles per row.
- Bottom navigation bar (scrollable), larger touch targets, two-row header.
- AI Doctor: animated pulsing core, expanding rings, rotating scan arc and heartbeat trace (colour = status; flat/grey when no live data;
  fast sweep while Gemini is analysing); alert cards slide in; "Analysing..." text animates.
- Not compiled in the editing environment: build in Android Studio and test on a device.


MONITORTUNE v1.5 - REALISTIC GAUGES + VISUAL / AUDIO ALERTS
- Gauges: chrome bezel, glass face with reflection, tick marks that light up to the needle, red zone, tapered needle with
  shadow and hub, spring-damped needle (slight overshoot), start-up full-scale sweep, peak-hold marker (4 s), red-zone alarm glow.
  RPM scale is x1000. Background engine-bay photo slowly drifts/zooms.
- Alert engine (Alerts.kt): alert must persist 0.4 s to be raised and is held 1.5 s after clearing (no flapping).
  CRITICAL: pulsing red screen-edge glow, flashing banner, urgent triple beep every 1.5 s, spoken once ("Warning. <alert>"), vibration.
  CAUTION: amber breathing glow + banner, two-tone chime on onset, repeated every 12 s while present.
  ECU DATA LOST (caution level): descending three-note tone + spoken - live data stopped, on-screen values are NOT current.
  SILENCE button on the banner acknowledges everything currently active; any NEW alert sounds again.
  Header "Sound ON / Muted" toggles tones + voice. AI Doctor tab: Voice and Vibrate options, Test caution / Test critical buttons.
  AI Doctor nav button flashes when an alert is active and another tab is open. Log tab tiles (coolant, boost, knock) colour by thresholds.
  Alert thresholds are still the Alert Settings in AI Doctor. "High engine speed" is shown on the RPM red zone and AI Doctor card only (no banner/sound).
- Audio uses the ALARM volume stream (independent of media volume, still plays through the phone speaker). If alarm volume is 0 the app warns on connect.
- Critical glow pulses at about 1.4 Hz (under the 3 Hz flash-safety guideline).
- Limits: alerts only run while the app process is alive (no background service); audio/visual alerts are advisory and do not control the ECU.
  Not compiled in the editing environment: build in Android Studio, then use Test caution / Test critical and Simulate to check.


MONITORTUNE 2.1 DEVELOPMENT ADDITIONS
====================================
New source components:
- ProtocolSupport.kt: ELM response frame parsing, ISO-TP response reassembly and validated VIN decoding.
- DtcCatalog: generic common-code descriptions with explicit manufacturer-specific fallbacks.
- VehicleProfiles.kt: persistent JSON vehicle profiles including ECU, engine and modification notes.
- SessionLogger.kt: timestamped CSV live-data logging and broad generic sensor plausibility warnings.

These are foundation components for the next UI integration pass. VehicleProfiles and SessionLogger are not yet exposed
as screens or controls. The ISO-TP helper reassembles frames already surfaced by the ELM adapter; it is not a universal
raw-CAN stack. Generic OBD-II support is limited to PIDs actually supported by the vehicle and adapter. GM Delco $11P
ALDL tuning remains a separate vehicle/ECU-specific path and must not be treated as generic OBD-II.

Before release: build with Android Studio, run instrumentation tests on supported Android versions, test USB ALDL with
the actual interface and $11P ECU, and test ELM327 adapters against known-good OBD-II vehicles. Verify permissions,
Bluetooth reconnects, multi-frame VINs, DTC parsing and CSV exports. Do not use road-dyno estimates on public roads.


MONITORTUNE 4.0 — DELCO SPECIALIST + MULTI-MAKE CATALOGUE
========================================================
The Workshop tab now contains a structured GM Delco family catalogue and a multi-manufacturer support matrix.

GM Delco entries:
- OSE $11P V104: existing project-specific 8192-baud ALDL decoder and offline 64 KiB BIN table tools.
- Delco 808: family catalogue only; no universal definition is bundled.
- Delco $12P: family catalogue only; no universal definition is bundled.
- Other Delco/GM controllers: locked until controller-specific protocol and definition data are verified.

The manufacturer matrix covers GM/Holden/Chevrolet, Ford, Toyota/Lexus, Nissan/Infiniti, Mazda, Subaru, BMW/MINI, Mercedes-Benz, Volkswagen Group, Hyundai/Kia, Volvo and Honda/Acura. These entries indicate generic SAE OBD-II read-only as the baseline only where the vehicle and adapter support it. They do not claim full OEM diagnostic coverage.

No OEM enhanced scan, ABS/SRS/TCM/BCM access, EV battery access, coding, adaptations, immobiliser functions or ECU flashing are enabled by adding a manufacturer profile. These require verified vehicle-specific definitions, suitable hardware, service information and safe implementation.

Definition matching is fail-closed. This source package does not include a trusted release SHA-256 allowlist for proprietary definition packages; therefore a fingerprint string cannot unlock ECU-specific writes. Existing OSE BIN editing remains an offline file operation and is not ECU flashing.

Version 4.0 has not had a full Android Gradle build or physical vehicle/adapter test in this environment. Build in Android Studio, run unit tests, then validate each transport with a known-good interface and the exact ECU before relying on it.

MONITORTUNE v5.0 — TUNERPRO / CALIBRATION WORKBENCH
---------------------------------------------------
Open Workshop and use Import XDF or Inspect ADX. Choose the relevant TunerPro XML
file. The importer lists recognised metadata and safety warnings. XDF table preview
is currently a conservative raw-byte preview only and is not an interpreted or
editable calibration table. It requires a manually confirmed BIN file offset.

ADX import is an index of common XML metadata nodes. It does not replace the existing
OSE $11P V104 ALDL decoder or automatically build a live packet decoder.

The AFR correction advisor is a review-only calculation engine. It requires at least
five samples, restricts input ranges and caps a suggested correction to +/-20%. The
advisor is not connected to automatic BIN writing. Confirm stable steady-state data,
wideband calibration, fuel pressure and knock safety before acting on any suggestion.

No live ECU flashing, live calibration write, or generic manufacturer-specific
programming support is introduced in v5.0. The exact ECU/OS/definition/checksum and
hardware must be validated before adding such capabilities.