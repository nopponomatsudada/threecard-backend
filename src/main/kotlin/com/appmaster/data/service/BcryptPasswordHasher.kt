package com.appmaster.data.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.appmaster.domain.service.PasswordHasher

/**
 * BCrypt-backed `PasswordHasher`. Cost factor 12 — the project security
 * baseline (`docs/guides/security.md`). 60-char output fits `users.device_secret_hash`.
 */
class BcryptPasswordHasher(
    private val cost: Int = DEFAULT_COST
) : PasswordHasher {

    override fun hash(plain: String): String =
        BCrypt.withDefaults().hashToString(cost, plain.toCharArray())

    override fun verify(plain: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash).verified

    companion object {
        const val DEFAULT_COST: Int = 12
    }
}
