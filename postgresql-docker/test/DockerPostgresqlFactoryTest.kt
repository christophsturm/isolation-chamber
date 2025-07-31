package com.christophsturm.isolationchamber.postgresql.docker

import com.christophsturm.isolationchamber.SchemaHasher
import failgood.Test
import failgood.testsAbout
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Test
class DockerPostgresqlFactoryTest {
    val tests = testsAbout(DockerPostgresqlFactory::class) {
        suspend fun connectToDb(dbName: String, port: Int, host: String, userName: String, password: String): io.vertx.sqlclient.SqlClient {
            val vertx = Vertx.vertx()
            val client = PgBuilder.client()
                .using(vertx)
                .connectingTo(
                    PgConnectOptions()
                        .setPort(port)
                        .setHost(host)
                        .setDatabase(dbName)
                        .setUser(userName)
                        .setPassword(password)
                )
                .build()!!
            return client
        }
        
        val factory = DockerPostgresqlFactory(
            "postgres:16.3-alpine", 
            "test_",
            reuse = true
        )
        
        test("creates database without schema") {
            val db = factory.preparePostgresDB(null)
            assert(db.databaseName.startsWith("test_"))
            db.close()
        }
        
        test("creates database with schema") {
            val schema = """
                CREATE TABLE users (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100)
                );
                INSERT INTO users (name) VALUES ('test user');
            """.trimIndent()
            
            val db = factory.preparePostgresDB(schema)
            
            // Verify schema was applied
            val client = connectToDb(db.databaseName, db.port, db.host, db.userName, db.password)
            
            val result = client.query("SELECT COUNT(*) as count FROM users").execute().coAwait()
            val userCount = result.iterator().next().getLong("count")
            assert(userCount == 1L)
            
            client.close().coAwait()
            db.close()
        }
        
        test("reuses template for same schema") {
            // Use a unique schema to ensure we get a fresh template
            val uniqueId = System.currentTimeMillis()
            val schema = "CREATE TABLE test_table_$uniqueId (id INT);"
            val hash = SchemaHasher.getHash(schema)
            val templateDbName = "template_test__${hash.take(8)}"
            
            // First database creation should create template
            val db1 = factory.preparePostgresDB(schema)
            
            // Check exactly one template exists with our expected name
            val adminClient = connectToDb("postgres", db1.port, db1.host, db1.userName, db1.password)
            val templateCount = adminClient.query(
                "SELECT COUNT(*) as count FROM pg_database WHERE datname = '$templateDbName'"
            ).execute().coAwait()
            val count = templateCount.iterator().next().getInteger("count")
            assert(count == 1) { "Expected exactly 1 template database named $templateDbName, but found $count" }
            adminClient.close().coAwait()
            
            // Connect to the template database to add a marker
            val templateClient = connectToDb(templateDbName, db1.port, db1.host, db1.userName, db1.password)
            templateClient.query("CREATE TABLE _template_marker (created_at TIMESTAMP DEFAULT NOW())").execute().coAwait()
            
            // Verify marker was created in template
            val markerCheck = templateClient.query("SELECT COUNT(*) as count FROM _template_marker").execute().coAwait()
            val markerCount = markerCheck.iterator().next().getLong("count")
            assert(markerCount == 0L)
            
            templateClient.close().coAwait()
            
            db1.close()
            
            // Second database creation should reuse template
            val db2 = factory.preparePostgresDB(schema)
            
            // Verify marker exists in new database (proving it was copied from template)
            val db2Client = connectToDb(db2.databaseName, db2.port, db2.host, db2.userName, db2.password)
            
            // First check what tables exist
            val tables = db2Client.query("SELECT tablename FROM pg_tables WHERE schemaname = 'public'").execute().coAwait()
            val tableList = buildString {
                tables.forEach { row ->
                    append(row.getString("tablename"))
                    append(", ")
                }
            }
            
            // Check for marker table
            val hasMarker = db2Client.query("SELECT COUNT(*) as count FROM pg_tables WHERE tablename = '_template_marker' AND schemaname = 'public'").execute().coAwait()
            val markerTableCount = hasMarker.iterator().next().getLong("count")
            assert(markerTableCount == 1L) { "Expected exactly 1 marker table in database created from template, but found $markerTableCount. Tables found: $tableList" }
            
            db2Client.close().coAwait()
            db2.close()
        }
        
        test("uses different templates for different schemas") {
            val schema1 = "CREATE TABLE table1 (id INT);"
            val schema2 = "CREATE TABLE table2 (name VARCHAR(50));"
            
            val db1 = factory.preparePostgresDB(schema1)
            val db2 = factory.preparePostgresDB(schema2)
            
            // Verify different schemas result in different template hashes
            val hash1 = SchemaHasher.getHash(schema1)
            val hash2 = SchemaHasher.getHash(schema2)
            assert(hash1 != hash2)
            
            db1.close()
            db2.close()
        }
        
        test("handles concurrent template creation") {
            val schema = "CREATE TABLE concurrent_test (id SERIAL);"
            
            // Create multiple databases concurrently with same schema
            coroutineScope {
                val dbs = (1..5).map {
                    async {
                        factory.preparePostgresDB(schema)
                    }
                }.awaitAll()
                
                // All should succeed
                assert(dbs.size == 5)
                dbs.forEach { it.close() }
            }
        }
    }
}