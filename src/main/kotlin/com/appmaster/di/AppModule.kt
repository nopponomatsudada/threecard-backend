package com.appmaster.di

import io.ktor.server.application.*
import org.koin.dsl.module

/**
 * Main Koin module for dependency injection.
 *
 * Add your dependencies here as you implement them.
 * Example:
 * ```
 * single<UserRepository> { UserRepositoryImpl() }
 * single { GetUserUseCase(get()) }
 * ```
 */
fun appModule(environment: ApplicationEnvironment) = module {
    // Environment configuration
    single { environment }

    // Add your dependencies here
    // Repository implementations
    // single<UserRepository> { UserRepositoryImpl() }

    // Use cases
    // single { GetUserUseCase(get()) }
}
