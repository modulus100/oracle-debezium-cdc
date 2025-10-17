# Debezium Oracle CDC with Kafka Connect

This project sets up a complete Debezium Oracle CDC (Change Data Capture) pipeline using Kafka Connect. It captures changes from an Oracle database and streams them to Kafka topics.

## First-Time Setup Guide

### 1. Prerequisites

- Docker and Docker Compose
- Oracle account (to download JDBC driver)
- At least 4GB of free memory for containers

### 2. Download Required Files

#### 2.1 Download Oracle JDBC Driver

1. Download the Oracle JDBC driver (ojdbc11.jar) from:
   https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html
2. Place it in the `connect-plugins/libs/` directory:
   ```bash
   mkdir -p connect-plugins/libs
   cp ~/Downloads/ojdbc11.jar connect-plugins/libs/
   ```

#### 2.2 Download Debezium Oracle Connector

```bash
# Create directory for the connector
mkdir -p connect-plugins/debezium-oracle-connector

# Download Debezium Oracle Connector (3.0.0.Final)
curl -L -o debezium-connector-oracle-3.0.0.Final-plugin.tar.gz \
  https://repo1.maven.org/maven2/io/debezium/debezium-connector-oracle/3.0.0.Final/debezium-connector-oracle-3.0.0.Final-plugin.tar.gz

# Extract the connector
tar -xzf debezium-connector-oracle-3.0.0.Final-plugin.tar.gz -C connect-plugins/debezium-oracle-connector --strip-components=1

# Clean up
rm debezium-connector-oracle-3.0.0.Final-plugin.tar.gz
```

### 3. Start the Stack

```bash
# Start all services
docker compose up -d kafka connect oracle kafdrop
```

### 4. Verify the Installation

1. **Check Kafka Connect health**:
   ```bash
   curl -s http://localhost:8083/ | jq
   ```

2. **Verify connector plugin is loaded**:
   ```bash
   curl -s http://localhost:8083/connector-plugins | jq '.[].class' | grep -i oracle
   ```
   Should return: `"io.debezium.connector.oracle.OracleConnector"`

### 5. Register the Connector

```bash
# Register the Oracle CDC connector
curl -s -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  --data @connectors/oracle-connector.json

# Check connector status
curl -s http://localhost:8083/connectors/oracle-cdc-connector/status | jq
```

### 6. Verify CDC is Working

1. **View CDC events**:
   ```bash
   docker compose exec -T kafka bash -lc \
     "kafka-console-consumer --bootstrap-server kafka:9092 \
      --topic cdc.customers --from-beginning --property print.key=true"
   ```

2. **Check Web UIs**:
   - Kafka Connect UI: http://localhost:8084
   - Kafdrop: http://localhost:9000

## Configuration Details

### Connector Configuration (`connectors/oracle-connector.json`)

```json
{
  "name": "oracle-cdc-connector",
  "config": {
    "connector.class": "io.debezium.connector.oracle.OracleConnector",
    "tasks.max": "1",
    "topic.prefix": "cdc",
    "database.hostname": "oracle",
    "database.port": "1521",
    "database.user": "C##CDC",
    "database.password": "cdcpass",
    "database.dbname": "XE",
    "database.pdb.name": "XEPDB1",
    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
    "schema.history.internal.kafka.topic": "cdc.schema-history",
    "table.include.list": "C##CDC.CUSTOMERS",
    "snapshot.mode": "initial",
    "log.mining.strategy": "online_catalog",
    "include.schema.changes": "false",
    "tombstones.on.delete": "false"
  }
}
```

## Common Issues

### 1. Connector Fails to Start
- **Symptom**: Connector stays in `UNASSIGNED` or `FAILED` state
- **Solution**:
  ```bash
  # Check Connect logs
  docker compose logs -f connect | grep -E 'ERROR|WARN'
  
  # Common issues:
  # - Missing JDBC driver: Ensure ojdbc11.jar is in connect-plugins/libs/
  # - Oracle connection issues: Verify Oracle is running and credentials are correct
  # - Missing topics: Ensure internal topics exist (see below)
  ```

### 2. Missing Internal Topics
```bash
# Create required topics if they don't exist
docker compose exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic connect_configs --partitions 1 --replication-factor 1 --config cleanup.policy=compact"
docker compose exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic connect_offsets --partitions 1 --replication-factor 1 --config cleanup.policy=compact"
docker compose exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic connect_statuses --partitions 1 --replication-factor 1 --config cleanup.policy=compact"
```

## Reset Everything

To start fresh:

```bash
# Stop and remove containers
docker compose down -v

# Remove downloaded plugins and configs
rm -rf connect-plugins/*

# Follow the setup steps again from the beginning
```

## License

[Your License Here]

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
