package app.users.api.models

import app.users.api.Constants.EMPTY_STRING
import app.users.api.models.User.Attributes.EMAIL_ATTR
import app.users.api.models.User.Attributes.EMAIL_OR_LOGIN
import app.users.api.models.User.Attributes.ID_ATTR
import app.users.api.models.User.Attributes.LANG_KEY_ATTR
import app.users.api.models.User.Attributes.LOGIN_ATTR
import app.users.api.models.User.Attributes.PASSWORD_ATTR
import app.users.api.models.User.Attributes.VERSION_ATTR
import app.users.api.models.User.Constraints.LOGIN_REGEX
import app.users.api.models.User.Members.ROLES_MEMBER
import app.users.api.models.User.Relations.Fields.EMAIL_FIELD
import app.users.api.models.User.Relations.Fields.EMAIL_IDX_FIELD
import app.users.api.models.User.Relations.Fields.ID_FIELD
import app.users.api.models.User.Relations.Fields.LANG_KEY_FIELD
import app.users.api.models.User.Relations.Fields.LOGIN_FIELD
import app.users.api.models.User.Relations.Fields.LOGIN_IDX_FIELD
import app.users.api.models.User.Relations.Fields.PASSWORD_FIELD
import app.users.api.models.User.Relations.Fields.PASSWORD_IDX_FIELD
import app.users.api.models.User.Relations.Fields.TABLE_NAME
import app.users.api.models.User.Relations.Fields.VERSION_FIELD
import app.users.api.models.UserRole.Relations.Fields.USER_ROLE_ID_SEQ_FIELD
import app.users.password.UserReset
import app.users.password.UserReset.Relations.Fields.ACTIVE_IDX_FIELD
import app.users.password.UserReset.Relations.Fields.DATE_IDX_FIELD
import app.users.password.UserReset.Relations.Fields.ID_DATE_IDX_FIELD
import app.users.password.UserReset.Relations.Fields.USER_ID_IDX_FIELD
import app.users.password.UserReset.Relations.Fields.USER_RESET_SEQ_FIELD
import app.users.signup.UserActivation
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.Locale.ENGLISH
import java.util.UUID


