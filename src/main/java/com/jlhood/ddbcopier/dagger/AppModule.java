package com.jlhood.ddbcopier.dagger;

import javax.inject.Singleton;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import com.google.common.base.Preconditions;
import com.jlhood.ddbcopier.DynamoDBCopier;

import com.jlhood.ddbcopier.transformers.Put;
import com.jlhood.ddbcopier.transformers.Transformer;
import dagger.Module;
import dagger.Provides;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;

/**
 * Application DI wiring.
 */
@Module
@Slf4j
public class AppModule {
    @Provides
    @Singleton
    public Transformer provideTransformer() {
        try {
            log.info("DDB_TRANSFORM: {}", Env.getTransform());
            if (Env.getTransform() != null) {
                Class<?> clazz = Class.forName("com.jlhood.ddbcopier.transformers." + Env.getTransform());
                Constructor<?> ctor = clazz.getConstructor();
                return (Transformer) ctor.newInstance();
            }
            return new Put();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create transformer", e);
        }

    }

    @Provides
    @Singleton
    public AmazonDynamoDB provideAmazonDynamoDB() {
        // automatically configured to local region in lambda runtime environment
        return AmazonDynamoDBClientBuilder.standard().build();
    }

    @Provides
    @Singleton
    public DynamoDBCopier provideDynamoDBCopier(final AmazonDynamoDB amazonDynamoDB, final Transformer transformer) {
        String destinationTableName = Env.getDestinationTable();
        Preconditions.checkArgument(destinationTableName != null, String.format("Destination table name not set. Expected environment variable with key %s to contain name of destination table.", Env.DESTINATION_TABLE_KEY));
        return new DynamoDBCopier(destinationTableName, amazonDynamoDB, transformer);
    }
}
