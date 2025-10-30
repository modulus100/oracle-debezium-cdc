package com.example.cdc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Simple DTO for CDC messages produced by the Debezium pipeline.
 * - data: the payload-only Debezium envelope as a structured JsonNode (before/after/source/op/...)
 *         This avoids double-encoding the inner JSON.
 * - operationType: high-level operation type (CREATE, UPDATE, DELETE, READ, or UNKNOWN)
 */
public record CdcMessage(JsonNode data, String operationType) {
}
