package com.christophsturm.isolationchamber.integresql

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import restaurant.HttpStatus
import restaurant.client.HttpClientConfig
import restaurant.client.Java11HttpClient
import kotlin.time.Duration.Companion.seconds
import io.github.oshai.kotlinlogging.KotlinLogging

class IntegresqlClientException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

private val logger = KotlinLogging.logger {}

class IntegresqlClient(config: Config) {
    private val client = Java11HttpClient(HttpClientConfig(config.baseUrl, timeout = 20.seconds))
    private val hostIsLocalHost = config.hostIsLocalHost
    private val knownHashes = ConcurrentHashMap.newKeySet<String>()!!
    private val inProgress = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    data class TestDbId(val hash: String, val id: Long)

    private val returnedTestDatabases = ConcurrentHashMap.newKeySet<TestDbId>()!!

    suspend fun dbForHash(
        hashCode: String,
        dbInitFunction: suspend (DatabaseConfig) -> Unit
    ): TestDatabase {
        if (hashCode.length != 32)
            throw IllegalArgumentException("it seems integresql only supports hash codes with length 32")
        val seenBefore = !knownHashes.add(hashCode)
        try {
            if (!seenBefore) {
                val deferred = CompletableDeferred<Unit>()
                inProgress.put(hashCode, deferred)
                val templateResponse =
                    client.send("/api/v1/templates") { // language=JSON
                        post("""{"hash":"$hashCode"}""")
                        addHeader("Content-Type", "application/json")
                    }
                val shouldPopulateTemplateDb = templateResponse.statusCode() == 200
                if (shouldPopulateTemplateDb) {
                    val templateDatabase =
                        deserialize(TemplateDatabase.serializer(), templateResponse.body!!)
                    val config = templateDatabase.database.config
                    try {
                        dbInitFunction(
                            if (hostIsLocalHost) config.copy(host = "localhost") else config
                        )
                    } catch (e: Exception) {
                        throw IntegresqlClientException("error in template init method", e)
                    }

                    val finalizeTemplateResponse =
                        client.send("/api/v1/templates/$hashCode") { put() }
                    if (!finalizeTemplateResponse.isOk) {
                        throw IntegresqlClientException(
                            "unexpected response $finalizeTemplateResponse"
                        )
                    }
                    deferred.complete(Unit)
                    inProgress.remove(hashCode)
                    // locked means that the template is already created
                } else if (templateResponse.statusCode != HttpStatus.LOCKED_423) {
                    throw IntegresqlClientException("unexpected response $templateResponse")
                }
            }
        } catch (e: IntegresqlClientException) {
            // if this block throws an error we try to recover, and we delete the template db
            // because it is not finished
            logger.error(e) { "Error during template creation for hash $hashCode, removing from known hashes and deleting incomplete template" }
            knownHashes.remove(hashCode)
            inProgress.remove(hashCode)
            client.send("/api/v1/templates/$hashCode") { delete() }
            throw e
        }
        var getResponse = client.send("/api/v1/templates/$hashCode/tests")
        // if we did not call the template api because of a known hash, the init could still be in
        // progress,
        // so we retry the get if it fails.
        if (getResponse.statusCode == HttpStatus.NOT_FOUND_404) {
            if (seenBefore) {
                inProgress[hashCode]?.await()
                getResponse = client.send("/api/v1/templates/$hashCode/tests")
            }
            if (getResponse.statusCode == HttpStatus.NOT_FOUND_404)
                throw IntegresqlClientException(
                    "template for hashcode $hashCode not found. seen before: $seenBefore"
                )
        }

        if (!getResponse.isOk) throw IntegresqlClientException("error returned: $getResponse")
        val db = deserialize(TestDatabase.serializer(), getResponse.body!!)
        returnedTestDatabases.add(TestDbId(hashCode, db.id))
        return if (hostIsLocalHost)
            db.copy(
                database = db.database.copy(config = db.database.config.copy(host = "localhost"))
            )
        else db
    }

    private fun <T> deserialize(deserializer: KSerializer<T>, string: String): T {
        if (string.isEmpty())
            throw IntegresqlClientException("unexpected empty response")
        return try {
            Json.decodeFromString(deserializer, string)
        } catch (e: SerializationException) {
            throw IntegresqlClientException("unexpected response: $string", e)
        }
    }

    suspend fun cleanUp() {
        coroutineScope {
            returnedTestDatabases.map { (hash, id) ->
                async { client.send("/api/v1/templates/$hash/tests/$id") { delete() } }
            }.awaitAll()
        }
    }

    data class Config(val baseUrl: String, val hostIsLocalHost: Boolean = false)
}
