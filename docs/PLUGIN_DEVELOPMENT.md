# Migraphe Plugin Development Guide

This guide explains how to create custom plugins for Migraphe.

## Overview

Migraphe uses a plugin system based on Java's ServiceLoader mechanism. Plugins can provide:
- **Target** - Database connection management
- **MigrationNode** - Migration task definition
- **HistoryRepository** - Execution history persistence

## Quick Start

### 1. Add Dependency

Add the `migraphe-api` module to your plugin project. Migraphe artefacts are currently distributed via JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.kakusuke.migraphe:migraphe-api:v0.7.0")
}
```

The coordinate has to match the migraphe you run against. The API breaks in 0.7.0 (see
[What changed in 0.7.0](#what-changed-in-070)), and a jar built against an older one loads and then
fails with `AbstractMethodError` at the first call.

### 2. Implement MigraphePlugin

Create a class implementing `MigraphePlugin`:

```java
package com.example.myplugin;

import io.github.kakusuke.migraphe.api.spi.*;

public class MyDatabasePlugin implements MigraphePlugin<String> {

    @Override
    public String type() {
        return "mydatabase";  // Used in target.*.type configuration
    }

    @Override
    public TargetProvider targetProvider() {
        return new MyDatabaseTargetProvider();
    }

    @Override
    public MigrationNodeProvider<String> migrationNodeProvider() {
        return new MyDatabaseMigrationNodeProvider();
    }

