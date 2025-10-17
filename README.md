# Debezium Oracle CDC with Kafka Connect

This project sets up a Debezium Oracle CDC (Change Data Capture) pipeline using Kafka Connect, capturing changes from an Oracle database and streaming them to Kafka topics.

## Prerequisites

- Docker and Docker Compose
- Oracle JDBC driver (`ojdbc11.jar`) placed in `connect-plugins/libs/`
- Oracle database (XEPDB1) with LogMiner configured

## Quick Start

### 1. Start the Stack

```bash
docker compose up -d kafka connect oracle kafdrop
```

### 2. Install Debezium Oracle Connector

```bash
# Install Debezium Oracle Connector (3.0.0.Final)
bash scripts/connect/install-debezium.sh 3.0.0.Final
```

### 3. Register the Connector

```bash
# Register the Oracle CDC connector
curl -s -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  --data @connectors/oracle-connector.json
```

### 4. Verify the Setup

- **Check connector status**:
  ```bash
  curl -s http://localhost:8083/connectors/oracle-cdc-connector/status | jq
  ```

- **View CDC events** (in a new terminal):
  ```bash
  docker compose exec -T kafka bash -lc \
    "kafka-console-consumer --bootstrap-server kafka:9092 \
     --topic cdc.customers --from-beginning --property print.key=true"
  ```

- **Web UIs**:
  - Kafka Connect UI: http://localhost:8084
  - Kafdrop: http://localhost:9000

## Configuration

### Connector Configuration (`connectors/oracle-connector.json`)

- **Source Database**:
  ```json
  "database.hostname": "oracle",
  "database.port": "1521",
  "database.user": "C##CDC",
  "database.password": "cdcpass",
  "database.dbname": "XE",
  "database.pdb.name": "XEPDB1"
  ```

- **Kafka Topics**:
  - CDC events: `cdc.<schema>.<table>` (routed via transforms)
  - Schema history: `cdc.schema-history`

- **Transforms**:
  - Routes `C##CDC.CUSTOMERS` → `cdc.customers`

### Environment Variables (in `docker-compose.yml`)

- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1`
- `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1`
- `CONNECT_PLUGIN_PATH: "/usr/share/confluent-hub-components,/connect-plugins"`

## Troubleshooting

### Common Issues

1. **Connector Stuck in "Ensuring Membership"**
   - Ensure internal topics exist:
     ```bash
     docker compose exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic connect_configs --partitions 1 --replication-factor 1 --config cleanup.policy=compact"
     # ... (repeat for connect_offsets, connect_statuses)
     ```
   - Restart Connect:
     ```bash
     docker compose restart connect
     ```

2. **No CDC Events**
   - Verify Oracle LogMiner is configured
   - Check connector logs:
     ```bash
     docker compose logs -f connect | grep -E 'oracle-cdc-connector|ERROR|WARN'
     ```
   - Ensure data changes are made to `C##CDC.CUSTOMERS`

3. **Plugin Not Found**
   - Confirm `ojdbc11.jar` is in `connect-plugins/libs/`
   - Re-run the install script and check logs

## Development

### Update Connector Configuration

1. Edit `connectors/oracle-connector.json`
2. Update the connector:
   ```bash
   curl -s -X PUT http://localhost:8083/connectors/oracle-cdc-connector/config \
     -H "Content-Type: application/json" \
     --data @connectors/oracle-connector.json
   ```

### Reset State

1. Delete the connector:
   ```bash
   curl -X DELETE http://localhost:8083/connectors/oracle-cdc-connector
   ```
2. Delete internal topics if needed:
   ```bash
   docker compose exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --delete --topic connect_configs"
   # ... (repeat for other topics)
   ```
3. Restart the stack:
   ```bash
   docker compose down
   docker compose up -d
   ```

## License

[Your License Here]
