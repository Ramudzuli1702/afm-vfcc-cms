package com.afmvfcc.app.data.api

import com.afmvfcc.app.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Thrown for any non-2xx CMS API response, carrying the server's actual
 * error message (from {"error": "..."}) and the HTTP status code, instead
 * of letting a mismatched-shape response fall through to a raw
 * kotlinx.serialization crash like "Fields[token,userId,name] are required".
 */
class ApiException(message: String, val statusCode: Int) : Exception(message)

/** Decodes a successful response as T, or throws [ApiException] with the
 * server's real error message for anything else. Every call in this file
 * should end in this instead of a bare .body(), since a bare .body() blindly
 * tries to decode error responses as the success type and throws a
 * confusing serialization error instead of the actual problem. */
private suspend inline fun <reified T> HttpResponse.parseOrThrow(): T {
    if (status.isSuccess()) return body()
    val message = try {
        body<ErrorResponse>().error
    } catch (e: Exception) {
        "Request failed (HTTP ${status.value})"
    }
    throw ApiException(message, status.value)
}

class ApiClient(private val baseUrl: String, private val token: String = "") {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis  = 10_000
            connectTimeoutMillis  = 8_000
            socketTimeoutMillis   = 10_000
        }
    }

    private fun HttpRequestBuilder.auth() {
        if (token.isNotEmpty()) header("Authorization", "Bearer $token")
    }

    // ── Login ─────────────────────────────────────────────────
    suspend fun login(username: String, password: String, device: String): LoginResponse {
        return client.post("$baseUrl/api/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("username" to username, "password" to password, "device" to device))
        }.parseOrThrow()
    }

    // ── Ping ──────────────────────────────────────────────────
    suspend fun ping(): Boolean {
        return try {
            val resp = client.get("$baseUrl/api/ping") { auth() }
            resp.status.value == 200
        } catch (e: Exception) { false }
    }

    // ── Sessions ──────────────────────────────────────────────
    suspend fun getSessions(): List<Session> =
        client.get("$baseUrl/api/sessions") { auth() }.parseOrThrow()

    suspend fun createSession(req: NewSessionRequest): Session =
        client.post("$baseUrl/api/sessions") {
            auth(); contentType(ContentType.Application.Json); setBody(req)
        }.parseOrThrow()

    // ── Members ───────────────────────────────────────────────
    suspend fun getMembers(sessionId: Int? = null): List<Member> =
        client.get("$baseUrl/api/members") {
            auth()
            if (sessionId != null) parameter("sessionId", sessionId)
        }.parseOrThrow()

    // ── Sub-branches ──────────────────────────────────────────
    suspend fun getSubBranches(): List<SubBranch> =
        client.get("$baseUrl/api/sub-branches") { auth() }.parseOrThrow()

    // ── Sync attendance ───────────────────────────────────────
    suspend fun syncAttendance(payload: AttendanceSyncPayload): SyncResult =
        client.post("$baseUrl/api/sync/attendance") {
            auth(); contentType(ContentType.Application.Json); setBody(payload)
        }.parseOrThrow()

    // ── Sync guests ───────────────────────────────────────────
    suspend fun syncGuests(payload: GuestSyncPayload): SyncResult =
        client.post("$baseUrl/api/sync/guests") {
            auth(); contentType(ContentType.Application.Json); setBody(payload)
        }.parseOrThrow()

    fun close() = client.close()
}
