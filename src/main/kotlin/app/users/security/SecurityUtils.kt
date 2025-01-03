package app.users.security

import app.core.Constants
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.lang3.RandomStringUtils.random
import org.springframework.http.HttpMethod.OPTIONS
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers
import app.users.security.SecurityConfiguration.Companion.negated
import java.security.SecureRandom


object SecurityUtils {

    private const val DEF_COUNT = 20

    private val SECURE_RANDOM: SecureRandom by lazy {
        SecureRandom().apply { 64.run(::ByteArray).run(::nextBytes) }
    }

    private val generateRandomAlphanumericString: String
        get() = random(
            DEF_COUNT,
            0,
            0,
            true,
            true,
            null,
            SECURE_RANDOM
        )

    val generatePassword: String
        get() = generateRandomAlphanumericString

    val generateActivationKey: String
        get() = generateRandomAlphanumericString

    val generateResetKey: String
        get() = generateRandomAlphanumericString

    val String.objectName get() = replaceFirst(first(), first().lowercaseChar())

    private fun extractPrincipal(authentication: Authentication?) =
        when (authentication) {
            null -> ""
            else -> when (val principal = authentication.principal) {
                is UserDetails -> principal.username
                is String -> principal
                else -> ""
            }
        }

    suspend fun getCurrentUserLogin() = extractPrincipal(
        getContext().awaitSingle().authentication
    )!!

    suspend fun getCurrentUserJwt() = getContext()
        .map(SecurityContext::getAuthentication)
        .filter { it.credentials is String }
        .map { it.credentials as String }
        .awaitSingle()!!

    suspend fun isAuthenticated(): Boolean = getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getAuthorities)
        .map { roles: Collection<GrantedAuthority> ->
            roles.map(GrantedAuthority::getAuthority)
                .none { it == Constants.ROLE_ANONYMOUS }
        }.awaitSingleOrNull()!!


    suspend fun isCurrentUserInRole(authority: String) =
        @Suppress("ReactiveStreamsTooLongSameOperatorsChain")
        getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getAuthorities)
        .map { roles: Collection<GrantedAuthority> ->
            roles.map(GrantedAuthority::getAuthority)
                .any { it == authority }
        }.awaitSingle()!!


    private val exchangeMatcher: NegatedServerWebExchangeMatcher
        get() = OrServerWebExchangeMatcher(
            pathMatchers(*negated),
            pathMatchers(OPTIONS, "/**")
        ).run(::NegatedServerWebExchangeMatcher)
}