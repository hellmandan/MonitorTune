# MonitorTune V4-style definition format

MonitorTune's current scanner uses its own **MonitorTuneV4** XML interchange format. It does not directly import proprietary EFILive V4 vehicle-definition files. Convert a definition only from information you are authorised to use and can verify against the exact ECM/service data. Do not copy a random request frame from a forum and assume it is universal.

## Minimal example (safe placeholder only)

The TX byte below is deliberately a placeholder, not a valid ECM command. It is marked as state-changing so the app will ask before transmitting. Do not connect or transmit it.

```xml
<MonitorTuneV4
    id="example-template"
    name="Example template - NOT A VEHICLE DEFINITION"
    baud="8192"
    dataBits="8"
    stopBits="1"
    parity="NONE">

    <parameter id="rpm" name="Engine RPM" offset="0" width="2"
               endian="BIG" signed="false" scale="0.25"
               add="0" units="rpm" description="Replace with verified response formula"/>

    <request id="read-data" name="Example request - replace before use"
             module="ECM" tx="00"
             responseOffset="0" responseLength="0"
             checksum="NONE" responseTimeoutMs="1200"
             quietTimeMs="60" pollIntervalMs="0"
             requiresConfirmation="true"/>

    <macro id="example-macro" name="Example macro - replace before use"
           requiresConfirmation="true">
        <step request="read-data" delayMs="100"/>
    </macro>
</MonitorTuneV4>
```

## Definition attributes

### Root
- `id`, `name`: definition identity and display name.
- `baud`: USB serial baud rate. Many GM ALDL applications use 8192, but this must be checked for the ECM/interface.
- `dataBits`, `stopBits`, `parity`: UART settings.
- `connectHex`, `disconnectHex`: optional, explicitly verified byte sequences (for example a vehicle/interface-specific chatter control sequence). These are transmitted when connecting/disconnecting; omit unless verified.
- `dtcReadRequestId`, `dtcClearRequestId`: IDs of the vehicle-specific DTC read and clear requests.

### Parameter
- `id`, `name`, `offset`, `width`: stable ID, label and response byte location. Width is 1–4 bytes.
- `endian`: `BIG` or `LITTLE`.
- `signed`: `true` or `false`.
- `scale`, `add`: decoded value = signed/unsigned raw value × scale + add.
- `units`, `description`: display metadata.\n- Optional `min`, `max`: engineering-unit dial range for the live dashboard; if omitted, the dial uses the full representable raw range after scale/offset.
- Parameter offset is relative to the request's response data start (after `responseOffset`).

### Request
- `id`, `name`, `module`: identity, display label and ECU module group.
- `tx`: exact raw transmit bytes in hexadecimal. No bytes are automatically invented or appended.
- `responseOffset`, `responseLength`: response region used for parameter decoding; zero response length means the remaining received frame.
- `checksum`: `NONE`, `GM_8BIT_SUM` or `GM_8BIT_COMPLEMENT`. Choose only after verifying the exact protocol checksum coverage.
- `responseTimeoutMs`: 50–10000 ms.
- `quietTimeMs`: 5–500 ms of silence after the last byte before the receive frame is returned.
- `pollIntervalMs`: 0 disables polling; live polling requires at least 100 ms. Poll only requests confirmed to be read-only and safe at the chosen rate.
- `requiresConfirmation`: mark any state-changing command `true`. The app asks before individual transmission and before macros containing such requests.

### Macro
- `id`, `name`, `requiresConfirmation`: macro identity and explicit confirmation flag.
- Each `step` references a request ID and has an optional delay in milliseconds (0–60000).
- Macros execute in order on the same serial worker. A macro containing a request marked `requiresConfirmation` must itself be marked for confirmation.

### DTC mapping
- Each `dtc` has `code`, `description`, `offset`, `mask`, and `value`.
- Offset is an absolute byte offset in the received frame. A rule matches when `(frame[offset] & mask) == value`.
- This simple one-byte rule map is not a universal GM OBD-I DTC decoder. Use only if it accurately describes the exact ECU's response encoding. Otherwise retain the raw response and implement a verified decoder.

## Runtime workflow

1. Import a definition.
2. Connect using USB serial or a Bluetooth Classic SPP **raw UART bridge**. Generic ELM327 AT-command adapters are not supported by this raw transport.
3. Select a configured request for one-shot transmit, or start polling a request that has a non-zero interval and is not marked state-changing.
4. View decoded ALDL values and the selected live trend; raw TX/RX bytes remain visible.
5. Read DTCs only if a DTC request and matching DTC rules are defined. Clear DTCs only if a verified clear request ID is defined; a confirmation dialog is always shown.
6. Run imported macros. State-changing macros require explicit confirmation.
7. Export the captured decoded session to CSV.

## Compatibility and safety

- This is an independent scanner implementation, not EFILive V4 software. Proprietary EFILive XML dialect support has not been established.
- USB serial configures the USB-UART chip. Bluetooth SPP cannot configure the remote bridge's physical UART baud from Android; configure the bridge itself to match the definition.
- A successful serial connection does not prove the electrical interface is correct. Verify ALDL voltage levels, wiring, ground, baud and chatter timing.
- Never use an ELM327 adapter in raw SPP mode; it expects AT commands and is not a transparent UART bridge.
- A checksum mismatch, empty response or unmatched DTC rule is not proof of a healthy ECU.
- DTC clear can erase diagnostic evidence/readiness information. Do not use until the command has been validated for the exact controller.
- No calibration write, flash, immobiliser, coding or actuator-control functions are included.
- Hardware validation on a bench and then a known vehicle is still required before relying on the results.
