# EFILive V4-style scanner subsystem — implementation status

MonitorTune now contains an independent, definition-driven GM ALDL scanner workflow inspired by the documented EFILive V4 F6/F7/F8 scan-tool model. It is not EFILive software and does not claim direct compatibility with proprietary EFILive V4 vehicle-definition files.

## Implemented in source

- Secure XML importer for the documented MonitorTuneV4 schema, with validation of identifiers, request references, parameter ranges, serial settings and timing.
- Vehicle/module/request models. Requests carry raw TX bytes, module grouping, response offset/length, checksum policy, timeout, inter-byte quiet time, polling interval and a state-change confirmation flag.
- USB serial transport using the existing usb-serial-for-android library, with baud/data/parity/stop configuration.
- Bluetooth Classic SPP raw-UART transport for transparent serial bridges. This is separate from the ELM327 AT-command connection and cannot set the bridge's physical UART baud from Android.
- Optional, definition-supplied connect/disconnect byte sequences for verified interface/chatter control; no default sequence is invented.
- F6-style macro execution from imported definitions, sequential request/response handling, per-step delays and confirmation gating for macros containing state-changing requests.
- F7-style request send and live decoded parameter display, including raw TX/RX frame monitor and checksum status.
- F8-style live dashboard with up to eight definition-scaled parameter dials and a selectable scrolling trend chart.
- Definition-specific DTC read request and one-byte mask/value DTC mapping. DTC clear is available only when the definition supplies a clear request ID and always requires an explicit confirmation dialog.
- Live polling of configured read-only requests at intervals of 100 ms or greater.
- Timestamped decoded session recording and CSV export.
- Unit tests for hex handling, checksums, parameter decoding, schema validation, DTC mapping, serial timing metadata, CSV export and chart history.
- GitHub Actions workflow to run Android unit tests and assemble a debug APK.

## EFILive V4 workflow mapping

| V4 concept | MonitorTune implementation | Status |
|---|---|---|
| Vehicle definitions | MonitorTuneV4 XML import | Supported for the documented MonitorTune schema; proprietary EFILive XML import is not implemented |
| Modules / requests | Module-grouped request definitions | Supported when supplied in the vehicle definition |
| F6 Macros | Sequential request macros with delays | Imported macro execution supported; editing/authoring macros in the app UI is not yet implemented |
| F7 ALDL table | Live request/response decoding | Supported for configured definitions and compatible raw serial interfaces |
| F8 Dashboard | Up to eight live dials plus selectable trend chart | Supported for configured parameter definitions |
| Read DTCs | Configured DTC read request plus byte mask/value rules | Supported only when correct vehicle-specific request and rules are supplied |
| Clear DTCs | Configured clear request | Confirmation-gated; command must be verified for exact ECU |
| Raw serial monitor | TX/RX hex monitor | Supported for configured request traffic |
| Logging / charts | Session CSV and live chart history | Supported for decoded live request traffic |

## Critical hardware/protocol limitations

- EFILive V4 was a legacy GM OBD-I/ALDL scan tool, not an ECU calibration flashing tool.
- Generic ELM327 adapters are not interchangeable with raw 8192-baud ALDL interfaces. Bluetooth SPP mode requires a transparent raw UART bridge. Configure its UART baud externally to match the definition.
- The USB serial library supports common FTDI, CP210x, CH340 and PL2303 USB-UART devices, but this does not prove electrical compatibility with a particular ALDL interface. Check voltage levels, wiring, grounding and adapter design.
- Every request, response layout, checksum coverage, scaling, DTC rule, polling interval and optional chatter sequence must be validated against the exact ECM/service definition. No universal request catalogue is bundled.
- DTC mask/value rules are a simple user-defined one-byte matcher, not a universal GM OBD-I DTC decoder. An unmatched rule or empty response is not proof that no faults exist.
- DTC clear and other state-changing macros can erase diagnostic evidence or learned data. The app requires confirmation but cannot establish that a supplied command is correct or safe.
- No calibration write, flash, immobiliser, coding, actuator-control or ECU programming functionality has been added.

## Validation status

The source and tests have been committed and a GitHub Actions build/test workflow has been added. A successful CI result must be checked before calling the Android build verified. No physical USB ALDL cable, Bluetooth raw-UART bridge or vehicle/ECM has been hardware-tested in this session. Do not rely on the readings until the CI build passes and the exact interface/ECU combination has been bench-tested.

See [V4_DEFINITION_FORMAT.md](V4_DEFINITION_FORMAT.md) and the explicitly non-transmittable [template XML](examples/MonitorTuneV4_template.xml) before preparing a vehicle definition.
