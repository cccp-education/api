@file:Suppress("MemberVisibilityCanBePrivate")

package app.users

import app.core.database.EntityModel.Members.withId
import app.core.Constants.ADMIN
import app.core.Constants.DOMAIN_DEV_URL
import app.core.Constants.EMPTY_STRING
import app.core.Constants.ROLE_ADMIN
import app.core.Constants.ROLE_ANONYMOUS
import app.core.Constants.ROLE_USER
import app.core.Constants.USER
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.reactive.collect
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingleOrNull
import app.users.User.Attributes.EMAIL_ATTR
import app.users.User.Attributes.LOGIN_ATTR
import app.users.UserDao.countUsers
import app.users.Utils.Data.displayInsertUserScript
import app.users.security.Role
import app.users.security.UserRoleDao.countUserAuthority
import app.users.signup.Signup
import app.users.signup.UserActivation
import app.users.signup.UserActivation.Attributes.ACTIVATION_KEY_ATTR
import app.users.signup.UserActivation.Fields.ACTIVATION_DATE_FIELD
import app.users.signup.UserActivation.Fields.ACTIVATION_KEY_FIELD
import app.users.signup.UserActivation.Fields.CREATED_DATE_FIELD
import app.users.signup.UserActivation.Fields.ID_FIELD
import app.users.signup.UserActivation.Relations.FIND_BY_ACTIVATION_KEY
import app.users.signup.UserActivationDao.countUserActivation
import java.time.LocalDateTime
import java.time.LocalDateTime.parse
import java.time.ZoneOffset.UTC
import java.util.*
import java.util.regex.Pattern
import kotlin.test.assertEquals

object Utils {
    @JvmStatic
    fun main(args: Array<String>): Unit = displayInsertUserScript()

    object Data {
        const val OFFICIAL_SITE = "https://cccp-education.github.io/"
        const val DEFAULT_IMAGE_URL = "https://placehold.it/50x50"
        val admin: User by lazy { userFactory(ADMIN) }
        val user: User by lazy { userFactory(USER) }
        val users: Set<User> = setOf(admin, user)
        const val DEFAULT_USER_JSON = """{
    "login": "$USER",
    "email": "$USER@$DOMAIN_DEV_URL",
    "password": "$USER"}"""
        val signup: Signup by lazy {
            Signup(
                login = user.login,
                password = user.password,
                email = user.email,
                repassword = user.password
            )
        }

        fun userFactory(login: String): User = User(
            password = login,
            login = login,
            email = "$login@$DOMAIN_DEV_URL",
        )

        fun displayInsertUserScript() {
            "InsertUserScript :\n$INSERT_USERS_SCRIPT".run(::println)
        }

        //TODO : add methode to complete user generation
        const val INSERT_USERS_SCRIPT = """
            -- Fonction pour générer des mots de passe aléatoires
            CREATE OR REPLACE FUNCTION random_password(length INT) RETURNS TEXT AS ${'$'}${'$'}
            DECLARE
                chars TEXT := 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
                pwd TEXT := '';
            BEGIN
                FOR i IN 1..length LOOP
                    pwd := pwd || substring(chars from (floor(random() * length(chars)) + 1) for 1);
                END LOOP;
                RETURN pwd;
            END;
            ${'$'}${'$'} LANGUAGE plpgsql;
            
            -- Insertion de 100 nouveaux utilisateurs avec des mots de passe aléatoires
            INSERT INTO "user" ("login", "password", "email", "lang_key")
            VALUES
                ('user1', random_password(10), 'user1@example.com', 'en'),
                ('user2', random_password(10), 'user2@example.com', 'fr'),
                -- ... (répéter 98 fois en remplaçant les noms d'utilisateur et les emails)
                ('user100', random_password(10), 'user100@example.com', 'es');
            
            -- Attribution du rôle "USER" à tous les nouveaux utilisateurs
            INSERT INTO "user_authority" ("user_id", "role")
            SELECT "id", 'USER'
            FROM "user"
            WHERE "id" IN (
                SELECT "id"
                FROM "user"
                WHERE "login" LIKE 'user%'
            );"""
    }

    val ApplicationContext.PATTERN_LOCALE_2: Pattern
        get() = Pattern.compile("([a-z]{2})-([a-z]{2})")

    val ApplicationContext.PATTERN_LOCALE_3: Pattern
        get() = Pattern.compile("([a-z]{2})-([a-zA-Z]{4})-([a-z]{2})")

    val ApplicationContext.languages
        get() = setOf("en", "fr", "de", "it", "es")

    val ApplicationContext.defaultRoles
        get() = setOf(ROLE_ADMIN, ROLE_USER, ROLE_ANONYMOUS)

