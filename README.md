# MonitorTune

Android automotive diagnostics and tuning workbench.

**Current imported source snapshot:** MonitorTune v5.0 — TunerPro Workbench  
**Primary platform:** Android / Kotlin  
**Package namespace:** `com.dan.ose11p`

## Project focus

MonitorTune is intended to provide a workshop-oriented interface for vehicle diagnostics, live data capture, logging and calibration analysis. GM Delco / OSE $11P is the specialist development focus, with generic OBD-II and a modular path for additional vehicle families.

## Included in the v5.1 source snapshot

- Android application project using Gradle Kotlin DSL.
- USB/serial and ELM327 communication components.
- Vehicle profiles and support catalogue.
- Protocol support definitions and diagnostics helpers.
- Session logging, alerts and AI-assisted diagnostic helper.
- Dyno calculation and workshop engine utilities.
- TunerPro XDF definition import/inspection utilities.
- Unit tests for workshop engine and TunerPro definition components.
- Changelogs documenting the v2–v5 development snapshots.

## EFILive V4-style ALDL scanner development

The Android app now includes a separate V4 Lab workflow:
- USB raw serial and Bluetooth Classic SPP raw-UART connections (not ELM327).
- Importable MonitorTuneV4 vehicle/module/request definitions.
- F6-style configured macro execution, F7-style live request decoding, and F8-style live gauges/trend chart.
- Raw TX/RX monitor, configurable polling/response timing, definition-specific DTC read/clear, and CSV session logging.

This is an independent scanner implementation, not an EFILive product or a drop-in replacement. It does not yet parse proprietary EFILive V4 definition XML. It requires a verified definition for the exact ECM and compatible raw ALDL interface. DTC clear and state-changing macros are confirmation-gated. No ECU flashing or calibration writes are included.

Read [the V4 definition format and setup guide](docs/V4_DEFINITION_FORMAT.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md) before use. The example XML is intentionally non-transmittable and contains a placeholder byte only.

## Important compatibility and safety notes

This repository is a development snapshot, not a claim of universal vehicle compatibility.

- An ELM327 adapter does not make every manufacturer-specific diagnostic service available.
- Generic OBD-II functions must be treated separately from enhanced OEM diagnostics.
- Protocol/catalogue entries may be definitions or framework support rather than verified end-to-end implementations.
- XDF inspection/import is not equivalent to complete ADX/XDF compatibility or calibration writing.
- ECU flashing/writing is not represented as supported unless transport, checksum, recovery and hardware validation have been implemented and tested.
- Delco ECM operating systems and definition files must be matched carefully; $11P, $12P and other Delco variants are not interchangeable by assumption.

## Build

Open the Android project in Android Studio and allow Gradle sync. The project uses the Gradle wrapper configuration in the repository where available. Build and run on a test device before connecting to a vehicle.

Hardware testing should be performed with the vehicle safely secured, suitable interface hardware, and a recovery plan. Do not perform experimental writes to a live ECU.

## Development status

See [IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) for the distinction between included code, partial/framework components and features that still require validation.

## License

No license has been assigned yet. All rights remain with the copyright holder until a license is added.
