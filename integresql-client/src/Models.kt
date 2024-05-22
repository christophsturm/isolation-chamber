package com.christophsturm.isolationchamber.integresql

import kotlinx.serialization.Serializable

@Serializable data class TemplateDatabase(val database: Database)

@Serializable data class Database(val templateHash: String, val config: DatabaseConfig)

@Serializable data class TestDatabase(val id: Long, val database: Database)

@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: String,
    val additionalParams: Map<String, String>? = null
)

@Serializable data class InitializeTemplateRequest(val hash: String)
