package com.christophsturm.isolationchamber.integresql

import com.christophsturm.isolationchamber.PostgresDb
import com.christophsturm.isolationchamber.SchemaHasher
import failgood.Test
import failgood.testsAbout

@Test
class IntegresqlPostgresqlFactoryTest {
    val tests =
        testsAbout(IntegresqlPostgresqlFactory::class) {
            test("hash is 32 chars long") {
                val hash = SchemaHasher.getHash("mystring")
                assert(hash.length == 32)
                assert(hash.toCharArray().all { it.isLowerCase() || it.isDigit() })
            }
            test("works without schema") {
                IntegresqlPostgresqlFactory().preparePostgresDB(null)
            }
            test("works with schema") {
                IntegresqlPostgresqlFactory().preparePostgresDB("select 1")
            }
        }
}
