package com.example.cdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.debezium.dsl.Debezium;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.util.Properties;
import java.nio.charset.StandardCharsets;

@Configuration
public class DebeziumEmbeddedConfig {

    private static final Logger log = LoggerFactory.getLogger(DebeziumEmbeddedConfig.class);

    // Oracle connection
    @Value("${cdc.oracle.database.hostname}")
    private String dbHost;
    @Value("${cdc.oracle.database.port}")
    private int dbPort;
    @Value("${cdc.oracle.database.cdb-name:ORCLCDB}")
    private String cdbName;
    @Value("${cdc.oracle.database.pdb-name}")
    private String pdbName;
    @Value("${cdc.oracle.database.username}")
    private String dbUser;
    @Value("${cdc.oracle.database.password}")
    private String dbPass;

    // Debezium filtering and naming
    @Value("${cdc.oracle.server-name}")
    private String topicPrefix;
    @Value("${cdc.oracle.table-include-list}")
    private String tableIncludeList;
    @Value("${cdc.oracle.snapshot-mode:initial}")
    private String snapshotMode;
    @Value("${cdc.oracle.log-mining-strategy:online_catalog}")
    private String logMiningStrategy;
    @Value("${cdc.oracle.include-schema-changes:false}")
    private boolean includeSchemaChanges;
    @Value("${cdc.oracle.tombstones-on-delete:false}")
    private boolean tombstonesOnDelete;

    @Bean
    public Properties debeziumOracleProperties() {
        Properties props = new Properties();
        props.setProperty("name", "oracle-embedded-connector");
        props.setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector");
        props.setProperty("tasks.max", "1");

        // Required Oracle properties
        props.setProperty("database.hostname", dbHost);
        props.setProperty("database.port", String.valueOf(dbPort));
        props.setProperty("database.user", dbUser);
        props.setProperty("database.password", dbPass);
        props.setProperty("database.dbname", cdbName); // CDB name (SID)
        props.setProperty("database.pdb.name", pdbName); // PDB

        // Topic prefix used in source records metadata
        props.setProperty("topic.prefix", topicPrefix);

        // Filters and behavior
        if (StringUtils.hasText(tableIncludeList)) {
            props.setProperty("table.include.list", tableIncludeList);
        }
        props.setProperty("snapshot.mode", snapshotMode);
        props.setProperty("log.mining.strategy", logMiningStrategy);
        props.setProperty("include.schema.changes", String.valueOf(includeSchemaChanges));
        props.setProperty("tombstones.on.delete", String.valueOf(tombstonesOnDelete));

        // Embedded engine offset storage - in memory (no disk)
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.MemoryOffsetBackingStore");
        // Embedded schema history - in memory (no disk)
        // Use Debezium core class to avoid extra dependency on storage-memory module
        props.setProperty("schema.history.internal", "io.debezium.relational.history.MemorySchemaHistory");

        // Reasonable Oracle specifics
        props.setProperty("decimal.handling.mode", "precise");
        props.setProperty("time.precision.mode", "adaptive_time_microseconds");

        log.info("Configured Debezium Oracle embedded with host={} port={} cdb={} pdb={} tables=\"{}\"", dbHost, dbPort, cdbName, pdbName, tableIncludeList);
        return props;
    }

    @Bean
    public IntegrationFlow debeziumInbound(Properties debeziumOracleProperties) {
        return IntegrationFlow
                .from(Debezium.inboundChannelAdapter(debeziumOracleProperties)
                        .headerNames("special*")
                        .contentType("application/json")
                        .enableBatch(false))
                .handle((Message<?> m) -> {
                    Object p = m.getPayload();
                    if (p instanceof byte[]) {
                        log.info(new String((byte[]) p, StandardCharsets.UTF_8));
                    } else {
                        log.info(String.valueOf(p));
                    }
                })
                .get();
    }
}
