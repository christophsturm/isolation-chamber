package com.christophsturm.isolationchamber.integresql.functional

import com.christophsturm.isolationchamber.integresql.IntegresqlClient
import failgood.Test
import failgood.tests
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll

@Test
class IntegresqlFunctionalTest {
    val tests = tests {
        test("it works under load") {
            val client = IntegresqlClient(IntegresqlClient.Config("http://localhost:5000"))
            coroutineScope {
                (1..50).map {async {client.dbForHash(generateRandomHexString()) {} }}.joinAll()
                client.cleanUp()
            }
        }
    }
}
fun generateRandomHexString(length: Int = 32) = buildString(length) {
    val charPool : List<Char> = ('a'..'f') + ('0'..'9')
    repeat(length) {
        val randomIndex = (charPool.indices).random()
        append(charPool[randomIndex])
    }
}
