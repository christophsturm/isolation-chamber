package com.christophsturm.isolationchamber.integresql
import com.christophsturm.isolationchamber.PostgresDb
import com.christophsturm.isolationchamber.PostgresqlFactory
import com.christophsturm.isolationchamber.SchemaHasher
import com.christophsturm.isolationchamber.integresql.DatabaseConfig
import com.christophsturm.isolationchamber.integresql.IntegresqlClient
import com.christophsturm.isolationchamber.integresql.TestDatabase
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.PoolOptions
import java.util.concurrent.ConcurrentHashMap

class IntegresqlPostgresqlFactory(
    clientConfig: IntegresqlClient.Config = IntegresqlClient.Config("http://localhost:5001", true)
) : PostgresqlFactory {
    val vertx = Vertx.vertx()
    val integresqlClient = IntegresqlClient(clientConfig)
    val returnedTestDatabases = ConcurrentHashMap.newKeySet<TestDatabase>()
    override val name: String
        get() = "integresql"

    override suspend fun cleanUp() {
        integresqlClient.cleanUp()
    }

    override suspend fun preparePostgresDB(schema: String?): PostgresDb {
        val testDatabase: TestDatabase =
            integresqlClient.dbForHash(SchemaHasher.getHash(schema)) { c: DatabaseConfig ->
                if (schema != null) {

                    val connectOptions =
                        PgConnectOptions()
                            .setPort(c.port)
                            .setHost(c.host)
                            .setDatabase(c.database)
                            .setUser(c.username)
                            .setPassword(c.password)
                    val client =
                        PgBuilder.client()
                            .using(vertx)
                            .with(PoolOptions().setMaxSize(5))
                            .connectingTo(connectOptions)
                            .build()!!
                    client.query(schema).execute().coAwait()
                    client.close().coAwait()
                }
            }
        val config = testDatabase.database.config
        returnedTestDatabases.add(testDatabase)
        return PostgresData(
            config.database,
            config.port,
            config.host,
            config.username,
            config.password
        )
    }
}

data class PostgresData(
    override val databaseName: String,
    override val port: Int,
    override val host: String,
    override val userName: String,
    override val password: String
) : PostgresDb {
    override fun close() {}
}
