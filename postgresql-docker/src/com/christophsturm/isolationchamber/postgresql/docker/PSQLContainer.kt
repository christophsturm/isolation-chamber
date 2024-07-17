package com.christophsturm.isolationchamber.postgresql.docker

import com.christophsturm.isolationchamber.PostgresDb
import com.christophsturm.isolationchamber.PostgresqlFactory
import io.vertx.kotlin.coroutines.coAwait
import java.util.UUID
import kotlin.time.measureTimedValue

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

    // drop all the returned dbs here.
    override suspend fun cleanUp() {}
}

private class PostgresqlContainer(
    dockerImage: String,
    private val databasePrefix: String,
    private val reuse: Boolean
) {
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

    suspend fun preparePostgresDB(schema: String?): PostgresDb =
        postgresDb(databasePrefix, port, host, pool, vertx, connectOptions, schema)
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
