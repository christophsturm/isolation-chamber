package com.christophsturm.isolationchamber.postgresql.docker

import com.christophsturm.isolationchamber.PostgresDb
import com.christophsturm.isolationchamber.PostgresqlFactory
import com.christophsturm.isolationchamber.SchemaHasher
import io.vertx.kotlin.coroutines.coAwait
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DockerPostgresqlFactory(
    val dockerImage: String,
    private val databasePrefix: String,
    private val reuse: Boolean
) : PostgresqlFactory {
    override val name: String
        get() = dockerImage

    override fun prepare() {
        dockerContainer
    }

    private val dockerContainer: PostgresqlContainer by lazy {
        PostgresqlContainer(dockerImage, databasePrefix, reuse)
    }

    override suspend fun preparePostgresDB(
        schema: String?
    ): PostgresDb = dockerContainer.preparePostgresDB(schema)

    override suspend fun cleanUp() {
        dockerContainer.cleanUp()
    }
}

private class PostgresqlContainer(
    dockerImage: String,
    private val databasePrefix: String,
    private val reuse: Boolean
) {
    private val templateDatabases = ConcurrentHashMap<String, String>()
    private val testDatabases = ConcurrentHashMap.newKeySet<String>()
    private val templateCreationLocks = ConcurrentHashMap<String, Mutex>()
    private val dockerContainer: org.testcontainers.containers.PostgreSQLContainer<Nothing> =
        measureTimedValue {
            org.testcontainers.containers.PostgreSQLContainer<Nothing>(dockerImage).apply {
                setCommand("postgres", "-c", "fsync=off", "-c", "max_connections=20000")
                withReuse(reuse)
                start()
            }
        }
            .also { println("creating docker container took ${it.duration}") }
            .value
    private val vertx = io.vertx.core.Vertx.vertx()
    private val host = dockerContainer.host.let { if (it == "localhost") "127.0.0.1" else it }!!
    private val port = dockerContainer.getMappedPort(5432)!!
    private val connectOptions =
        io.vertx.pgclient.PgConnectOptions()
            .setPort(port)
            .setHost(host)
            .setDatabase("postgres")
            .setUser("test")
            .setPassword("test")
    private val pool =
        io.vertx.pgclient.PgBuilder.pool()
            .with(io.vertx.sqlclient.PoolOptions().setMaxSize(5))
            .connectingTo(connectOptions)
            .using(vertx)
            .build()!!

    suspend fun preparePostgresDB(schema: String?): PostgresDb {
        val hash = SchemaHasher.getHash(schema)
        val templateDbName = templateDatabases.computeIfAbsent(hash) { h ->
            "template_${databasePrefix}_${h.take(8)}"
        }

        // Ensure template exists
        val mutex = templateCreationLocks.computeIfAbsent(hash) { Mutex() }
        mutex.withLock {
            if (!doesDatabaseExist(templateDbName)) {
                createTemplateDatabase(templateDbName, schema)
            }
        }

        // Create test database from template
        val uuid = UUID.randomUUID().toString().take(5)
        val databaseName = "$databasePrefix$uuid".replace("-", "_")
        testDatabases.add(databaseName)

        pool.query("CREATE DATABASE $databaseName TEMPLATE $templateDbName").execute().coAwait()

        return PostgresDbWithPool(databaseName, port, host, pool)
    }

    private suspend fun doesDatabaseExist(dbName: String): Boolean {
        val result = pool.query("SELECT 1 FROM pg_database WHERE datname = '$dbName'").execute().coAwait()
        return result.size() > 0
    }

    private suspend fun createTemplateDatabase(templateDbName: String, schema: String?) {
        pool.query("CREATE DATABASE $templateDbName").execute().coAwait()

        if (schema != null) {
            val ddlConnection = io.vertx.pgclient.PgBuilder.client()
                .using(vertx)
                .connectingTo(io.vertx.pgclient.PgConnectOptions(connectOptions).setDatabase(templateDbName))
                .build()!!
            ddlConnection.query(schema).execute().coAwait()
            ddlConnection.close().coAwait()
        }
    }

    suspend fun cleanUp() {
        // Drop all test databases
        testDatabases.forEach { dbName ->
            try {
                pool.query("DROP DATABASE IF EXISTS $dbName").execute().coAwait()
            } catch (_: Exception) {
                // Ignore errors during cleanup
            }
        }
        testDatabases.clear()
    }
}

suspend fun postgresDb(
    prefix: String,
    port: Int,
    host: String,
    pool: io.vertx.sqlclient.Pool,
    vertx: io.vertx.core.Vertx,
    connectOptions: io.vertx.pgclient.PgConnectOptions,
    schema: String?
): PostgresDb {
    val uuid = UUID.randomUUID().toString().take(5)
    val databaseName = "$prefix$uuid".replace("-", "_")
    val postgresDb = PostgresDbWithPool(databaseName, port, host, pool)
    postgresDb.createDb()
    if (schema != null) {
        val ddlConnection =
            io.vertx.pgclient.PgBuilder.client()
                .using(vertx)
                .connectingTo(io.vertx.pgclient.PgConnectOptions(connectOptions).setDatabase(databaseName))
                .build()!!
        ddlConnection.query(schema).execute().coAwait()

        ddlConnection.close().coAwait()
    }

    return postgresDb
}

data class PostgresDbWithPool(
    override val databaseName: String,
    override val port: Int,
    override val host: String,
    val pool: io.vertx.sqlclient.Pool,
    override val userName: String = "test",
    override val password: String = "test"
) : AutoCloseable, PostgresDb {
    suspend fun createDb() {
        executeSql("create database $databaseName")
    }

    private suspend fun dropDb() {
        executeSql("drop database $databaseName")
    }

    private suspend fun executeSql(command: String) {
        pool.query(command).execute().coAwait()
    }

    override fun close() {
        kotlinx.coroutines.runBlocking { dropDb() }
    }
}
