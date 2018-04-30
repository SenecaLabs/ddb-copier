package com.jlhood.ddbcopier.transformers;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.Map;

public class Put implements Transformer {

    @Override
    public Map<String, AttributeValue> transform(Map<String, AttributeValue> record) {
        return record;
    }

    @Override
    public WriteType getWriteType() {
        return WriteType.PUT;
    }
}
