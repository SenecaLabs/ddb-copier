package com.jlhood.ddbcopier.dagger;

/**
 * Helper class for fetching environment values.
 */
public final class Env {
    public static final String DESTINATION_TABLE_KEY = "DESTINATION_TABLE_NAME";
    public static final String DDB_TRANSFORM = "DDB_TRANSFORM";

    private Env() {
    }

    public static String getDestinationTable() {
        return System.getenv(DESTINATION_TABLE_KEY);
    }

    public static String getTransform() {
        return System.getenv(DDB_TRANSFORM);
    }
}
