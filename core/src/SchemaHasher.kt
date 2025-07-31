package com.christophsturm.isolationchamber

object SchemaHasher {
    @OptIn(ExperimentalStdlibApi::class)
    fun getHash(schema: String?): String {
        if (schema == null) return "1234567890abcdef1234567890abcdef"
        val hexString = schema.hashCode().toHexString()
        return hexString + hexString + hexString + hexString
    }
}