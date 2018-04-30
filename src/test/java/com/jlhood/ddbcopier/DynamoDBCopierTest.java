package com.jlhood.ddbcopier;

import static org.mockito.Mockito.inOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;

import com.google.common.collect.ImmutableMap;

import com.jlhood.ddbcopier.transformers.Put;
import com.jlhood.ddbcopier.transformers.Update;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DynamoDBCopierTest {
    private static final String DESTINATION_TABLE = "tableForTwo";

    private static final String DISALLOWED_STREAM_VIEW_TYPE = "OLD_IMAGE";
    private static final String ALLOWED_STREAM_VIEW_TYPE = "NEW_IMAGE";

    @Mock
    private AmazonDynamoDB amazonDynamoDB;

    private DynamoDBCopier copier;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        copier = new DynamoDBCopier(DESTINATION_TABLE, amazonDynamoDB, new Put());
    }

    @Test(expected = IllegalStateException.class)
    public void accept_disallowedStreamViewType() throws Exception {
        StreamRecord record = new StreamRecord();
        record.setStreamViewType(DISALLOWED_STREAM_VIEW_TYPE);
        copier.accept(buildDynamoDbEvent("INSERT", record));
    }

    @Test
    public void accept_insert() throws Exception {
        copier.accept(buildDynamoDbEvent("INSERT", streamRecord(null, attributeValue("a", "b")), streamRecord(null, attributeValue("c", "d"))));

        InOrder inOrder = inOrder(amazonDynamoDB);

        PutItemRequest expected = new PutItemRequest()
                .withTableName(DESTINATION_TABLE)
                .withItem(ImmutableMap.of("a", new AttributeValue("b")));
        inOrder.verify(amazonDynamoDB).putItem(expected);

        expected = expected
                .withItem(ImmutableMap.of("c", new AttributeValue("d")));
        inOrder.verify(amazonDynamoDB).putItem(expected);
    }

    @Test
    public void accept_remove() throws Exception {
        copier.accept(buildDynamoDbEvent(DynamoDBCopier.DELETE_EVENT_NAME, streamRecord(attributeValue("a", "b"), null), streamRecord(attributeValue("c", "d"), null)));

        InOrder inOrder = inOrder(amazonDynamoDB);

        inOrder.verify(amazonDynamoDB).deleteItem(DESTINATION_TABLE, ImmutableMap.of("a", new AttributeValue("b")));
        inOrder.verify(amazonDynamoDB).deleteItem(DESTINATION_TABLE, ImmutableMap.of("c", new AttributeValue("d")));
    }

    @Test
    public void accept_insert_with_update_method() throws Exception {
        copier = new DynamoDBCopier(DESTINATION_TABLE, amazonDynamoDB, new Update());

        copier.accept(buildDynamoDbEvent("INSERT",
                streamRecord(attributeValue("k", "1"), attributeValue("a", "b")),
                streamRecord(attributeValue("k", "1"), attributeValue("c", "d"))));

        InOrder inOrder = inOrder(amazonDynamoDB);

        inOrder.verify(amazonDynamoDB).updateItem(new UpdateItemRequest()
                .withTableName(DESTINATION_TABLE)
                .withKey(ImmutableMap.of("k", new AttributeValue("1")))
                .withUpdateExpression("SET #name0 = :value0")
                .withExpressionAttributeNames(ImmutableMap.of("#name0", "a"))
                .withExpressionAttributeValues(ImmutableMap.of(":value0", new AttributeValue("b"))));

        inOrder.verify(amazonDynamoDB).updateItem(new UpdateItemRequest()
                .withTableName(DESTINATION_TABLE)
                .withKey(ImmutableMap.of("k", new AttributeValue("1")))
                .withUpdateExpression("SET #name0 = :value0")
                .withExpressionAttributeNames(ImmutableMap.of("#name0", "c"))
                .withExpressionAttributeValues(ImmutableMap.of(":value0", new AttributeValue("d"))));
    }

    private DynamodbEvent buildDynamoDbEvent(String eventName, StreamRecord... records) {
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(toDynamoDbStreamRecords(eventName, records));
        return event;
    }

    private List<DynamodbEvent.DynamodbStreamRecord> toDynamoDbStreamRecords(String eventName, StreamRecord... records) {
        return Arrays.stream(records)
                .map(r -> toDynamoDbStreamRecord(eventName, r))
                .collect(Collectors.toList());
    }

    private DynamodbEvent.DynamodbStreamRecord toDynamoDbStreamRecord(String eventName, StreamRecord streamRecord) {
        DynamodbEvent.DynamodbStreamRecord result = new DynamodbEvent.DynamodbStreamRecord();
        result.setEventName(eventName);
        result.setDynamodb(streamRecord);
        return result;
    }

    private StreamRecord streamRecord(Map<String, AttributeValue> keys, Map<String, AttributeValue> attributes) {
        StreamRecord streamRecord = new StreamRecord();
        streamRecord.setStreamViewType(ALLOWED_STREAM_VIEW_TYPE);

        streamRecord.setKeys(keys);
        streamRecord.setNewImage(attributes);

        return streamRecord;
    }

    private Map<String, AttributeValue> attributeValue(String key, String value) {
        return ImmutableMap.of(key, new AttributeValue(value));
    }
}