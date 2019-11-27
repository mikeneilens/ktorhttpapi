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
    private val mainApplication = Application::main

    @BeforeEach
    internal fun beforeAll(){
        snippets.removeAll { true }
    }
    @Test
    fun `get on root returns My Example Blog`() = withTestApplication(mainApplication) {
        val call = handleRequest(HttpMethod.Get, "/")
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("My Example Blog", response.content)
        }
    }
    @Test
    fun `get on snippets returns test hello test world`() = withTestApplication(mainApplication) {
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
    fun `post to login-register using basic authentication returns an access token `() = withTestApplication(mainApplication) {
        val account = Account("test","test")
        val call = handleRequest(HttpMethod.Post, "/login-register") {
            val up = "${account.user}:${account.password}"
            val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64() //this is actually ignored in the application as body is used to provide credentials
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody(jacksonMapper.writeValueAsString(account))
        }
        with(call){
            assertEquals(HttpStatusCode.OK, response.status())
            val tokenDecoded:TokenJson = jacksonMapper.readValue(response.content ?:"")
            assertEquals(simpleJwt.sign(account.user),tokenDecoded.token)
        }
    }
    @InternalAPI
    @Test
    fun `post to login-register using basic authentication fails with Unauthorized if credentials are wrong`() = withTestApplication(mainApplication) {
        val account = Account("test","rubbish")

        val call = handleRequest(HttpMethod.Post, "/login-register") {
            val up = "${account.user}:${account.password}"
            val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64() //this is actually ignored in the application as body is used to provide credentials
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"user\": \"${account.user}\",\"password\":\"${account.password}\"}")
        }
        with(call){
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertEquals("{\"OK\":false,\"error\":\"Invalid credentials\"}",response.content)
        }
    }

    @Test
    fun `can post to snippets`() = withTestApplication(mainApplication)  {
        val account = Account("test","test")
        val token = simpleJwt.sign(account.user)

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

    @Test
    fun `delete snippets removes all snippets`() = withTestApplication(mainApplication) {
        snippets.add(Snippet(user = "test", text = "hello"))
        snippets.add(Snippet(user = "test", text = "world"))

        val account = Account("test","test")
        val token = simpleJwt.sign(account.user)

        val call = handleRequest(HttpMethod.Delete, "/snippets"){
            val bearer = "Bearer $token"
            addHeader(HttpHeaders.Authorization, bearer)
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"snippet\": {\"text\" : \"mysnippet!\"}}")
        }
        with(call) {
            org.junit.jupiter.api.Assertions.assertEquals(io.ktor.http.HttpStatusCode.OK, response.status())
            org.junit.jupiter.api.Assertions.assertEquals("{\"OK\":true}", response.content)
            org.junit.jupiter.api.Assertions.assertEquals(0, blog.snippets.size)
        }
    }
}

