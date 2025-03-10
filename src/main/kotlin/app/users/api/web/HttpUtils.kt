package app.users.api.web

import app.users.api.Constants.SPA_NEGATED_REGEX
import app.users.api.Constants.defaultProblems
import jakarta.validation.Validation.byProvider
import jakarta.validation.Validator
import org.hibernate.validator.HibernateValidator
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ProblemDetail
import org.springframework.http.ProblemDetail.forStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.web.server.ServerWebExchange
import app.users.api.security.SecurityConfiguration.Companion.spaNegated
import app.users.api.Loggers.i
import java.net.URI
import java.util.Locale.ENGLISH
import java.util.Locale.forLanguageTag

object HttpUtils {
    @Suppress("MemberVisibilityCanBePrivate", "unused")
    val ServerWebExchange.spaExchange: ServerWebExchange
        get() = request.uri.path.run {
            when {
                spaNegated.none { !startsWith(it) } && matches(Regex(SPA_NEGATED_REGEX)) ->
                    mutate().request(request.mutate().path("/index.html").build()).build()

                else -> this@spaExchange
            }
        }

    val ServerWebExchange.validator: Validator
        get() = byProvider(HibernateValidator::class.java)
            .configure()
            .localeResolver {
                try {
                    forLanguageTag(request.headers.acceptLanguage.first().range)
                } catch (e: Exception) {
                    ENGLISH
                }
            }
            .buildValidatorFactory()
            .validator


    //TODO: i18n
    @Suppress("unused")
    fun ProblemsModel.serverErrorResponse(error: String)
            : ResponseEntity<ProblemDetail> = internalServerError()
        .body(forStatus(INTERNAL_SERVER_ERROR).apply {
            type = URI(this@serverErrorResponse.type)
            title = title
            status = INTERNAL_SERVER_ERROR.value()
            setProperty("path", path)
            setProperty("message", message)
            setProperty("error", error)
        }).also { "fieldErrors: $fieldErrors".run(::i) }

    fun ProblemsModel.badResponse(
        fieldErrors: Set<Map<String, String?>>
    ): ResponseEntity<ProblemDetail> = badRequest()
        .body(forStatus(BAD_REQUEST).apply {
            type = URI(defaultProblems.type)
            title = defaultProblems.title
            status = BAD_REQUEST.value()
            setProperty("path", path)
            setProperty("message", message)
            setProperty("fieldErrors", fieldErrors)
        }).also { "fieldErrors: $fieldErrors".run(::i) }
}