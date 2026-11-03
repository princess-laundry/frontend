package com.sidefocus.app.sync

import com.sidefocus.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SyncClientTest {
    private lateinit var server: MockWebServer
    private val vault = object : TokenStore {
        var token: String? = null
        override fun read(endpoint: String) = token
        override fun write(endpoint: String, token: String) { this.token = token }
        override fun clear() { token = null }
    }
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun client() = SyncClient(server.url("/api/v1/").toString(), "device-id", vault)
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    private fun session() = StudySession("stable-id", 0, 1000, listOf(Segment(Posture.NORMAL, 0, 1000))).upload("0.1")
    @Test fun `401 refreshes exactly once with the same idempotency key`() = runBlocking {
        vault.token = "expired"
        server.enqueue(json("{}", 401))
        server.enqueue(json("""{"access_token":"fresh"}"""))
        server.enqueue(json("""{"session_id":1024}""", 201))
        assertEquals(1024L, client().upload(session()))
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!
        val auth = server.takeRequest(1, TimeUnit.SECONDS)!!
        val retry = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/device", auth.path)
        assertEquals("Bearer expired", first.getHeader("Authorization"))
        assertEquals("Bearer fresh", retry.getHeader("Authorization"))
        assertEquals(first.body.readUtf8(), retry.body.readUtf8())
    }
    @Test fun `repeated 401 stops after a single token refresh`() = runBlocking {
        vault.token = "expired"
        server.enqueue(json("{}", 401)); server.enqueue(json("""{"access_token":"fresh"}""")); server.enqueue(json("{}", 401))
        try { client().upload(session()); fail("Expected 401") } catch (e: ApiFailure) { assertEquals(401, e.status); assertFalse(e.retryable) }
        assertEquals(3, server.requestCount)
    }
    @Test fun `duplicate upload response 200 is accepted`() = runBlocking {
        vault.token = "valid"; server.enqueue(json("""{"session_id":1024}""", 200))
        assertEquals(1024L, client().upload(session()))
    }
    @Test fun `400 is permanent while 503 is retryable`() = runBlocking {
        vault.token = "valid"
        for (status in listOf(400, 503)) {
            server.enqueue(json("{}", status))
            try { client().upload(session()); fail("Expected HTTP error") } catch (e: ApiFailure) { assertEquals(status == 503, e.retryable) }
        }
    }
    @Test fun `204 user deletion clears local token`() = runBlocking {
        vault.token = "valid"; server.enqueue(MockResponse().setResponseCode(204))
        client().deleteUser(); assertNull(vault.token)
    }
}
