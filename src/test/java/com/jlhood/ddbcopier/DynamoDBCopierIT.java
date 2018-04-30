package com.jlhood.ddbcopier;

import static com.jlhood.ddbcopier.EventualConsistency.waitUntil;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamoDBCopierIT {
    private AmazonDynamoDB ddb;
    private TestStackHelper testStackHelper;

    @Before
    public void setup() throws Exception {
        ddb = AmazonDynamoDBClientBuilder.standard().build();
        testStackHelper = new TestStackHelper(AmazonCloudFormationClientBuilder.standard().build());
        deleteAllItems(testStackHelper.getSourceTableName());
        deleteAllItems(testStackHelper.getCopyTableName());
        deleteAllItems(testStackHelper.getUpdateTableName());
    }

    @Test
    public void endToEndPut() throws Exception {
        List<Map<String, AttributeValue>> writtenRecords = generateTestRecords(13);
        writtenRecords.stream()
                .map(this::toPutItemRequest)
                .forEach(ddb::putItem);

        ScanResult scanResult = waitForCopyComplete(testStackHelper.getCopyTableName(), 13);
        List<Map<String, AttributeValue>> copiedRecords = scanResult.getItems();

        assertThat(ImmutableSet.copyOf(copiedRecords), is(ImmutableSet.copyOf(writtenRecords)));

        Map<String, AttributeValue> item = writtenRecords.get(0);
        item.put("attribute1", new AttributeValue("modified"));
        item.remove("attribute2");
        ddb.putItem(toPutItemRequest(item));

        GetItemResult getItemResult = waitForItemModified(testStackHelper.getCopyTableName(), item);
        assertThat(getItemResult.getItem(), is(item));

        ddb.deleteItem(testStackHelper.getSourceTableName(), toKey(item));
        waitForItemDeleted(testStackHelper.getCopyTableName(), item);
    }

    @Test
    public void endToEndUpdate() throws Exception {
        List<Map<String, AttributeValue>> writtenRecords = generateTestRecords(13);
        writtenRecords.stream()
                .map(this::toPutItemRequest)
                .forEach(ddb::putItem);

        ScanResult scanResult = waitForCopyComplete(testStackHelper.getUpdateTableName(),13);
        List<Map<String, AttributeValue>> copiedRecords = scanResult.getItems();

        assertThat(ImmutableSet.copyOf(copiedRecords), is(ImmutableSet.copyOf(writtenRecords)));

        Map<String, AttributeValue> item = writtenRecords.get(0);
        Map<String, AttributeValue> expectedItem = new HashMap<>(item);

        item.put("attribute1", new AttributeValue("modified"));
        item.remove("attribute2");
        ddb.putItem(toPutItemRequest(item));

        expectedItem.put("attribute1", new AttributeValue("modified"));
        // expect attribute2 to still exist when updating

        GetItemResult getItemResult = waitForItemModified(testStackHelper.getUpdateTableName(), expectedItem);
        assertThat(getItemResult.getItem(), is(expectedItem));

        ddb.deleteItem(testStackHelper.getSourceTableName(), toKey(item));
        waitForItemDeleted(testStackHelper.getUpdateTableName(), item);
    }

    private List<Map<String, AttributeValue>> generateTestRecords(int count) {
        List<Map<String, AttributeValue>> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, AttributeValue> record = new HashMap<>();
            record.put("id", new AttributeValue("id-" + i));
            record.put("attribute1", new AttributeValue("attribute1-" + i));
            record.put("attribute2", new AttributeValue("attribute2-" + i));
            records.add(record);
        }
        return records;
    }

    private PutItemRequest toPutItemRequest(Map<String, AttributeValue> record) {
        return new PutItemRequest()
                .withTableName(testStackHelper.getSourceTableName())
                .withItem(record);
    }

    private ScanResult waitForCopyComplete(String tableName, int expectedNumItems) {
        return waitUntil(scanTable(tableName),
                scanResult -> {
                    log.info("Waiting for copy completion: {} records expected, {} records found", expectedNumItems, scanResult.getCount());
                    return scanResult.getCount() == expectedNumItems;
                },
                180000,
                "Timed out waiting for table copy to complete");
    }

    private Supplier<ScanResult> scanTable(String tableName) {
        return () -> ddb.scan(new ScanRequest(tableName)
                .withLimit(100));
    }

    private GetItemResult waitForItemModified(String tableName, Map<String, AttributeValue> item) {
        return waitUntil(getItem(tableName, toKey(item)),
                result -> {
                    log.info("waiting for modified item to be copied: expected={}, actual={}", item, result.getItem());
                    return item.equals(result.getItem());
                },
                180000,
                "Timed out waiting for item modification to complete");
    }

    private Map<String, AttributeValue> toKey(Map<String, AttributeValue> item) {
        return ImmutableMap.of("id", item.get("id"));
    }

    private Supplier<GetItemResult> getItem(String tableName, Map<String, AttributeValue> key) {
        return () -> ddb.getItem(tableName, key);
    }

    private GetItemResult waitForItemDeleted(String tableName, Map<String, AttributeValue> item) {
        return waitUntil(getItem(tableName, toKey(item)),
                result -> {
                    log.info("waiting for item to be deleted");
                    return result.getItem() == null;
                },
                180000,
                "Timed out waiting for item deletion to complete");
    }

    private void deleteAllItems(String tableName) {
        ScanResult scanResult = scanTable(tableName).get();
        scanResult.getItems().forEach(m -> ddb.deleteItem(tableName, toKey(m)));
        scanResult.getItems().forEach(m -> waitForItemDeleted(tableName, m));
    }
}