data class User(
    override val id: UUID? = null,
    @field:NotNull
    @field:Pattern(regexp = LOGIN_REGEX)
    @field:Size(min = 1, max = 50)
    val login: String = EMPTY_STRING,
    @field:JsonIgnore
    @field:NotNull
    @field:Size(min = 60, max = 60)
    val password: String = EMPTY_STRING,
    @field:Email
    @field:Size(min = 5, max = 254)
    val email: String = EMPTY_STRING,
    @field:JsonIgnore
    val roles: Set<Role> = emptySet(),
    @field:Size(min = 2, max = 10)
    val langKey: String = ENGLISH.language,
    @field:JsonIgnore
    val version: Long = 0,
) : EntityModel<UUID>() {

    companion object {
        val objectName: String = User::class.java.simpleName.run {
            replaceFirst(first(), first().lowercaseChar())
        }
    }

    object Constraints {
        const val LOGIN_REGEX =
            "^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$"
        const val PHONE_REGEX = "^(\\+|00)?[1-9]\\d{0,49}\$"


        const val IMAGE_URL_DEFAULT = "https://placehold.it/50x50"
    }

    object Members {
        const val PASSWORD_MEMBER = "password"
        const val ROLES_MEMBER = "roles"
    }

    object Attributes {
        const val ID_ATTR = "id"
        const val LOGIN_ATTR = "login"
        const val PASSWORD_ATTR = "password"
        const val EMAIL_ATTR = "email"
        const val LANG_KEY_ATTR = "langKey"
        const val VERSION_ATTR = "version"
        const val EMAIL_OR_LOGIN = "emailOrLogin"
    }

    object Relations {
        val CREATE_TABLES: String
            get() = listOf(
                @Suppress("RemoveRedundantQualifierName")
                User.Relations.SQL_SCRIPT,
                Role.Relations.SQL_SCRIPT,
                UserRole.Relations.SQL_SCRIPT,
                UserActivation.Relations.SQL_SCRIPT,
                UserReset.Relations.SQL_SCRIPT,
            ).joinToString(
                separator = "",
                transform = String::trimIndent
            ).run(String::trimMargin)


        @Suppress("RemoveRedundantQualifierName")
        val testCreateTables = """
            
            SET search_path = test;
            
            DROP TABLE IF EXISTS "${UserRole.Relations.Fields.TABLE_NAME}";
            DROP TABLE IF EXISTS "${UserActivation.Relations.Fields.TABLE_NAME}";
            DROP TABLE IF EXISTS "${UserReset.Relations.Fields.TABLE_NAME}";
            DROP TABLE IF EXISTS "${Role.Relations.Fields.TABLE_NAME}";
            DROP TABLE IF EXISTS "${User.Relations.Fields.TABLE_NAME}";
            
            DROP SEQUENCE IF EXISTS "$USER_ROLE_ID_SEQ_FIELD";
            DROP SEQUENCE IF EXISTS "$USER_RESET_SEQ_FIELD";
            
            DROP INDEX IF EXISTS "$USER_ID_IDX_FIELD";
            DROP INDEX IF EXISTS "$DATE_IDX_FIELD";
            DROP INDEX IF EXISTS "$ID_DATE_IDX_FIELD";
            DROP INDEX IF EXISTS "$ACTIVE_IDX_FIELD";            
            DROP INDEX IF EXISTS "$LOGIN_IDX_FIELD";
            DROP INDEX IF EXISTS "$EMAIL_IDX_FIELD";
            DROP INDEX IF EXISTS "$PASSWORD_IDX_FIELD";

            DROP INDEX IF EXISTS "uniq_idx_user_login";
            DROP INDEX IF EXISTS "uniq_idx_user_email";
            DROP INDEX IF EXISTS "uniq_idx_user_authority";
            DROP INDEX IF EXISTS "uniq_idx_user_activation_key";
        
        """.trimIndent()

        const val SQL_SCRIPT = """
        
        CREATE TABLE IF NOT EXISTS "$TABLE_NAME"(
            "$ID_FIELD"       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            "$LOGIN_FIELD"    TEXT NOT NULL,
            "$PASSWORD_FIELD" TEXT NOT NULL,
            "$EMAIL_FIELD"    TEXT NOT NULL,
            "$LANG_KEY_FIELD" VARCHAR DEFAULT 'en',
            UNIQUE ("$LOGIN_FIELD"),
            UNIQUE ("$EMAIL_FIELD"),
            "$VERSION_FIELD"  BIGINT DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS "$LOGIN_IDX_FIELD" ON "$TABLE_NAME"("$LOGIN_FIELD");
        CREATE INDEX IF NOT EXISTS "$EMAIL_IDX_FIELD" ON "$TABLE_NAME"("$EMAIL_FIELD");
        CREATE INDEX IF NOT EXISTS "$PASSWORD_IDX_FIELD" ON "$TABLE_NAME"("$PASSWORD_FIELD");
        """

        object Fields {
            const val TABLE_NAME = "user"
            const val ID_FIELD = "id"
            const val LOGIN_FIELD = "login"
            const val PASSWORD_FIELD = "password"
            const val EMAIL_FIELD = "email"
            const val LANG_KEY_FIELD = "lang_key"
            const val VERSION_FIELD = "version"
            const val LOGIN_IDX_FIELD = "idx_${TABLE_NAME}_$LOGIN_FIELD"
            const val EMAIL_IDX_FIELD = "idx_${TABLE_NAME}_$EMAIL_FIELD"
            const val PASSWORD_IDX_FIELD = "idx_${TABLE_NAME}_$PASSWORD_FIELD"

        }

        const val FIND_ALL_USERS = """SELECT * FROM "$TABLE_NAME";"""
        const val LOGIN_AND_EMAIL_AVAILABLE_COLUMN = "login_and_email_available"
        const val EMAIL_AVAILABLE_COLUMN = "email_available"
        const val LOGIN_AVAILABLE_COLUMN = "login_available"

        const val INSERT = """
        INSERT INTO "$TABLE_NAME"(
            "$LOGIN_FIELD", "$EMAIL_FIELD", "$PASSWORD_FIELD",
            "$LANG_KEY_FIELD", "$VERSION_FIELD") VALUES (
            :$LOGIN_ATTR, :$EMAIL_ATTR, :$PASSWORD_ATTR,
            :$LANG_KEY_ATTR, :$VERSION_ATTR );
        """
        const val UPDATE_PASSWORD_RESET = """
        UPDATE "user"
        SET "password" = :password, "version" = "version" + 1
        WHERE "id" = :id;
        """
        const val UPDATE_PASSWORD = """
            UPDATE "$TABLE_NAME"
            SET "$PASSWORD_FIELD" = :$PASSWORD_ATTR,
                    "$VERSION_FIELD" = :$VERSION_ATTR + 1
            WHERE "$ID_FIELD" = :$ID_ATTR;"""

        const val SELECT_SIGNUP_AVAILABILITY = """
        SELECT
            CASE
            WHEN EXISTS(
                SELECT 1 FROM "$TABLE_NAME"
                WHERE LOWER("$LOGIN_FIELD") = LOWER(:$LOGIN_ATTR)
            ) OR
            EXISTS(
                SELECT 1 FROM "$TABLE_NAME"
                WHERE LOWER("$EMAIL_FIELD") = LOWER(:$EMAIL_ATTR)
            )
            THEN FALSE
            ELSE TRUE
            END AS $LOGIN_AND_EMAIL_AVAILABLE_COLUMN,
            NOT EXISTS(
                SELECT 1 FROM "$TABLE_NAME"
                WHERE LOWER("$EMAIL_FIELD") = LOWER(:$EMAIL_ATTR)
            ) AS $EMAIL_AVAILABLE_COLUMN,
            NOT EXISTS(
                SELECT 1 FROM "$TABLE_NAME"
                WHERE LOWER("$LOGIN_FIELD") = LOWER(:$LOGIN_ATTR)
            ) AS $LOGIN_AVAILABLE_COLUMN;
        """

        const val FIND_USER_WITH_AUTHS_BY_EMAILOGIN = """
        SELECT
            u."$ID_FIELD",
            u."$EMAIL_FIELD",
            u."$LOGIN_FIELD",
            u."$PASSWORD_FIELD",
            u.$LANG_KEY_FIELD,
            u.$VERSION_FIELD,
            STRING_AGG(DISTINCT a."${Role.Relations.Fields.ID_FIELD}", ', ') AS $ROLES_MEMBER
        FROM "$TABLE_NAME" as u
        LEFT JOIN
            user_authority ua ON u."$ID_FIELD" = ua."${UserRole.Relations.Fields.USER_ID_FIELD}"
        LEFT JOIN
            authority as a 
            ON UPPER(ua."${UserRole.Relations.Fields.ROLE_FIELD}") = UPPER(a."${Role.Attributes.ID_ATTR}")
        WHERE
            lower(u."$EMAIL_FIELD") = lower(:$EMAIL_OR_LOGIN)
        OR
            lower(u."$LOGIN_FIELD") = lower(:$EMAIL_OR_LOGIN)
        GROUP BY u."$ID_FIELD", u."$LOGIN_FIELD",u."$EMAIL_FIELD";
                        """
    }

    object EndPoint {
        const val API_USER = "/api/user"
        const val API_USERS = "/api/users"
        const val API_AUTHORITY_PATH = "/api/authorities"
    }
}