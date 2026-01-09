# Migraphe Plugin Development Guide

This guide explains how to create custom plugins for Migraphe.

## Overview

Migraphe uses a plugin system based on Java's ServiceLoader mechanism. Plugins can provide:
- **Environment** - Database connection management
- **MigrationNode** - Migration task definition
- **HistoryRepository** - Execution history persistence

## Quick Start

### 1. Add Dependency

Add the `migraphe-api` module to your plugin project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.migraphe:migraphe-api:0.1.0")
}
```

### 2. Implement MigraphePlugin

Create a class implementing `MigraphePlugin`:

```java
package com.example.myplugin;

import io.github.migraphe.api.spi.*;

public class MyDatabasePlugin implements MigraphePlugin {

    @Override
    public String type() {
        return "mydatabase";  // Used in target.*.type configuration
    }

    @Override
    public EnvironmentProvider environmentProvider() {
        return new MyDatabaseEnvironmentProvider();
    }

    @Override
    public MigrationNodeProvider migrationNodeProvider() {
        return new MyDatabaseMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MyDatabaseHistoryRepositoryProvider();
    }
}
```

### 3. Implement Providers

#### EnvironmentProvider

```java
public class MyDatabaseEnvironmentProvider implements EnvironmentProvider {

    @Override
    public Environment createEnvironment(String name, EnvironmentConfig config) {
        String connectionString = config.get("connection_string")
            .orElseThrow(() -> new IllegalArgumentException("connection_string required"));

        return new MyDatabaseEnvironment(name, connectionString);
    }
}
```

#### MigrationNodeProvider

The framework handles dependency resolution. Your provider receives:
- `TaskDefinition` - Type-safe task configuration (name, up SQL, down SQL)
- `Set<NodeId>` - Pre-resolved dependencies from the framework

```java
public class MyDatabaseMigrationNodeProvider implements MigrationNodeProvider {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition task,
            Set<NodeId> dependencies,
            Environment environment) {

        // Get SQL from TaskDefinition
        String upSql = task.up().sql()
            .orElseThrow(() -> new IllegalArgumentException("up.sql required"));

        String downSql = task.down()
            .flatMap(SqlDefinition::sql)
            .orElse(null);

        return new MyDatabaseMigrationNode(
            nodeId,
            task.name(),
            task.description().orElse(""),
            environment,
            dependencies,  // Provided by framework
            upSql,
            downSql
        );
    }
}
```

#### HistoryRepositoryProvider

```java
public class MyDatabaseHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Environment environment) {
        return new MyDatabaseHistoryRepository((MyDatabaseEnvironment) environment);
    }
}
```

### 4. Register ServiceLoader

Create the service registration file:

```
src/main/resources/META-INF/services/io.github.migraphe.api.spi.MigraphePlugin
```

Contents:
```
com.example.myplugin.MyDatabasePlugin
```

### 5. Build Plugin JAR

```bash
./gradlew jar
```

## Using Your Plugin

### Option 1: plugins/ Directory

Place your plugin JAR in the `plugins/` directory of your Migraphe project:

```
my-project/
├── migraphe.yaml
├── plugins/
│   └── my-database-plugin-1.0.0.jar
└── ...
```

### Option 2: Classpath

Add your plugin to the classpath when running Migraphe.

## Configuration

Configure your plugin in target YAML files:

```yaml
# targets/mydb.yaml
type: mydatabase
connection_string: "mydb://localhost:1234/database"
username: user
password: secret
```

## SPI Interfaces Reference

### MigraphePlugin

```java
public interface MigraphePlugin {
    String type();
    EnvironmentProvider environmentProvider();
    MigrationNodeProvider migrationNodeProvider();
    HistoryRepositoryProvider historyRepositoryProvider();
}
```

### EnvironmentProvider

```java
public interface EnvironmentProvider {
    Environment createEnvironment(String name, EnvironmentConfig config);
}
```

### MigrationNodeProvider

```java
public interface MigrationNodeProvider {
    MigrationNode createNode(
        NodeId nodeId,
        TaskDefinition task,
        Set<NodeId> dependencies,
        Environment environment);
}
```

### HistoryRepositoryProvider

```java
public interface HistoryRepositoryProvider {
    HistoryRepository createRepository(Environment environment);
}
```

### TaskDefinition

Type-safe task configuration provided by the framework:

```java
public interface TaskDefinition {
    String name();
    Optional<String> description();
    SqlDefinition up();
    Optional<SqlDefinition> down();
}
```

### SqlDefinition

```java
public interface SqlDefinition {
    Optional<String> sql();
    Optional<String> file();
    Optional<String> resource();
}
```

## Core Interfaces to Implement

### Environment

```java
public interface Environment {
    EnvironmentId id();
    String name();
}
```

### MigrationNode

```java
public interface MigrationNode {
    NodeId id();
    String name();
    Environment environment();
    Set<NodeId> dependencies();
    Task upTask();
    Optional<Task> downTask();
}
```

### Task

```java
public interface Task {
    Result<TaskResult, String> execute();
}
```

### HistoryRepository

```java
public interface HistoryRepository {
    void initialize();
    void record(ExecutionRecord record);
    boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId);
    List<NodeId> executedNodes(EnvironmentId environmentId);
    List<ExecutionRecord> allRecords(EnvironmentId environmentId);
    Optional<ExecutionRecord> findLatestRecord(NodeId nodeId, EnvironmentId environmentId);
}
```

## Example: PostgreSQL Plugin

See `migraphe-postgresql` module for a complete example:
- `PostgreSQLPlugin` - Main plugin class
- `PostgreSQLEnvironmentProvider` - Creates PostgreSQLEnvironment
- `PostgreSQLMigrationNodeProvider` - Creates PostgreSQLMigrationNode
- `PostgreSQLHistoryRepositoryProvider` - Creates PostgreSQLHistoryRepository

## Best Practices

1. **Lightweight API dependency**: Only depend on `migraphe-api`, not `migraphe-core`
2. **Clear error messages**: Throw descriptive exceptions for configuration errors
3. **Transaction management**: Handle transactions properly in Task implementations
4. **Testing**: Use integration tests with real database instances (e.g., Testcontainers)
5. **ServiceLoader registration**: Don't forget the META-INF/services file
6. **Dependency handling**: Let the framework handle dependencies; focus on task execution
