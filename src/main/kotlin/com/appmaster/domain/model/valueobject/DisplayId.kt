package com.appmaster.domain.model.valueobject

import java.security.SecureRandom

@JvmInline
value class DisplayId(val value: String) {
    companion object {
        private val CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val secureRandom = SecureRandom()

        fun generate(): DisplayId {
            val random = (1..6).map { CHARS[secureRandom.nextInt(CHARS.length)] }.joinToString("")
            return DisplayId("u_$random")
        }
    }
}
