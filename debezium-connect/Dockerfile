ARG DEBEZIUM_VERSION=3.3.1.Final

FROM quay.io/debezium/connect:${DEBEZIUM_VERSION}

COPY drivers/ojdbc11.jar /kafka/libs/

COPY connectors/ /docker-entrypoint-init.d/

EXPOSE 8083
