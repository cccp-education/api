package app.users.api

import app.users.api.Constants.CLOUD
import app.users.api.Constants.DEVELOPMENT
import app.users.api.Constants.DEV_HOST
import app.users.api.Constants.EMPTY_CONTEXT_PATH
import app.users.api.Constants.EMPTY_STRING
import app.users.api.Constants.HTTP
import app.users.api.Constants.HTTPS
import app.users.api.Constants.LINE
import app.users.api.Constants.PRODUCTION
import app.users.api.Constants.SERVER_PORT
import app.users.api.Constants.SERVER_SERVLET_CONTEXT_PATH
import app.users.api.Constants.SERVER_SSL_KEY_STORE
import app.users.api.Constants.SPRING_APPLICATION_NAME
import app.users.api.Constants.STARTUP_HOST_WARN_LOG_MSG
import app.users.api.Constants.STARTUP_LOG_MSG_KEY
import app.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import java.net.InetAddress.getLocalHost
import java.net.UnknownHostException
import java.util.Locale.getDefault

object Loggers {
    private val log: Logger by lazy { LoggerFactory.getLogger(Server::class.java) }

    fun i(message: String): Unit = log.info(message)
    fun d(message: String): Unit = log.debug(message)
    fun w(message: String): Unit = log.warn(message)
    fun t(message: String): Unit = log.trace(message)
    fun e(message: String): Unit = log.error(message)
    fun e(message: String, defaultMessage: String?): Unit = log.error(message, defaultMessage)
    fun e(message: String, e: Exception?): Unit = log.error(message, e)
    fun w(message: String, e: Exception?): Unit = log.warn(message, e)


    fun ApplicationContext.startupLog() = logProfiles.run {
        StartupLogMsg(
            appName = SPRING_APPLICATION_NAME.run(environment::getProperty),
            goVisitMessage = getBean<Properties>().goVisitMessage,
            protocol = when {
                SERVER_SSL_KEY_STORE.run(environment::getProperty) != null -> HTTPS
                else -> HTTP
            },
            serverPort = SERVER_PORT.run(environment::getProperty),
            contextPath = SERVER_SERVLET_CONTEXT_PATH.run(environment::getProperty)
                ?: EMPTY_CONTEXT_PATH,
            hostAddress = try {
                getLocalHost().hostAddress
            } catch (e: UnknownHostException) {
                STARTUP_HOST_WARN_LOG_MSG.run(Loggers::w)
                DEV_HOST
            },
            profiles = when {
                environment.defaultProfiles.isNotEmpty() -> environment.defaultProfiles.reduce { accumulator, profile -> "$accumulator, $profile" }

                else -> EMPTY_STRING
            },
            activeProfiles = when {
                environment.activeProfiles.isNotEmpty() -> environment.activeProfiles.reduce { accumulator, profile -> "$accumulator, $profile" }

                else -> EMPTY_STRING
            },
        ).run(Loggers::startupLogMessage)
            .run(Loggers::i)
    }


    private data class StartupLogMsg(
        val appName: String?,
        val goVisitMessage: String,
        val protocol: String,
        val serverPort: String?,
        val contextPath: String,
        val hostAddress: String,
        val profiles: String,
        val activeProfiles: String
    )

    private fun startupLogMessage(startupLogMsg: StartupLogMsg): String = """$LINE$LINE$LINE
----------------------------------------------------------
Go visit ${startupLogMsg.goVisitMessage}    
----------------------------------------------------------
Application '${startupLogMsg.appName}' is running!
Access URLs
    Local:      ${startupLogMsg.protocol}://localhost:${startupLogMsg.serverPort}${startupLogMsg.contextPath}
    External:   ${startupLogMsg.protocol}://${startupLogMsg.hostAddress}:${startupLogMsg.serverPort}${startupLogMsg.contextPath}${
        when {
            startupLogMsg.profiles.isNotBlank() -> LINE + buildString {
                append("Profile(s): ")
                append(startupLogMsg.profiles)
            }

            else -> EMPTY_STRING
        }
    }${
        when {
            startupLogMsg.activeProfiles.isNotBlank() -> LINE + buildString {
                append("Active(s) profile(s): ")
                append(startupLogMsg.activeProfiles)
            }

            else -> EMPTY_STRING
        }
    }
----------------------------------------------------------
$LINE$LINE""".trimIndent()

    private val ApplicationContext.logProfiles: ApplicationContext
        get() = apply {
            environment.activeProfiles.run {
                when {
                    contains(DEVELOPMENT) && contains(PRODUCTION) -> e(
                        getBean<MessageSource>().getMessage(
                            STARTUP_LOG_MSG_KEY,
                            arrayOf(DEVELOPMENT, PRODUCTION),
                            getDefault()
                        )
                    )

                    contains(DEVELOPMENT) && contains(CLOUD) -> e(
                        getBean<MessageSource>().getMessage(
                            STARTUP_LOG_MSG_KEY,
                            arrayOf(DEVELOPMENT, CLOUD),
                            getDefault()
                        )
                    )
                }
            }
        }
}