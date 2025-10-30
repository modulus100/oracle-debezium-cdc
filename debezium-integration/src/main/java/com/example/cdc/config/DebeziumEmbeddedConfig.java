package com.example.cdc.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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

    // Kafka-backed storage configuration
    @Value("${cdc.kafka.bootstrap-servers:localhost:29092}")
    private String kafkaBootstrapServers;
    @Value("${cdc.kafka.offset-storage.topic:debezium-offsets}")
    private String offsetStorageTopic;
    @Value("${cdc.kafka.offset-storage.partitions:1}")
    private int offsetStoragePartitions;
    @Value("${cdc.kafka.offset-storage.replication-factor:1}")
    private short offsetStorageReplicationFactor;
    @Value("${cdc.kafka.offset.flush.interval.ms:10000}")
    private long offsetFlushIntervalMs;
    @Value("${cdc.kafka.schema-history.topic:debezium-schema-history}")
    private String schemaHistoryTopic;
    @Value("${cdc.kafka.schema-history.consumer.group-id:}")
    private String schemaHistoryConsumerGroupId;

    // Outbound topic for transformed CDC messages
    @Value("${cdc.kafka.out.topic:cdc.out}")
    private String cdcOutTopic;

    // Dead-letter topic for failed publishes
    @Value("${cdc.kafka.out.dlt.topic:cdc.out.dlt}")
    private String cdcOutDltTopic;

    // KafkaTemplate to produce transformed CDC payloads
    @Bean
    public KafkaTemplate<String, String> cdcKafkaTemplate() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Reliability settings
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        configs.put(ProducerConfig.RETRIES_CONFIG, 5);
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
    }

    // File-backed storage paths (for experiments)
    @Value("${cdc.file.offsets:./data/debezium-offsets.dat}")
    private String fileOffsetsPath;
    @Value("${cdc.file.schema-history:./data/debezium-schema-history.dat}")
    private String fileSchemaHistoryPath;

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
        // Honor the external configuration for snapshot mode
        props.setProperty("snapshot.mode", snapshotMode);
        props.setProperty("log.mining.strategy", logMiningStrategy);
        props.setProperty("include.schema.changes", String.valueOf(includeSchemaChanges));
        props.setProperty("tombstones.on.delete", String.valueOf(tombstonesOnDelete));
        props.setProperty("offset.flush.interval.ms", String.valueOf(offsetFlushIntervalMs));

        // Offsets in Kafka (file option commented out)
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.KafkaOffsetBackingStore");
        props.setProperty("offset.storage.topic", offsetStorageTopic);
        props.setProperty("offset.storage.partitions", String.valueOf(offsetStoragePartitions));
        props.setProperty("offset.storage.replication.factor", String.valueOf(offsetStorageReplicationFactor));
        props.setProperty("offset.flush.interval.ms", String.valueOf(offsetFlushIntervalMs));
        props.setProperty("bootstrap.servers", kafkaBootstrapServers);

        // Schema history in Kafka (file option commented out)
        props.setProperty("schema.history.internal", "io.debezium.storage.kafka.history.KafkaSchemaHistory");
        props.setProperty("schema.history.internal.kafka.bootstrap.servers", kafkaBootstrapServers);
        props.setProperty("schema.history.internal.kafka.topic", schemaHistoryTopic);
        String shGroupId = StringUtils.hasText(schemaHistoryConsumerGroupId)
                ? schemaHistoryConsumerGroupId
                : (topicPrefix + "-schemahistory");
        props.setProperty("schema.history.internal.kafka.consumer.group.id", shGroupId);

        // Reasonable Oracle specifics
        // Emit Oracle NUMBER as strings so IDs appear as plain values (e.g., "21")
        props.setProperty("decimal.handling.mode", "string");
        props.setProperty("time.precision.mode", "adaptive_time_microseconds");

        // Emit only the payload (no Kafka Connect schema envelope) in JSON
        props.setProperty("key.converter", "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter.schemas.enable", "false");

        // No SMT unwrap: keep Debezium envelope so messages include payload.before and payload.after

        log.info("Configured Debezium Oracle embedded with host={} port={} cdb={} pdb={} tables=\"{}\"", dbHost, dbPort, cdbName, pdbName, tableIncludeList);
        return props;
    }

    
}
