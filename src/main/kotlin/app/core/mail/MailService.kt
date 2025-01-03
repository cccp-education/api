package app.core.mail

//interface MailService {
//    fun sendEmail(
//        to: String,
//        subject: String,
//        content: String,
//        isMultipart: Boolean,
//        isHtml: Boolean
//    )
//
//    fun sendEmailFromTemplate(
//        account: AccountCredentials,
//        templateName: String,
//        titleKey: String
//    )
//
//    fun sendPasswordResetMail(account: AccountCredentials)
//    fun sendActivationEmail(account: AccountCredentials)
//    fun sendCreationEmail(account: AccountCredentials)
//}

data class GoogleWrapper(
    val client_id: String,
    val project_id: String,
    val auth_uri: String,
    val token_uri: String,
    val auth_provider_x509_cert_url: String,
    val client_secret: String,
    val redirect_uris: List<String>
)