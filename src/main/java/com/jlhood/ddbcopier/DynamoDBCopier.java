package com.jlhood.ddbcopier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import com.jlhood.ddbcopier.transformers.Transformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates DynamoDB stream events to writes to the given destination DynamoDB table. Writes the items sequentially according to the order
 * in which they were received from the stream to maintain the order of writes from source table to destination table.
 */
@RequiredArgsConstructor
@Slf4j
public class DynamoDBCopier implements Consumer<DynamodbEvent> {
    public static final String DELETE_EVENT_NAME = "REMOVE";

    private static final Set<String> ALLOWED_STREAM_VIEW_TYPES = ImmutableSet.of("NEW_IMAGE", "NEW_AND_OLD_IMAGES");

    private final String destinationTable;
    private final AmazonDynamoDB amazonDynamoDB;
    private final Transformer transformer;

    @Override
    public void accept(final DynamodbEvent dynamodbEvent) {
        log.info("Copying {} records", dynamodbEvent.getRecords().size());
        dynamodbEvent.getRecords().stream()
                .forEach(this::processRecord);
    }

    private void processRecord(final DynamodbEvent.DynamodbStreamRecord record) {
        Preconditions.checkState(ALLOWED_STREAM_VIEW_TYPES.contains(record.getDynamodb().getStreamViewType()),
                "ddb-copier requires source table stream to be configured with one of the following StreamViewTypes: " + ALLOWED_STREAM_VIEW_TYPES);

        if (isDelete(record)) {
            deleteRecord(record);
        } else {
            putRecord(record);
        }
    }

    private boolean isDelete(final DynamodbEvent.DynamodbStreamRecord record) {
        return DELETE_EVENT_NAME.equals(record.getEventName());
    }

    private void deleteRecord(final DynamodbEvent.DynamodbStreamRecord record) {
        log.info("Deleting record: {}", record.getDynamodb().getKeys());
        amazonDynamoDB.deleteItem(destinationTable, record.getDynamodb().getKeys());
    }

    private void putRecord(final DynamodbEvent.DynamodbStreamRecord record) {
        Map<String, AttributeValue> key = record.getDynamodb().getKeys();
        log.info("Creating or updating record: {}", key);
        Map<String, AttributeValue> newValues = transformer.transform(record.getDynamodb().getNewImage());

        if (transformer.getWriteType() == Transformer.WriteType.UPDATE) {
            StringBuilder updateExpr = new StringBuilder();
            Map<String, String> attribNames = new HashMap<>();
            Map<String, AttributeValue> attribValues = new HashMap<>();
            int i = 0;
            for (Map.Entry<String, AttributeValue> e : newValues.entrySet()) {
                if (key.keySet().contains(e.getKey())) {
                    continue;
                }
                String n = "#name" + i;
                String v = ":value" + i;
                attribNames.put(n, e.getKey());
                attribValues.put(v, e.getValue());
                if (updateExpr.length() == 0) {
                    updateExpr.append("SET ");
                } else {
                    updateExpr.append(", ");
                }
                updateExpr.append(n).append(" = ").append(v);
                i++;
            }
            amazonDynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(destinationTable)
                    .withKey(key)
                    .withUpdateExpression(updateExpr.toString())
                    .withExpressionAttributeNames(attribNames)
                    .withExpressionAttributeValues(attribValues));
            return;
        }

        amazonDynamoDB.putItem(new PutItemRequest()
                .withTableName(destinationTable)
                .withItem(newValues));
    }
}
