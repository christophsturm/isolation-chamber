# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Isolation Chamber is a Kotlin library that provides isolated databases for tests, currently supporting PostgreSQL. The main goal is to enable tests to run with isolated database instances to prevent test interference.

## Build Commands

### Prerequisites
Before running tests, start the required services:
```bash
docker-compose up
```

### Gradle Commands
- **Run all tests**: `./gradlew test` or `./gradlew jvmTest`
- **Run all tests (including multi-platform)**: `./gradlew allTests`
- **Format code**: `./gradlew ktfmtFormat`
- **Check code formatting**: `./gradlew ktfmtCheck`
- **Check dependency updates**: `./gradlew dependencyUpdates`
- **Build project**: `./gradlew build`
- **Clean build**: `./gradlew clean`

### Amper Commands (Alternative Build System)
The project also supports JetBrains Amper 0.7.0:
- **Build all modules**: `./amper build`
- **Run tests**: `./amper test`
- **Clean build outputs**: `./amper clean`
- **Clean shared caches**: `./amper clean-shared-caches`
- **Update Amper**: `./amper update`

For debugging Amper builds:
- **Verbose output**: `./amper build --log-level debug`
- **Build specific module**: `./amper build -m core`

### Testing Individual Modules
- **Core module tests**: `./gradlew :core:test` or `./amper test -m core`
- **IntegreSQL client tests**: `./gradlew :integresql-client:test` or `./amper test -m integresql-client`
- **IntegreSQL tests**: `./gradlew :integresql:test` or `./amper test -m integresql`
- **PostgreSQL Docker tests**: `./gradlew :postgresql-docker:test` or `./amper test -m postgresql-docker`

## Architecture

The project is a multi-module Gradle project with the following structure:

### Core Module (`core/`)
- Contains the main interfaces: `PostgresqlFactory` and `PostgresDb`
- Defines the contract for database isolation implementations
- No external dependencies beyond Kotlin stdlib

### IntegreSQL Client Module (`integresql-client/`)
- HTTP client for communicating with IntegreSQL service
- Uses kotlinx.serialization for JSON handling
- Depends on restaurant HTTP client library (0.0.12)
- Contains API models and client implementation

### IntegreSQL Module (`integresql/`)
- Implements `PostgresqlFactory` interface using IntegreSQL backend
- Bridges core abstractions with the IntegreSQL client
- Main implementation for database isolation

### PostgreSQL Docker Module (`postgresql-docker/`)
- Alternative implementation using Docker containers
- Implements `PostgresqlFactory` interface using Testcontainers
- Creates isolated databases within Docker PostgreSQL containers

## Key Technologies

- **Build System**: Dual support for Gradle with Kotlin DSL and JetBrains Amper 0.7.0
- **Language**: Kotlin 2.0
- **Test Framework**: Failgood 0.9.1 (custom testing framework with expressive DSL)
- **Code Formatting**: ktfmt (via Gradle plugin)
- **Serialization**: kotlinx.serialization
- **Assertions**: Kotlin Power Assert plugin for better assertion messages

## Build System Notes

The project maintains both Gradle and Amper build configurations:
- **Gradle**: Primary build system with full publishing support
- **Amper**: Experimental build system with simpler YAML-based configuration
- Amper build outputs are in `.amper/build/`
- Amper modules are defined in `project.yaml` and individual `module.yaml` files

## Infrastructure Requirements

The project requires Docker Compose with:
- PostgreSQL 16.3-alpine on port 15432
- IntegreSQL v1.1.0 on port 5001

## API Usage Pattern

```kotlin
// Get an isolated database with optional schema
val db = PostgresqlFactory.preparePostgresDB(schema)

// Use the database connection
db.connection.use { conn ->
    // Your test code here
}

// Clean up at the end of test suite
PostgresqlFactory.cleanUp()
```

## Testing Approach

- Tests use the Failgood framework with its expressive DSL
- Test files follow the pattern `*Test.kt`
- Tests require Docker services to be running
- The test logger uses MOCHA_PARALLEL theme for readable output
- Power Assert is configured for better assertion messages

### Testing Best Practices

- Use exact assertions: Assert on exact values (e.g., `assert(count == 1L)`) rather than ranges (e.g., `assert(count > 0)`)
- When querying counts from the database, use `COUNT(*) as count` and extract the value properly
- Only include custom assertion messages when they add valuable context beyond what the assertion itself would show
  - Good: `assert(hasTable) { "Table 'users' not found. Available tables: $tableList" }` (adds context)
  - Bad: `assert(count == 1L) { "Expected 1 but got $count" }` (redundant with assertion failure)
- When testing template databases, use unique schemas with timestamps to avoid test interference

## Publishing

The project publishes to Sonatype/Maven Central:
- Group ID: `com.christophsturm.isolationchamber`
- Current version: 0.0.1
- Includes source and javadoc jars
- Requires signing for releases