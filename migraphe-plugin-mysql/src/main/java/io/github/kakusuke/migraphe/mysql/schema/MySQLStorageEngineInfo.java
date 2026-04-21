package io.github.kakusuke.migraphe.mysql.schema;

public record MySQLStorageEngineInfo(
        String name, String support, String transactions, String xa, String savepoints) {}
