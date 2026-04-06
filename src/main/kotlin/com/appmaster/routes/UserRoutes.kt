@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.UserProfileResponse
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileUseCase by inject<GetMyProfileUseCase>()

    authenticate("jwt") {
        route("/api/v1/users") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim(TokenProvider.CLAIM_USER_ID)?.asString()
                    ?: throw DomainException(DomainError.Unauthorized)

                val user = getMyProfileUseCase(UserId(userId))
                call.respond(
                    ApiResponse(
                        data = UserProfileResponse(
                            id = user.id.value,
                            displayId = user.displayId.value,
                            bestCount = 0,
                            collectionCount = 0,
                            plan = user.plan.name.lowercase(),
                            createdAt = user.createdAt.toString()
                        )
                    )
                )
            }
        }
    }
}
