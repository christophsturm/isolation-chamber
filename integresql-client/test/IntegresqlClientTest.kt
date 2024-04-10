package com.christophsturm.isolationchamber.integresql

import failgood.Test
import failgood.testsAbout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import restaurant.Method
import restaurant.Request
import restaurant.Restaurant
import restaurant.response

@Test
class IntegresqlClientTest {
    val tests =
        testsAbout(IntegresqlClient::class) {
            var postTemplatesRequest = CompletableDeferred<Request>()
            val getTemplatesRequest = CompletableDeferred<Request>()
            val databaseConfig =
                DatabaseConfig("host", 123, "username", "password", "database", mapOf())
            val database = Database("1234", databaseConfig)
            val mock = Restaurant {
                route(Method.POST, "/api/v1/templates") { request, _ ->
                    postTemplatesRequest.complete(request)
                    response(
                        Json.encodeToString(
                            TemplateDatabase.serializer(),
                            TemplateDatabase(database)
                        )
                    )
                }
                route(Method.PUT, "/api/v1/templates/1234") { _, _ -> response() }
                route(Method.GET, "/api/v1/templates/1234/tests") { request, _ ->
                    getTemplatesRequest.complete(request)
                    response(
                        Json.encodeToString(TestDatabase.serializer(), TestDatabase(1234, database))
                    )
                }
            }
            test("happy path: calls the init callback and returns a test database") {
                val databaseConfigDeferred = CompletableDeferred<DatabaseConfig>()
                println(mock.baseUrl)
                IntegresqlClient(IntegresqlClient.Config(mock.baseUrl)).dbForHash("1234") { db ->
                    databaseConfigDeferred.complete(db)
                }
                assert(withTimeout(1.seconds) { databaseConfigDeferred.await() } == databaseConfig)
            }
            test("does not post to the template endpoint for a known hash") {
                val integresqlClient = IntegresqlClient(IntegresqlClient.Config(mock.baseUrl))
                integresqlClient.dbForHash("1234") {}
                postTemplatesRequest = CompletableDeferred()
                val databaseConfigDeferred = CompletableDeferred<DatabaseConfig>()
                integresqlClient.dbForHash("1234") { databaseConfigDeferred.complete(it) }
                assert(!databaseConfigDeferred.isCompleted)
                assert(!postTemplatesRequest.isCompleted)
            }
        }
}
