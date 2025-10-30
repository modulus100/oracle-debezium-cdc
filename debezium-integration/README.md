# CDC Inbound Workflow (Debezium Embedded + Spring Integration)

This module ingests Oracle CDC events via Debezium Embedded, transforms them, and republishes them to Kafka.

## Components

- **`com.example.cdc.config.DebeziumEmbeddedConfig`**
  - Builds Debezium Oracle connector `Properties`.
  - Configures Kafka producer (`KafkaTemplate<String, String>`) with reliability settings (acks=all, idempotence, retries, etc.).
  - Uses Kafka Connect JSON converters with schemas disabled so only the Debezium payload (envelope) is emitted, not the Connect schema wrapper.

- **`com.example.cdc.flow.CdcInboundFlowConfig`**
  - Defines the Spring Integration `IntegrationFlow` that consumes Debezium engine events.
  - Extracts a Kafka key from the Debezium envelope (`before.ID` for deletes, otherwise `after.ID`).
  - Maps Debezium op codes to a human-friendly operation type (CREATE/UPDATE/DELETE/READ).
  - Wraps the original Debezium payload JSON into a `CdcMessage` record and publishes serialized JSON to Kafka.

- **`com.example.cdc.model.CdcMessage`**
  - Java record: `CdcMessage(String json, String operationType)`.
  - `json` contains the original Debezium envelope (payload-only): `before`, `after`, `source`, `op`, timestamps, etc.
  - `operationType` is derived from Debezium `op`.

## Event shape

Because `JsonConverter` is configured with `schemas.enable=false`, the inbound string is the Debezium payload (no Connect schema wrapper). Example after re-publish:

```json
{
  "json": "{\"before\":{...},\"after\":{...},\"source\":{...},\"op\":\"u\",...}",
  "operationType": "UPDATE"
}
```

Notes:
- For deletes, `after` is `null` and `op == "d"`. The key is extracted from `before.ID`.
- `row_id` is present only on log-mining (non-snapshot) events under `source.row_id` in the inner `json`.

## Kafka topics and keys

- Outbound topic: `cdc.kafka.out.topic` (default: `cdc.out`).
- Dead-letter topic: `cdc.kafka.out.dlt.topic` (default: `cdc.out.dlt`).
- Message key: the table primary key (`ID`) extracted as a string; ensures per-key ordering and stable partitioning.

Recommended topic configs for `cdc.out`:
- `cleanup.policy=compact` (or `compact,delete`) to retain the latest state per key.

## Reliability and DLT

Producer settings (in `DebeziumEmbeddedConfig`):
- `acks=all`, `enable.idempotence=true`, `retries=5`, `delivery.timeout.ms=120000`, `linger.ms=5`.

Failure path:
- The send uses `CompletableFuture.whenComplete(...)`.
- On error, a DLT record is prepared with error headers (`x-error-class`, `x-error-message`, `x-original-topic`).
- The actual DLT publish call is currently commented in `CdcInboundFlowConfig` (uncomment to enable).


Important Debezium connector properties (set in code):
- `decimal.handling.mode=string` so `ID` appears as a plain string (easier key extraction).
- `time.precision.mode=adaptive_time_microseconds`.
- JSON converters to emit only payloads (no Connect schema):
  - `key.converter=value.converter=org.apache.kafka.connect.json.JsonConverter`
  - `key.converter.schemas.enable=false`
  - `value.converter.schemas.enable=false`

## Build & Run

- Build the module:

```bash
./gradlew :debezium-integration:clean :debezium-integration:build -x test
```

- Run your Spring Boot app (from IDE or via `bootRun` if configured).
- Execute DML on the captured Oracle table (e.g., `INSERT/UPDATE/DELETE` on `C##CDC.CUSTOMERS`).
- Consume messages from the out topic:

```bash
kcat -b localhost:29092 -t cdc.out -C -o end
```

## Customization

- **Publish original payload**: send `json` directly instead of the `CdcMessage` wrapper.
- **Add headers**: include `operationType`, `row_id`, etc., as Kafka headers rather than in the value.
- **Include `row_id` in `CdcMessage`**: extend the record and extract `source.row_id`.
- **Switch to unwrap SMT**: If you need only `after` (no `before`), enable Debezium `ExtractNewRecordState` SMT; note this removes `before`.

