package com.example.cdc.flow;

import com.example.cdc.model.CdcMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.debezium.dsl.Debezium;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class CdcInboundFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(CdcInboundFlowConfig.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cdc.kafka.out.topic:cdc.out}")
    private String cdcOutTopic;

    @Value("${cdc.kafka.out.dlt.topic:cdc.out.dlt}")
    private String cdcOutDltTopic;

    public CdcInboundFlowConfig(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    public IntegrationFlow debeziumInbound(Properties debeziumOracleProperties) {
        return IntegrationFlow
                .from(Debezium.inboundChannelAdapter(debeziumOracleProperties)
                        .headerNames("special*")
                        .contentType("application/json")
                        .enableBatch(false))
                .handle((Message<?> message) -> {
                    PreprocessResult prep = preprocessPayload(message);

                    kafkaTemplate.send(cdcOutTopic, prep.key(), prep.outJson())
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    ProducerRecord<String, String> dltRecord = new ProducerRecord<>(cdcOutDltTopic, prep.outJson());
                                    dltRecord.headers().add("x-error-class", ex.getClass().getName().getBytes(StandardCharsets.UTF_8));
                                    String msg = ex.getMessage() != null ? ex.getMessage() : "";
                                    dltRecord.headers().add("x-error-message", msg.getBytes(StandardCharsets.UTF_8));
                                    dltRecord.headers().add("x-original-topic", cdcOutTopic.getBytes(StandardCharsets.UTF_8));
                                    // Optionally publish to DLT (uncomment to enable)
                                    // TODO think about how to handle failures
                                    // kafkaTemplate.send(dltRecord);
                                    log.warn("Handling message to DLT due to send failure: {}", ex.toString());
                                }
                            });
                })
                .get();
    }

    private PreprocessResult preprocessPayload(Message<?> message) {
        Object payload = message.getPayload();
        String json = (payload instanceof byte[]) ? new String((byte[]) payload, StandardCharsets.UTF_8) : String.valueOf(payload);
        log.info(json);

        // Parse once to avoid double-encoding and to reuse for key/op
        JsonNode root;
        String operationType = "UNKNOWN";
        String key = null;
        try {
            root = objectMapper.readTree(json);
            operationType = extractOperationType(root);
            key = extractIdKey(root);
        } catch (Exception e) {
            // Fallbacks already initialized: operationType=UNKNOWN; key=null
            root = null;
        }

        CdcMessage cdcMessage = new CdcMessage(root, operationType);

        String computedOutJson;
        try {
            computedOutJson = objectMapper.writeValueAsString(cdcMessage);
        } catch (Exception e) {
            computedOutJson = json; // fallback to raw if serialization fails
        }
        final String outJson = computedOutJson;

        return new PreprocessResult(key, outJson);
    }

    private record PreprocessResult(String key, String outJson) {}

    private String extractIdKey(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        String op = root.path("op").asText("");
        JsonNode container = "d".equals(op) ? root.path("before") : root.path("after");
        if (!container.isMissingNode() && !container.isNull()) {
            JsonNode idNode = container.path("ID");
            if (!idNode.isMissingNode() && !idNode.isNull()) {
                return idNode.asText();
            }
        }
        return null;
    }

    private String extractOperationType(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "UNKNOWN";
        }
        String op = root.path("op").asText("");
        return switch (op) {
            case "c" -> "CREATE";
            case "u" -> "UPDATE";
            case "d" -> "DELETE";
            case "r" -> "READ";
            default -> "UNKNOWN";
        };
    }
}
