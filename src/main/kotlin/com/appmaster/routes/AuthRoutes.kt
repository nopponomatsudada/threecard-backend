@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.domain.usecase.auth.LogoutUseCase
import com.appmaster.domain.usecase.auth.RefreshTokenUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.AuthResponseData
import com.appmaster.routes.dto.AuthUserDto
import com.appmaster.routes.dto.DeviceAuthRequest
import com.appmaster.routes.dto.RefreshTokenRequest
import com.appmaster.routes.dto.RefreshTokenResponse
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.time.Clock

fun Route.authRoutes() {
    val deviceAuthUseCase by inject<DeviceAuthUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()
    val logoutUseCase by inject<LogoutUseCase>()
    val tokenProvider by inject<TokenProvider>()

    route("/api/v1/auth") {
        rateLimit(RateLimitName("auth")) {

            post("/device") {
                val request = try {
                    call.receive<DeviceAuthRequest>()
                } catch (e: Exception) {
                    throw DomainException(DomainError.ValidationError("リクエストが不正です"))
                }

                val result = try {
                    deviceAuthUseCase(request.deviceId, request.deviceSecret)
                } catch (e: DomainException) {
                    AuthLogger.event(
                        call,
                        event = if (e.error is DomainError.InvalidDeviceCredentials)
                            "auth.device.login.failure"
                        else "auth.device.error",
                        deviceId = request.deviceId,
                        extra = mapOf("code" to e.error.code)
                    )
                    throw e
                }

                AuthLogger.event(
                    call,
                    event = if (result.isNewUser) "auth.device.bootstrap"
                    else "auth.device.login.success",
                    deviceId = request.deviceId,
                    userId = result.user.id.value
                )

                val statusCode = if (result.isNewUser) HttpStatusCode.Created else HttpStatusCode.OK
                call.respond(
                    statusCode,
                    ApiResponse(
                        data = AuthResponseData(
                            accessToken = result.accessToken.token,
                            refreshToken = result.refreshToken.plain,
                            expiresIn = (result.accessToken.expiresAt - Clock.System.now()).inWholeSeconds,
                            deviceSecret = result.deviceSecret,
                            user = AuthUserDto(
                                id = result.user.id.value,
                                displayId = result.user.displayId.value
                            )
                        )
                    )
                )
            }

            post("/refresh") {
                val request = try {
                    call.receive<RefreshTokenRequest>()
                } catch (e: Exception) {
                    throw DomainException(DomainError.InvalidRefreshToken)
                }

                val result = try {
                    refreshTokenUseCase(request.refreshToken)
                } catch (e: DomainException) {
                    AuthLogger.event(
                        call,
                        event = "auth.refresh.failure",
                        extra = mapOf("code" to e.error.code)
                    )
                    throw e
                }

                AuthLogger.event(call, event = "auth.refresh.success")

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        data = RefreshTokenResponse(
                            accessToken = result.accessToken.token,
                            refreshToken = result.refreshToken.plain,
                            expiresIn = (result.accessToken.expiresAt - Clock.System.now()).inWholeSeconds
                        )
                    )
                )
            }

            authenticate("jwt") {
                post("/logout") {
                    val userId = call.requireUserId()
                    val authHeader = call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single
                    val rawToken = authHeader?.blob

                    val claims = rawToken?.let { tokenProvider.parseClaims(it) }
                        ?: throw DomainException(DomainError.Unauthorized)

                    logoutUseCase(userId, claims)

                    AuthLogger.event(
                        call,
                        event = "auth.logout",
                        userId = userId.value
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
