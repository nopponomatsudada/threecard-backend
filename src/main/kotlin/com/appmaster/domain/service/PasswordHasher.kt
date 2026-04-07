package com.appmaster.domain.service

/**
 * Hashing primitive for secrets at rest (device secrets, future passwords).
 * Domain-level interface so use cases stay framework-free.
 */
interface PasswordHasher {
    fun hash(plain: String): String
    fun verify(plain: String, hash: String): Boolean
}
