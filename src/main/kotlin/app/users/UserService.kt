package app.users

import app.core.Constants.defaultProblems
import app.core.Loggers.i
import app.core.database.EntityModel.Companion.MODEL_FIELD_FIELD
import app.core.database.EntityModel.Companion.MODEL_FIELD_MESSAGE
import app.core.database.EntityModel.Companion.MODEL_FIELD_OBJECTNAME
import app.core.database.EntityModel.Members.withId
import app.core.web.HttpUtils.badResponse
import app.core.web.HttpUtils.validator
import app.core.web.ProblemsModel
import app.users.User.Attributes.EMAIL_ATTR
import app.users.User.Attributes.LOGIN_ATTR
import app.users.User.Attributes.PASSWORD_ATTR
import app.users.UserController.UserRestApiRoutes.API_ACTIVATE
import app.users.UserController.UserRestApiRoutes.API_SIGNUP
import app.users.UserController.UserRestApiRoutes.API_USERS
import app.users.UserDao.signupAvailability
import app.users.UserDao.signupDao
import app.users.UserDao.signupToUser
import app.users.signup.Signup
import app.users.signup.UserActivation
import app.users.signup.UserActivation.Attributes.ACTIVATION_KEY_ATTR
import app.users.signup.UserActivationDao.activateDao
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import org.springframework.http.ProblemDetail
import org.springframework.http.ProblemDetail.forStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerWebExchange
import java.net.URI
import java.nio.channels.AlreadyBoundException
import java.util.UUID.randomUUID

@Service
class UserService(private val context: ApplicationContext) {
    suspend fun signupService(signup: Signup): Either<Throwable, User> = try {
        context.signupToUser(signup).run {
            (this to context).signupDao()
                .mapLeft { return Exception("Unable to save user with id").left() }
                .map { return withId(it.first).right() }
        }
    } catch (t: Throwable) {
        t.left()
    }

    suspend fun signupAvailability(signup: Signup)
            : Either<Throwable, Triple<Boolean, Boolean, Boolean>> = try {
        (signup to context)
            .signupAvailability()
            .onRight { it.right() }
            .onLeft { it.left() }
    } catch (ex: Throwable) {
        ex.left()
    }

    suspend fun signupRequest(signup: Signup, exchange: ServerWebExchange)
            : ResponseEntity<ProblemDetail> = signup
        .validate(exchange)
        .run {
            "signup attempt: ${this@run} ${signup.login} ${signup.email}".run(::i)
            if (isNotEmpty()) return signupProblems.badResponse(this)
        }.run {
            signupAvailability(signup).map {
                return when (it) {
                    SIGNUP_LOGIN_AND_EMAIL_NOT_AVAILABLE -> signupProblems.badResponseLoginAndEmailIsNotAvailable
                    SIGNUP_LOGIN_NOT_AVAILABLE -> signupProblems.badResponseLoginIsNotAvailable
                    SIGNUP_EMAIL_NOT_AVAILABLE -> signupProblems.badResponseEmailIsNotAvailable
                    else -> {
                        signupService(signup).run { CREATED.run(::ResponseEntity) }
                    }
                }
            }
            SERVICE_UNAVAILABLE.run(::ResponseEntity)
        }