    @Override
    public HistoryRepositoryProvider historyRepositoryProvider() {
        return new MyDatabaseHistoryRepositoryProvider();
    }
}
```

### 3. Implement Providers

#### TargetProvider

`TargetDefinition` carries only `type()`. Everything else your `targets/*.yaml` holds goes on a
sub-interface, which SmallRye maps for you — so the keys are read as typed accessors rather than by
name:

```java
public interface MyTargetDefinition extends TargetDefinition {
    String connectionString();
    Optional<String> password();
}

public class MyDatabaseTargetProvider implements TargetProvider {

    @Override
    public Target createTarget(String name, TargetDefinition definition) {
        if (!(definition instanceof MyTargetDefinition mine)) {
            throw new IllegalArgumentException(
                "Expected MyTargetDefinition but got: " + definition.getClass().getName());
        }

        return new MyDatabaseTarget(name, mine.connectionString(), mine.password().orElse(null));
    }
}
```

#### MigrationNodeProvider

The framework handles dependency resolution. Your provider receives:
- `TaskDefinition<T>` - Type-safe task configuration; cast to your own sub-interface to read the
  keys only your plugin understands
- `Set<NodeId>` - Pre-resolved dependencies from the framework

```java
public class MyDatabaseMigrationNodeProvider implements MigrationNodeProvider {

    @Override
    public MigrationNode createNode(
            NodeId nodeId,
            TaskDefinition<String> task,
            Set<NodeId> dependencies,
            Target target) {

        String upSql = task.up();
        String downSql = task.down().filter(sql -> !sql.isBlank()).orElse(null);

        // A task declares either a rollback or the reason there is none. Casting to your own
        // definition interface is how you read whatever else its YAML carries.
        String noWayBack =
            task instanceof MyTaskDefinition mine ? mine.noWayBack().orElse(null) : null;

        return new MyDatabaseMigrationNode(
            nodeId,
            task.name(),
            task.description().orElse(null),
            target,
            dependencies,  // Provided by framework
            upSql,
            downSql,
            noWayBack
        );
    }
}
```

#### HistoryRepositoryProvider

```java
public class MyDatabaseHistoryRepositoryProvider implements HistoryRepositoryProvider {

    @Override
    public HistoryRepository createRepository(Target target) {
        return new MyDatabaseHistoryRepository((MyDatabaseTarget) target);
    }
}
```

### 4. Register ServiceLoader

Create the service registration file:

```
src/main/resources/META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin
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

Migraphe loads plugins from the classpath and from the Maven coordinates the project declares. It
does not scan a directory for JARs, so the plugin has to be published somewhere a resolver can reach —
a local `~/.m2` install is enough while developing.

### Option 1: Maven coordinate (CLI)

Publish the JAR (`./gradlew publishToMavenLocal` while developing), then declare it in
`migraphe.yaml` and pin it:

```yaml
plugins:
  - coordinate: com.example:my-database-plugin:1.0.0
```

```bash
migraphe pin
```

A lockfile is required whenever `plugins:` is declared; `migraphe pin` writes it.

### Option 2: migraphePlugin configuration (Gradle)

```kotlin
dependencies {
    migraphePlugin("com.example:my-database-plugin:1.0.0")
}
```

### Option 3: Classpath

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
public interface MigraphePlugin<T> {
    String type();
    TargetProvider targetProvider();
    MigrationNodeProvider<T> migrationNodeProvider();
    HistoryRepositoryProvider historyRepositoryProvider();
}
```

`T` is what your `up:` and `down:` keys map to — `String` for a plugin whose tasks are SQL.

### TargetProvider

```java
public interface TargetProvider {
    Target createTarget(String name, TargetDefinition config);
}
```

### MigrationNodeProvider

```java
public interface MigrationNodeProvider<T> {
    MigrationNode createNode(
        NodeId nodeId,
        TaskDefinition<T> task,
        Set<NodeId> dependencies,
        Target target);
}
```

### HistoryRepositoryProvider

```java
public interface HistoryRepositoryProvider {
    HistoryRepository createRepository(Target target);
}
```

### TaskDefinition

Type-safe task configuration provided by the framework. `T` is whatever your plugin's `up:` and
`down:` keys hold — plain SQL for the JDBC family, but a plugin is free to map them to a record:

```java
public interface TaskDefinition<T> {
    String name();
    Optional<String> description();
    String target();
    Optional<List<String>> dependencies();
    T up();
    Optional<T> down();
}
```

Your own definition interface extends it and adds whatever else the task's YAML may carry. SmallRye
maps the keys, so `@WithName` renames one where the YAML spelling differs:

```java
public interface SqlTaskDefinition extends TaskDefinition<String> {
    Optional<Boolean> autocommit();

    @WithName("autocommit.up")
    Optional<Boolean> autocommitUp();
}
```

## Core Interfaces to Implement

### Target

```java
public interface Target {
    TargetId id();
    String name();
}
```

A target is a **connection**, and only that. (It was called `Environment` before 0.7.0;
*environment* now means the `environments/*.yaml` overlay and nothing else.)

### MigrationNode

```java
public interface MigrationNode {
    NodeId id();
    String name();
    @Nullable String description();
    Target target();
    Set<NodeId> dependencies();
    Task upTask();
    @Nullable Task downTask();

    @Nullable String fingerprint(Fingerprinter fingerprinter);

    default @Nullable String noWayBack() {
        return null;
    }
}
```

**`fingerprint` has no default and must be implemented.** It is what lets `status` tell a migration
that still matches its definition from one that was edited after it ran, so a plugin that cannot
answer stops every command but `status`. Hand your parts to the `Fingerprinter` and return what it
gives back — core frames them, appends the dependency closure and hashes, so every plugin folds
alike:

```java
@Override
public @Nullable String fingerprint(Fingerprinter fingerprinter) {
    return downTask == null
            ? fingerprinter.over(upTask.signature())
            : fingerprinter.over(upTask.signature(), downTask.signature());
}
```

Returning `null` is **not** a way to opt out: the one null the design keeps is core's own adapter
over a history row, which has to report that the row carries no token. A declared node answering
null is out of contract and is reported as a plugin fault.

`noWayBack()` returns the author's reason a migration is one-way, or `null`. It answers a question a
missing `downTask()` cannot: the author decided, or the author forgot. Those want opposite
responses, so a task must declare one or the other — `up` refuses a project until every task does.

### Task

```java
public interface Task {
    Result<TaskResult, String> execute();
    String description();
    String signature();
}
```

**`signature` has no default either.** It is the text your half of the fingerprint is folded from,
so it must cover everything about the task that could change what it does — and it must *frame* its
parts if it has several, or a value ending in another's name collides with it:

```java
@Override
public String signature() {
    return part(sql.strip()) + part(autocommit ? "t" : "f");
}

private static String part(String text) {
    return text.length() + ":" + text;  // length-prefixed, so no value can imitate the next one
}
```

### HistoryRepository

```java
public interface HistoryRepository {
    void initialize();
    void record(ExecutionRecord record);
    boolean wasExecuted(NodeId nodeId);
    List<NodeId> executedNodes();
    List<ExecutionRecord> allRecords();
    default List<ExecutionRecord> latestApplies() { /* walks allRecords() per id; override if cheaper */ }
    @Nullable ExecutionRecord findLatestRecord(NodeId nodeId);
}
```

## Capabilities: what a plugin has to implement to be rolled back

A rollback does not run the `down:` in the task file. It runs the payload the **history row** kept,
because that is what matches the objects that exist — the file describes what the migration would
create if it were applied today, which is a different thing the moment it is edited. Two small
interfaces carry that, and a plugin that implements neither can apply migrations but never take them
back out.

### `RollbackPayloadProvider` — on the up task

What the row should keep, reported by the task that applied it:

```java
public interface RollbackPayloadProvider {
    @Nullable String serializedDownTask();
    @Nullable String pluginMetadata();
}
```

`serializedDownTask` is stored as plain text, so an operator debugging a stuck migration can read it
straight out of the column; the JDBC family stores the `down:` SQL verbatim. `pluginMetadata` is
yours — core stores and returns it without looking inside, and only you know its encoding. The JDBC
family uses `java.util.Properties` to carry `autocommit`, which the rollback needs and the SQL alone
does not say.

An up task reports this on success through its `TaskResult`. Implementing the interface as well lets
`amend` ask the same question **without executing anything**, which is how it writes a complete row
for a migration that never ran — so a plugin that does not implement it cannot be amended at all.

### `DownTaskRestorer` — on the target

Turning that payload back into something runnable:

```java
public interface DownTaskRestorer {
    Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata);
}
```

Detected with `instanceof`, so a target that does not implement it simply cannot roll anything back —
visible in the type system rather than as a failure halfway through a run. The returned `Task`
travels the ordinary executor, listener and history-writer path, exactly like an apply.

```java
public final class MyTarget implements Target, DownTaskRestorer {
    @Override
    public Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata) {
        return new MyTask(this, serializedDownTask, MyOptions.decode(pluginMetadata));
    }
}
```

## What changed in 0.7.0

A plugin built against 0.6.x will not compile, which is the intent — and a **jar** built against it
loads and then fails with `AbstractMethodError` at the first call, so plugins must be rebuilt rather
than re-resolved.

| was | is |
|---|---|
| `Environment`, `EnvironmentId`, `EnvironmentDefinition`, `EnvironmentProvider` | `Target`, `TargetId`, `TargetDefinition`, `TargetProvider` |
| `Optional<Task> downTask()` | `@Nullable Task downTask()` |
| — | `MigrationNode.fingerprint(Fingerprinter)`, required |
| — | `Task.signature()`, required |
| — | `MigrationNode.noWayBack()`, defaulted |
| `HistoryRepository` reads took a target id | they are asked about ids alone; every row carries the target it was applied to |
| `ExecutionRecord`, `TaskResult` | both carry more, including `origin` and `no_way_back` |

## Example: PostgreSQL Plugin

See `migraphe-plugin-postgresql` module for a complete example:
- `PostgreSQLPlugin` - Main plugin class
- `PostgreSQLTargetProvider` - Creates PostgreSQLTarget
- `PostgreSQLMigrationNodeProvider` - Creates PostgreSQLMigrationNode
- `PostgreSQLHistoryRepositoryProvider` - Creates PostgreSQLHistoryRepository

## Best Practices

1. **Lightweight API dependency**: Only depend on `migraphe-api`, not `migraphe-core`
2. **Clear error messages**: Throw descriptive exceptions for configuration errors
3. **Transaction management**: Handle transactions properly in Task implementations
4. **Testing**: Use integration tests with real database instances (e.g., Testcontainers)
5. **ServiceLoader registration**: Don't forget the META-INF/services file
6. **Dependency handling**: Let the framework handle dependencies; focus on task execution
7. **Rollback**: Implement `RollbackPayloadProvider` and `DownTaskRestorer` unless your migrations
   are genuinely one-way — without them a project can apply migrations and never take them back out,
   and `amend` cannot repair its history either
