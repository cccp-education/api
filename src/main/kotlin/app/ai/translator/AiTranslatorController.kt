package app.ai.translator

import app.ai.translator.AiTranslatorController.AssistantManager.PromptManager.userMessageEn
import app.ai.translator.AiTranslatorController.AssistantManager.PromptManager.userMessageFr
import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.getOrElse
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.output.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import kotlin.coroutines.resume

@RestController
@RequestMapping("api/ai/translator")
class AiTranslatorController(service: ChatModelService) {

    @PostMapping(produces = [APPLICATION_PROBLEM_JSON_VALUE])
    suspend fun translate(
        message: String,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> = OK.run(::ResponseEntity)

    @Service
    class ChatModelService(context: ApplicationContext) {
        suspend fun answer(question: String) {
//            val answer=dev.langchain4j.model.output.Response<AiMessage>()
        }
    }

    object AssistantManager {
        @JvmStatic
        fun main(args: Array<String>) {
            userMessageFr.run { "userMessageFr : $this" }.run(::println)
            println()
            userMessageEn.run { "userMessageEn : $this" }.run(::println)
        }

        @JvmStatic
        val localModels
            get() = setOf(
                "llama3.2:3b" to "LlamaTiny",
                "llama3.1:8b" to "LlamaSmall",
                "mistral:7b" to "MistralSmall",
                "aya:8b" to "AyaSmall",
                "phi3.5:3.8b" to "Phi",
                "smollm:135m" to "SmollM",
                "granite-code:3b" to "GraniteCodeTiny",
                "codegemma:2b" to "CodeGemmaTiny",
                "yi-coder:1.5b" to "YiCoderTiny",
                "yi-coder:9b" to "YiCoderSmall"
            )

        // Creating tasks for each model
        @JvmStatic
        fun ApplicationContext.createChatTasks() = localModels.forEach {
            createChatTask(it.first, "helloOllama${it.second}")
            createStreamingChatTask(it.first, "helloOllamaStream${it.second}")
        }

        fun ApplicationContext.createOllamaChatModel(model: String = "smollm:135m"): OllamaChatModel =
            OllamaChatModel.builder().apply {
                baseUrl(environment.getProperty("ollama.baseUrl") as? String ?: "http://localhost:11434")
//                modelName(findProperty("ollama.modelName") as? String ?: model)
//                temperature(findProperty("ollama.temperature") as? Double ?: 0.8)
//                timeout(ofSeconds(findProperty("ollama.timeout") as? Long ?: 6_000))
                logRequests(true)
                logResponses(true)
            }.build()

        fun ApplicationContext.createOllamaStreamingChatModel(model: String = "smollm:135m"): OllamaStreamingChatModel =
            OllamaStreamingChatModel.builder().apply {
//                baseUrl(findProperty("ollama.baseUrl") as? String ?: "http://localhost:11434")
//                modelName(findProperty("ollama.modelName") as? String ?: model)
//                temperature(findProperty("ollama.temperature") as? Double ?: 0.8)
//                timeout(ofSeconds(findProperty("ollama.timeout") as? Long ?: 6_000))
                logRequests(true)
                logResponses(true)
            }.build()


        suspend fun generateStreamingResponse(
            model: StreamingChatLanguageModel, promptMessage: String
        ): Either<Throwable, Response<AiMessage>> = Either.catch {
            suspendCancellableCoroutine { continuation ->
                model.generate(promptMessage, object : StreamingResponseHandler<AiMessage> {
                    override fun onNext(token: String) = print(token)

                    override fun onComplete(response: Response<AiMessage>) =
                        continuation.resume(response)

                    override fun onError(error: Throwable) =
                        continuation.resume(Left(error).getOrElse { throw it })
                })
            }
        }

        fun ApplicationContext.runChat(model: String) {
            createOllamaChatModel(model = model)
                .run { userMessageFr.run(::generate).let(::println) }
        }

        fun ApplicationContext.runStreamChat(model: String) {
            runBlocking {
                createOllamaStreamingChatModel(model).run {
                    when (val answer = generateStreamingResponse(this, userMessageFr)) {
                        is Right -> "Complete response received:\n${
                            answer.value.content().text()
                        }".run(::println)

                        is Left -> "Error during response generation:\n${answer.value}".run(::println)
                    }
                }
            }
        }

        // Generic function for chat model tasks
        fun ApplicationContext.createChatTask(model: String, taskName: String) {
//            task(taskName) {
//                group = "school-ai"
//                description = "Display the Ollama $model chatgpt prompt request."
//                doFirst { ApplicationContext.runChat(model) }
//            }
        }

        // Generic function for streaming chat model tasks
        fun ApplicationContext.createStreamingChatTask(model: String, taskName: String) {
//            task(taskName) {
//                group = "school-ai"
//                description = "Display the Ollama $model chatgpt stream prompt request."
//                doFirst { runStreamChat(model) }
//            }
        }

//        @JvmStatic
//        val ApplicationContext.openAIapiKey: String
//            get() = privateProps["OPENAI_API_KEY"] as String

        object PromptManager {
            const val ASSISTANT_NAME = "E-3PO"
            val userName = System.getProperty("user.name")!!
            val userMessageFr = """config```--lang=fr;```. 
            | Salut je suis $userName,
            | toi tu es $ASSISTANT_NAME, tu es mon assistant.
            | Le cœur de métier de ${System.getProperty("user.name")} est le développement logiciel dans l'EdTech
            | et la formation professionnelle pour adulte.
            | La spécialisation de ${System.getProperty("user.name")} est dans l'ingénierie de la pédagogie pour adulte,
            | et le software craftmanship avec les méthodes agiles.
            | $ASSISTANT_NAME ta mission est d'aider ${System.getProperty("user.name")} dans l'activité d'écriture de formation et génération de code.
            | Réponds moi à ce premier échange uniquement en maximum 120 mots""".trimMargin()
            val userMessageEn = """config```--lang=en;```.
        | You are E-3PO, an AI assistant specialized in EdTech and professional training. 
        | Your primary user is cheroliv, a software craftsman and adult education expert 
        | who focuses on EdTech development and agile methodologies.
        | Your base responsibilities:
        | 1. Assist with creating educational content for adult learners
        | 2. Help with code generation and software development tasks
        | 3. Support application of agile and software craftsmanship principles
        | 4. Provide guidance on instructional design for adult education
        | Please communicate clearly and concisely, focusing on practical solutions.
        | Keep initial responses under 120 words.""".trimMargin()
        }
    }
}