    fun ApplicationContext.checkProperty(
        property: String,
        value: String,
        injectedValue: String
    ) = property.apply {
        assertEquals(value, let(environment::getProperty))
        assertEquals(injectedValue, let(environment::getProperty))
    }

//    suspend fun ApplicationContext.findAllUsers()
//            : Either<Throwable, List<User>> = FIND_ALL_USERS
//        .trimIndent()
//        .run(getBean<DatabaseClient>()::sql)
//        .fetch()
//        .all()
//        .collect {
//            it.run {
//                User(
//                    id = get(ID_FIELD).toString().run(UUID::fromString),
//                    login = get(LOGIN_ATTR).toString(),
//                    email = get(EMAIL_ATTR).toString(),
//                    langKey = get(LANG_KEY_ATTR).toString(),
//                    roles = mutableSetOf<Role>().apply {
//                        """
//                        SELECT ua."role"
//                        FROM "user" u
//                        JOIN user_authority ua
//                        ON u.id = ua.user_id
//                        WHERE u.id = :userId;"""
//                            .trimIndent()
//                            .run(getBean<DatabaseClient>()::sql)
//                            .bind("userId", get(ID_FIELD))
//                            .fetch()
//                            .all()
//                            .collect { add(Role(it[Role.Fields.ID_FIELD].toString())) }
//                    }.toSet()
//                )
//            }
//        }

    @Throws(EmptyResultDataAccessException::class)
    suspend fun ApplicationContext.findUserActivationByKey(key: String)
            : Either<Throwable, UserActivation> = try {
        FIND_BY_ACTIVATION_KEY
            .trimIndent()
            .run(getBean<R2dbcEntityTemplate>().databaseClient::sql)
            .bind(ACTIVATION_KEY_ATTR, key)
            .fetch()
            .awaitSingleOrNull()
            .let {
                when (it) {
                    null -> return EmptyResultDataAccessException(1).left()
                    else -> return UserActivation(
                        id = it[ID_FIELD].toString().run(UUID::fromString),
                        activationKey = it[ACTIVATION_KEY_FIELD].toString(),
                        createdDate = parse(it[CREATED_DATE_FIELD].toString())
                            .toInstant(UTC),
                        activationDate = it[ACTIVATION_DATE_FIELD].run {
                            when {
                                this == null || toString().lowercase() == "null" -> null
                                else -> toString().run(LocalDateTime::parse).toInstant(UTC)
                            }
                        },
                    ).right()
                }
            }
    } catch (e: Throwable) {
        e.left()
    }

    suspend fun ApplicationContext.findAuthsByEmail(email: String): Either<Throwable, Set<Role>> = try {
        mutableSetOf<Role>().apply {
            """
            SELECT ua."role" 
            FROM "user" u 
            JOIN user_authority ua 
            ON u.id = ua.user_id 
            WHERE u."email" = :$EMAIL_ATTR;"""
                .trimIndent()
                .run(getBean<DatabaseClient>()::sql)
                .bind(EMAIL_ATTR, email)
                .fetch()
                .all()
                .collect { add(Role(it[Role.Fields.ID_FIELD].toString())) }
        }.toSet().right()
    } catch (e: Throwable) {
        e.left()
    }

    suspend fun ApplicationContext.findAuthsByLogin(login: String): Either<Throwable, Set<Role>> = try {
        mutableSetOf<Role>().apply {
            """
            SELECT ua."role" 
            FROM "user" u 
            JOIN user_authority ua 
            ON u.id = ua.user_id 
            WHERE u."login" = :$LOGIN_ATTR;"""
                .trimIndent()
                .run(getBean<DatabaseClient>()::sql)
                .bind(LOGIN_ATTR, login)
                .fetch()
                .all()
                .collect { add(Role(it[Role.Fields.ID_FIELD].toString())) }
        }.toSet().right()
    } catch (e: Throwable) {
        e.left()
    }

    suspend fun ApplicationContext.findUserById(id: UUID): Either<Throwable, User> = try {
        User().withId(id).copy(password = EMPTY_STRING).run user@{
            findAuthsById(id).getOrNull().run roles@{
                return if (isNullOrEmpty())
                    "Unable to retrieve roles from user by id"
                        .run(::Exception)
                        .left()
                else copy(roles = this@roles).right()
            }
        }
    } catch (e: Throwable) {
        e.left()
    }

    suspend fun ApplicationContext.findAuthsById(userId: UUID): Either<Throwable, Set<Role>> = try {
        mutableSetOf<Role>().apply {
            """
            SELECT ua."role" 
            FROM "user" as u 
            JOIN user_authority as ua 
            ON u.id = ua.user_id 
            WHERE u.id = :userId;"""
                .trimIndent()
                .run(getBean<DatabaseClient>()::sql)
                .bind("userId", userId)
                .fetch()
                .all()
                .collect { add(Role(it[Role.Fields.ID_FIELD].toString())) }
        }.toSet().right()
    } catch (e: Throwable) {
        e.left()
    }

    suspend fun ApplicationContext.tripleCounts() = Triple(
        countUsers().also {
            assertEquals(
                0,
                it,
                "I expected 0 app.users in database."
            )
        },
        countUserAuthority().also {
            assertEquals(
                0,
                it,
                "I expected 0 userAuthority in database."
            )
        },
        countUserActivation().also {
            assertEquals(
                0,
                it,
                "I expected 0 userActivation in database."
            )
        }
    )
}