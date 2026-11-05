# EFILive V4-style scanner subsystem — implementation status

MonitorTune now contains an independent, data-driven ALDL scanner core inspired by the documented EFILive V4 scan workflow. It is not EFILive software, does not use proprietary binaries, and does not claim drop-in compatibility with EFILive V4 vehicle-definition files.

## Implemented in source

- Explicit raw hexadecimal request representation and formatter.
- Configurable GM 8-bit sum and complement checksum helpers; checksum policy is per request and defaults to NONE rather than guessing.
- Data-driven 1–4 byte parameter decoding with endian, signedness, scale and offset.
- A secure XML importer for the documented MonitorTuneV4 interchange schema (not arbitrary EFILive XML).
- Macro model with ordered request steps, delays and an explicit confirmation flag for sensitive macros.
- Raw serial transport interface contract; an Android USB/serial driver adapter and verified per-vehicle timing/framing are still required before live ECU I/O.
- Decoded session recorder with CSV export and bounded chart-history model.
- Unit tests for hex parsing, checksum policies, endian/signed scaling, schema validation, CSV and chart retention.

## EFILive V4 workflow mapping

| V4 concept | MonitorTune implementation | Status |
|---|---|---|
| Vehicle definitions | MonitorTuneV4 XML model/importer | Partial; proprietary V4 XML dialect not reverse-engineered |
| Modules / requests | Explicit request definitions and raw bytes | Core model present; UI and ECU-specific definitions needed |
| F6 Macros | Ordered macro model and confirmation metadata | Core model present; live execution engine/UI pending |
| F7 ALDL table | Parameter decoder and decoded value model | Core present; live transport integration pending |
| F8 Dashboard | Decoded values and bounded chart series | Data model present; dashboard UI pending |
| Read DTCs | Vehicle-specific request/parameter definitions | Not supplied by generic core |
| Clear DTCs | No universal command is assumed | Not enabled; requires verified vehicle-specific definition and explicit confirmation |
| Raw serial monitor | Hex parser/formatter and transport interface | UI/driver integration pending |
| Logging / charts | CSV recorder and bounded chart history | Core present; live capture integration pending |

## Critical hardware/protocol limitations

- EFILive V4 was a legacy GM OBD-I/ALDL scan tool; it is not an ECU calibration flash/tuning interface.
- Generic ELM327 adapters are not interchangeable with raw 8192-baud ALDL interfaces. The actual ECU, ALDL electrical interface, serial baud and chatter/response timing must be verified.
- Do not transmit sample bytes shown in historical discussions as a universal request. Every request must come from a verified vehicle-specific definition.
- DTC clear and other state-changing macros must remain disabled until their exact command sequence is validated on the bench and confirmation is required in the UI.
- No ECU write/flash functionality has been added.

## Validation still required

- Run clean Android Gradle build and all unit tests in Android Studio/CI.
- Test XML parsing on Android API 24+ with malformed and hostile inputs.
- Integrate a compatible raw serial adapter and implement framing, response timeouts, chatter control and reconnect behavior.
- Validate every request, checksum, response offset, scaling and DTC mapping against the exact ECM/service definition.
- Hardware-test logging, macro sequencing and dashboard update rate.

Do not advertise this subsystem as a completed EFILive V4 replacement until those gates pass.
