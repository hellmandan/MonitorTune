# Implementation and validation status

This document deliberately separates code present in the v5.0 source snapshot from claims that would require further implementation or hardware testing.

## Present in the snapshot

- Android/Kotlin application structure and UI.
- ELM327 link implementation.
- Session logging and vehicle profile components.
- Protocol support/catalogue classes.
- Workshop engine, alerts, dyno and diagnostic helper components.
- TunerPro definition parsing/inspection utilities.
- Unit tests for selected workshop and TunerPro components.

## Partial or requiring deeper verification

- Manufacturer catalogue breadth does not establish that every model, ECU or enhanced diagnostic service is supported.
- Protocol definitions may provide scaffolding without a complete physical transport and ECU service implementation.
- XDF support must be checked against real-world definition files, including address handling, bit width, signedness, endianness, scaling, axes and bounds.
- ADX acquisition/decoding and live-definition compatibility require separate validation.
- AI-generated diagnostic advice is advisory and must not replace measured diagnosis or service information.

## Not to be represented as complete without further work

- Universal all-makes/all-models diagnostics.
- Universal Bluetooth ELM327 compatibility.
- Complete TunerPro XDF/ADX compatibility.
- ECU calibration writing or flashing.
- Verified operation across every listed protocol and vehicle family.

## Next engineering gates

1. Run a clean Android Studio/Gradle build and record the exact toolchain.
2. Run all unit tests and add parser/transport regression tests.
3. Validate ELM327 connection and PID handling against known-good adapters and vehicles.
4. Validate OSE $11P data definitions against the exact ECM OS and matching ADX/XDF files.
5. Test logging, reconnect, timeout and malformed-frame handling.
6. Keep any ECU write capability disabled until a separately reviewed implementation has checksums, recovery handling and bench-tested validation.
