package com.christophsturm.isolationchamber.integresql

/*
type TemplateDatabase struct {
	Database `json:"database"`
}

type Database struct {
	TemplateHash string         `json:"templateHash"`
	Config       DatabaseConfig `json:"config"`
}

type DatabaseConfig struct {
	Host             string            `json:"host"`
	Port             int               `json:"port"`
	Username         string            `json:"username"`
	Password         string            `json:"password"`
	Database         string            `json:"database"`
	AdditionalParams map[string]string `json:"additionalParams,omitempty"` // Optional additional connection parameters mapped into the connection string
}

 */

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
