package blog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.InternalAPI
import io.ktor.util.encodeBase64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach

class MainTest {
    data class Base(val snippets: List<Snippet>)
    data class TokenJson(val token: String)
    private val jacksonMapper = ObjectMapper().registerModule(KotlinModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @BeforeEach
    internal fun beforeAll(){
        snippets.removeAll { true }
    }
    @Test
    fun `get on root returns My Example Blog`() = withTestApplication(Application::main) {
        val call = handleRequest(HttpMethod.Get, "/")
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("My Example Blog", response.content)
        }
    }
    @Test
    fun `get on snippets returns test hello test world`() = withTestApplication(Application::main) {
        snippets.add(Snippet(user = "test", text = "hello"))
        snippets.add(Snippet(user = "test", text = "world"))

        val call = handleRequest(HttpMethod.Get, "/snippets")
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())

            val base:Base = jacksonMapper.readValue(response.content ?: "")
            val firstSnippet = base.snippets[0]
            val secondSnippet = base.snippets[1]

            assertEquals("test", firstSnippet.user)
            assertEquals("hello", firstSnippet.text)
            assertEquals("test", secondSnippet.user)
            assertEquals("world", secondSnippet.text)
        }
    }
    @InternalAPI // needed because encodeBase64() is experimental
    @Test
    fun `post to login-register using basic authentication returns an access token `() = withTestApplication(Application::main) {
        val user = User("test","test")
        val call = handleRequest(HttpMethod.Post, "/login-register") {
            val up = "${user.name}:${user.password}"
            val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64() //this is actually ignored in the application as body is used to provide credentials
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"user\": \"${user.name}\",\"password\":\"${user.password}\"}")
        }
        with(call){
            assertEquals(HttpStatusCode.OK, response.status())
            val tokenDecoded:TokenJson = jacksonMapper.readValue(response.content ?:"")
            assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoidGVzdCJ9.96At6bwFhxebk4xk4tpkOFj-3ThxkLFNHkHaKoedOfA",tokenDecoded.token)
        }
    }
    @InternalAPI
    @Test
    fun `post to login-register using basic authentication fails with Unauthorized if credentials are wrong`() = withTestApplication(Application::main) {
        val user = User("test","rubbish")

        val call = handleRequest(HttpMethod.Post, "/login-register") {
            val up = "${user.name}:${user.password}"
            val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64() //this is actually ignored in the application as body is used to provide credentials
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"user\": \"${user.name}\",\"password\":\"${user.password}\"}")
        }
        with(call){
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `can post to snippets`() = withTestApplication(Application::main)  {
        val user = User("test","test")
        val token = simpleJwt.sign(user.name)

        val call = handleRequest(HttpMethod.Post, "/snippets"){
            val bearer = "Bearer $token"
            addHeader(HttpHeaders.Authorization, bearer)
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"snippet\": {\"text\" : \"mysnippet!\"}}")
        }
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("{\"OK\":true}", response.content)
            assertEquals(1, snippets.size)
            val firstSnippet = snippets[0]

            assertEquals("test", firstSnippet.user)
            assertEquals("mysnippet!", firstSnippet.text)
        }

    }
}