    suspend fun activateRequest(
        key: String,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> = UserActivation(
        id = randomUUID(),
        activationKey = key
    ).validate(exchange).run {
        "User activation attempt with key: $this".run(::i)
        if (isNotEmpty()) return activateProblems
            .copy(path = "$API_USERS$API_ACTIVATE")
            .badResponse(this)
    }.run {
        try {
            when (ONE_ROW_UPDATED) {
                activateService(key) -> OK.run(::ResponseEntity)
                else -> signupProblems
                    .copy(path = "$API_USERS$API_ACTIVATE")
                    .exceptionProblem(
                        AlreadyBoundException(),
                        UNPROCESSABLE_ENTITY,
                        UserActivation::class.java
                    )
            }
        } catch (ise: IllegalStateException) {
            signupProblems
                .copy(path = "$API_USERS$API_ACTIVATE")
                .exceptionProblem(
                    ise,
                    EXPECTATION_FAILED,
                    UserActivation::class.java
                )
        } catch (iae: IllegalArgumentException) {
            signupProblems
                .copy(path = "$API_USERS$API_ACTIVATE")
                .exceptionProblem(
                    iae,
                    PRECONDITION_FAILED,
                    UserActivation::class.java
                )
        }
    }

    suspend fun activateService(key: String): Long = context.activateDao(key)
        .getOrElse { throw IllegalStateException("Error activating user with key: $key", it) }
        .takeIf { it == ONE_ROW_UPDATED }
        ?: throw IllegalArgumentException("Activation failed: No user was activated for key: $key")

    companion object {
        const val ONE_ROW_UPDATED = 1L

        @JvmStatic
        val SIGNUP_AVAILABLE = Triple(true, true, true)

        @JvmStatic
        val SIGNUP_LOGIN_NOT_AVAILABLE = Triple(false, true, false)

        @JvmStatic
        val SIGNUP_EMAIL_NOT_AVAILABLE = Triple(false, false, true)

        @JvmStatic
        val SIGNUP_LOGIN_AND_EMAIL_NOT_AVAILABLE = Triple(false, false, false)

        @JvmStatic
        fun Signup.validate(
            exchange: ServerWebExchange
        ): Set<Map<String, String?>> = exchange.validator.run {
            setOf(
                PASSWORD_ATTR,
                EMAIL_ATTR,
                LOGIN_ATTR,
            ).map { it to validateProperty(this@validate, it) }
                .flatMap { (first, second) ->
                    second.map {
                        mapOf<String, String?>(
                            MODEL_FIELD_OBJECTNAME to Signup.objectName,
                            MODEL_FIELD_FIELD to first,
                            MODEL_FIELD_MESSAGE to it.message
                        )
                    }
                }.toSet()
        }


        @JvmStatic
        fun UserActivation.validate(
            exchange: ServerWebExchange
        ): Set<Map<String, String?>> = exchange.validator.run {
            "Validate UserActivation : ${this@validate}".run(::i)
            setOf(ACTIVATION_KEY_ATTR)
                .map { it to validateProperty(this@validate, it) }
                .flatMap { (first, second) ->
                    second.map {
                        mapOf<String, String?>(
                            MODEL_FIELD_OBJECTNAME to UserActivation.objectName,
                            MODEL_FIELD_FIELD to first,
                            MODEL_FIELD_MESSAGE to it.message
                        )
                    }
                }.toSet()
        }

        @JvmStatic
        val signupProblems: ProblemsModel = defaultProblems.copy(path = "$API_USERS$API_SIGNUP")

        @JvmStatic
        val activateProblems: ProblemsModel = defaultProblems.copy(path = "$API_USERS$API_ACTIVATE")

        @JvmStatic
        fun ProblemsModel.exceptionProblem(
            ex: Throwable,
            status: HttpStatus,
            obj: Class<*>
        ): ResponseEntity<ProblemDetail> =
            forStatus(status).apply {
                type = URI(activateProblems.type)
                setProperty("path", path)
                setProperty("message", message)
                setProperty(
                    "fieldErrors", setOf(
                        mapOf(
                            MODEL_FIELD_OBJECTNAME to obj.simpleName.run {
                                replaceFirst(
                                    first(),
                                    first().lowercaseChar()
                                )
                            },
                            MODEL_FIELD_MESSAGE to ex.message
                        )
                    )
                )
            }.run { status(status).body(this) }


        @JvmStatic
        val ProblemsModel.badResponseLoginAndEmailIsNotAvailable: ResponseEntity<ProblemDetail>
            get() = badResponse(
                setOf(
                    mapOf(
                        MODEL_FIELD_OBJECTNAME to User.objectName,
                        MODEL_FIELD_FIELD to User.Fields.LOGIN_FIELD,
                        MODEL_FIELD_FIELD to User.Fields.EMAIL_FIELD,
                        MODEL_FIELD_MESSAGE to "Login name already used and email is already in use!!"
                    )
                )
            )

        @JvmStatic
        val ProblemsModel.badResponseLoginIsNotAvailable: ResponseEntity<ProblemDetail>
            get() = badResponse(
                setOf(
                    mapOf(
                        MODEL_FIELD_OBJECTNAME to Signup.objectName,
                        MODEL_FIELD_FIELD to User.Fields.LOGIN_FIELD,
                        MODEL_FIELD_MESSAGE to "Login name already used!"
                    )
                )
            )

        @JvmStatic
        val ProblemsModel.badResponseEmailIsNotAvailable: ResponseEntity<ProblemDetail>
            get() = badResponse(
                setOf(
                    mapOf(
                        MODEL_FIELD_OBJECTNAME to Signup.objectName,
                        MODEL_FIELD_FIELD to User.Fields.EMAIL_FIELD,
                        MODEL_FIELD_MESSAGE to "Email is already in use!"
                    )
                )
            )
    }
}