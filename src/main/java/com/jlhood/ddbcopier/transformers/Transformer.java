package com.jlhood.ddbcopier.transformers;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.Map;

public interface Transformer {

    enum WriteType{ PUT, UPDATE}

    Map<String, AttributeValue> transform(Map<String, AttributeValue> record);

    WriteType getWriteType();
}
