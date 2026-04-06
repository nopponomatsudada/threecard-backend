package com.appmaster.routes

import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.AuthResponseData
import com.appmaster.routes.dto.AuthUserDto
import com.appmaster.routes.dto.DeviceAuthRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ratelimit.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val deviceAuthUseCase by inject<DeviceAuthUseCase>()
    val tokenProvider by inject<TokenProvider>()

    route("/api/v1/auth") {
        rateLimit(RateLimitName("auth")) {
            post("/device") {
                val request = call.receive<DeviceAuthRequest>()
                val result = deviceAuthUseCase(request.deviceId)
                val token = tokenProvider.generateAccessToken(result.user)

                val statusCode = if (result.isNewUser) HttpStatusCode.Created else HttpStatusCode.OK
                call.respond(
                    statusCode,
                    ApiResponse(
                        data = AuthResponseData(
                            accessToken = token,
                            user = AuthUserDto(
                                id = result.user.id.value,
                                displayId = result.user.displayId.value
                            )
                        )
                    )
                )
            }
        }
    }
}
