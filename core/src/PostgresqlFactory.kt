package com.christophsturm.isolationchamber


interface PostgresqlFactory {
    val name: String

    /**
     * Optionally call this method to make prepare steps like starting a docker container. This is
     * only an optimization, it is not necessary to call the method.
     */
    fun prepare() {}

    suspend fun preparePostgresDB(schema: String?): PostgresDb

    suspend fun cleanUp()
}

interface PostgresDb : AutoCloseable {
    val databaseName: String
    val port: Int
    val host: String
    val userName: String
    val password: String
}
