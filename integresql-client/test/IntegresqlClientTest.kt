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
import kotlin.test.assertNotNull

@Test
class IntegresqlClientTest {
    val tests =
        testsAbout(IntegresqlClient::class) {
            var postTemplatesRequest = CompletableDeferred<Request>()
            val getTemplatesRequest = CompletableDeferred<Request>()
            val databaseConfig =
                DatabaseConfig("host", 123, "username", "password", "database", mapOf())
            val hashCode = "1234567890abcdef1234567890abcdef"
            val database = Database(hashCode, databaseConfig)
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
                route(Method.PUT, "/api/v1/templates/$hashCode") { _, _ -> response() }
                route(Method.GET, "/api/v1/templates/$hashCode/tests") { request, _ ->
                    getTemplatesRequest.complete(request)
                    response(
                        Json.encodeToString(TestDatabase.serializer(), TestDatabase(1234, database))
                    )
                }
            }
            test("happy path: calls the init callback and returns a test database") {
                val databaseConfigDeferred = CompletableDeferred<DatabaseConfig>()
                println(mock.baseUrl)
                IntegresqlClient(IntegresqlClient.Config(mock.baseUrl)).dbForHash(hashCode) { db ->
                    databaseConfigDeferred.complete(db)
                }
                assert(withTimeout(1.seconds) { databaseConfigDeferred.await() } == databaseConfig)
            }
            test("does not post to the template endpoint for a known hash") {
                val integresqlClient = IntegresqlClient(IntegresqlClient.Config(mock.baseUrl))
                integresqlClient.dbForHash(hashCode) {}
                postTemplatesRequest = CompletableDeferred()
                val databaseConfigDeferred = CompletableDeferred<DatabaseConfig>()
                integresqlClient.dbForHash(hashCode) { databaseConfigDeferred.complete(it) }
                assert(!databaseConfigDeferred.isCompleted)
                assert(!postTemplatesRequest.isCompleted)
            }

            describe("error handling") {
                test("throws IntegresqlClientException when template init fails") {
                    val errorMock = Restaurant {
                        route(Method.POST, "/api/v1/templates") { _, _ ->
                            response(
                                Json.encodeToString(
                                    TemplateDatabase.serializer(),
                                    TemplateDatabase(database)
                                )
                            )
                        }
                    }
                    val client = IntegresqlClient(IntegresqlClient.Config(errorMock.baseUrl))
                    val exception = assertNotNull(kotlin.runCatching { 
                        client.dbForHash(hashCode) { throw RuntimeException("init failed") }
                    }.exceptionOrNull())
                    assert(exception is IntegresqlClientException)
                    assert(exception.message == "error in template init method")
                    assert(exception.cause?.message == "init failed")
                }

                test("throws IntegresqlClientException for unexpected template response") {
                    val errorMock = Restaurant {
                        route(Method.POST, "/api/v1/templates") { _, _ ->
                            response(500, "Internal Server Error")
                        }
                    }
                    val client = IntegresqlClient(IntegresqlClient.Config(errorMock.baseUrl))
                    val exception = assertNotNull(kotlin.runCatching {
                        client.dbForHash(hashCode) {}
                    }.exceptionOrNull())
                    assert(exception is IntegresqlClientException)
                }

                test("throws IntegresqlClientException for malformed JSON response") {
                    val errorMock = Restaurant {
                        route(Method.POST, "/api/v1/templates") { _, _ ->
                            response("not valid json")
                        }
                        route(Method.PUT, "/api/v1/templates/$hashCode") { _, _ -> response() }
                        route(Method.GET, "/api/v1/templates/$hashCode/tests") { _, _ ->
                            response("not valid json")
                        }
                    }
                    val client = IntegresqlClient(IntegresqlClient.Config(errorMock.baseUrl))
                    val exception = assertNotNull(kotlin.runCatching {
                        client.dbForHash(hashCode) {}
                    }.exceptionOrNull())
                    assert(exception is IntegresqlClientException)
                    assert(exception.message?.contains("unexpected response") == true)
                }

                test("throws IntegresqlClientException for empty response") {
                    val errorMock = Restaurant {
                        route(Method.POST, "/api/v1/templates") { _, _ ->
                            response("")
                        }
                    }
                    val client = IntegresqlClient(IntegresqlClient.Config(errorMock.baseUrl))
                    val exception = assertNotNull(kotlin.runCatching {
                        client.dbForHash(hashCode) {}
                    }.exceptionOrNull())
                    assert(exception is IntegresqlClientException)
                    assert(exception.message == "unexpected empty response")
                }

                test("cleans up on failure during template creation") {
                    val deleteRequested = CompletableDeferred<Boolean>()
                    val errorMock = Restaurant {
                        route(Method.POST, "/api/v1/templates") { _, _ ->
                            response(
                                Json.encodeToString(
                                    TemplateDatabase.serializer(),
                                    TemplateDatabase(database)
                                )
                            )
                        }
                        route(Method.PUT, "/api/v1/templates/$hashCode") { _, _ ->
                            response(500, "Failed to finalize")
                        }
                        route(Method.DELETE, "/api/v1/templates/$hashCode") { _, _ ->
                            deleteRequested.complete(true)
                            response()
                        }
                    }
                    val client = IntegresqlClient(IntegresqlClient.Config(errorMock.baseUrl))
                    val exception = assertNotNull(kotlin.runCatching {
                        client.dbForHash(hashCode) {}
                    }.exceptionOrNull())
                    assert(exception is IntegresqlClientException)
                    assert(withTimeout(1.seconds) { deleteRequested.await() })
                }
            }
        }
}
