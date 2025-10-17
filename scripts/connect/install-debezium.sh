#!/usr/bin/env bash
set -euo pipefail

# Installs Debezium Oracle connector into the running Kafka Connect worker (Confluent image)
# Prereqs:
# - docker compose service name: connect (as in docker-compose.yml)
# - Confluent Hub client available in the image (cp-kafka-connect has it)
# - Place Oracle JDBC jar into ./connect-plugins/libs (e.g., ./connect-plugins/libs/ojdbc11.jar)
#
# Usage:
#   bash scripts/connect/install-debezium.sh [DEBEZIUM_VERSION]
# Example:
#   bash scripts/connect/install-debezium.sh 3.0.0.Final

VERSION="${1:-3.0.0.Final}"
CONNECT_CONTAINER="connect"
PLUGIN_COORD="debezium/debezium-connector-oracle:${VERSION}"
INSTALL_DIR_IN_CONTAINER="/usr/share/confluent-hub-components/debezium-debezium-connector-oracle"
HOST_LIBS_DIR="$(cd "$(dirname "$0")"/../../connect-plugins/libs && pwd)"
CONTAINER_LIBS_DIR="/connect-plugins/libs"

if ! docker compose ps --services | grep -q "^${CONNECT_CONTAINER}$"; then
  echo "Connect service '${CONNECT_CONTAINER}' not found. Start it with: docker compose up -d connect" >&2
  exit 1
fi

echo "Installing ${PLUGIN_COORD} into Kafka Connect ..."
docker compose exec -T "${CONNECT_CONTAINER}" bash -lc \
  "confluent-hub install --no-prompt ${PLUGIN_COORD}"

echo "Copying Oracle JDBC drivers (if present) into the connector lib directory ..."
# Ensure host libs directory exists, but it's okay if empty
mkdir -p "${HOST_LIBS_DIR}"

# Determine connector lib dir (created by install)
CONNECTOR_LIB_DIR_IN_CONTAINER="${INSTALL_DIR_IN_CONTAINER}/lib"

# If any ojdbc*.jar present in the mounted host libs, copy them into connector lib dir
if compgen -G "${HOST_LIBS_DIR}/ojdbc*.jar" > /dev/null; then
  for jar in "${HOST_LIBS_DIR}"/ojdbc*.jar; do
    jar_name="$(basename "$jar")"
    echo "Copying $jar_name into ${CONNECTOR_LIB_DIR_IN_CONTAINER} ..."
    docker compose exec -T "${CONNECT_CONTAINER}" bash -lc \
      "cp -f '${CONTAINER_LIBS_DIR}/$jar_name' '${CONNECTOR_LIB_DIR_IN_CONTAINER}/'"
  done
else
  echo "No ojdbc*.jar found under ${HOST_LIBS_DIR}. Place your Oracle JDBC jar there (e.g., ojdbc11.jar)." >&2
fi

echo "Restarting Kafka Connect to pick up the new plugin ..."
docker compose restart "${CONNECT_CONTAINER}"

# Wait briefly, then verify plugin availability
sleep 5

echo "Checking available connector plugins ..."
curl -sf http://localhost:8083/connector-plugins | jq '.' || true

echo "Done. You can now register the connector with scripts/connect/register-oracle.sh"
