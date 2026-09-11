package com.afmvfcc.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afmvfcc.app.data.api.ApiClient
import com.afmvfcc.app.data.local.Prefs
import com.afmvfcc.app.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    // ── Auth state ────────────────────────────────────────────
    private val _isLoggedIn    = MutableStateFlow(false)
    val isLoggedIn              = _isLoggedIn.asStateFlow()

    private val _loginError    = MutableStateFlow<String?>(null)
    val loginError              = _loginError.asStateFlow()

    private val _isLoggingIn   = MutableStateFlow(false)
    val isLoggingIn             = _isLoggingIn.asStateFlow()

    private val _loggedInUser  = MutableStateFlow("")
    val loggedInUser            = _loggedInUser.asStateFlow()

    // ── Sessions ──────────────────────────────────────────────
    private val _sessions        = MutableStateFlow<List<Session>>(emptyList())
    val sessions                 = _sessions.asStateFlow()

    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading          = _sessionsLoading.asStateFlow()

    private val _sessionsError   = MutableStateFlow<String?>(null)
    val sessionsError            = _sessionsError.asStateFlow()

    // ── Attendance ────────────────────────────────────────────
    private val _attendanceList    = MutableStateFlow<List<AttendanceState>>(emptyList())
    val attendanceList             = _attendanceList.asStateFlow()

    private val _attendanceLoading = MutableStateFlow(false)
    val attendanceLoading          = _attendanceLoading.asStateFlow()

    // ── Sub-branches ──────────────────────────────────────────
    private val _subBranches = MutableStateFlow<List<SubBranch>>(emptyList())
    val subBranches           = _subBranches.asStateFlow()

    // ── Sync state ────────────────────────────────────────────
    private val _syncResult = MutableStateFlow<String?>(null)
    val syncResult           = _syncResult.asStateFlow()

    private val _isSyncing  = MutableStateFlow(false)
    val isSyncing            = _isSyncing.asStateFlow()

    // ── Local guest queue (persisted across process death) ────
    private val _pendingGuests = MutableStateFlow<List<GuestRecord>>(emptyList())
    val pendingGuests           = _pendingGuests.asStateFlow()

    // ── Helpers ───────────────────────────────────────────────
    private suspend fun buildUrl(): String {
        val ip   = prefs.serverIp.first().trim()
        val port = prefs.serverPort.first().trim().ifEmpty { "8080" }
        return "http://$ip:$port"
    }

    private suspend fun client(): ApiClient {
        val token = prefs.token.first()
        return ApiClient(buildUrl(), token)
    }

    // ── Login ─────────────────────────────────────────────────
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value  = null
            try {
                val c    = ApiClient(buildUrl())
                val resp = c.login(username, password, android.os.Build.MODEL.take(40))
                prefs.saveAuth(resp.token, resp.name)
                _loggedInUser.value = resp.name
                _isLoggedIn.value   = true
                prefs.ensureDeviceId()
            } catch (e: Exception) {
                _loginError.value = when {
                    e.message?.contains("401") == true -> "Incorrect username or password."
                    e.message?.contains("403") == true -> "Account is locked or inactive."
                    e.message?.contains("refused") == true ||
                            e.message?.contains("connect") == true ->
                        "Cannot reach the CMS. Check the server IP and make sure you are on the same WiFi."
                    else -> "Login failed: ${e.message}"
                }
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.logout()
            _isLoggedIn.value     = false
            _sessions.value       = emptyList()
            _attendanceList.value = emptyList()
            // Guests are intentionally kept in memory and in DataStore
            // so they survive logout/login cycles until they are synced.
        }
    }

    // ── Sessions ──────────────────────────────────────────────
    fun loadSessions() {
        viewModelScope.launch {
            _sessionsLoading.value = true
            _sessionsError.value   = null
            try {
                _sessions.value = client().getSessions()
            } catch (e: Exception) {
                _sessionsError.value = "Could not load sessions: ${e.message}"
            } finally {
                _sessionsLoading.value = false
            }
        }
    }

    fun createSession(name: String, date: String, onSuccess: (Session) -> Unit) {
        viewModelScope.launch {
            try {
                val session = client().createSession(NewSessionRequest(name, date))
                loadSessions()
                onSuccess(session)
            } catch (e: Exception) {
                _sessionsError.value = "Could not create session: ${e.message}"
            }
        }
    }

    // ── Attendance ────────────────────────────────────────────
    fun loadAttendance(sessionId: Int, sessionName: String = "") {
        viewModelScope.launch {
            _attendanceLoading.value = true
            try {
                val members = client().getMembers(sessionId)
                _attendanceList.value = members.map { m -> AttendanceState(m, m.isPresent) }
            } catch (e: Exception) {
                Log.e("AFM", "Failed to load members", e)
            } finally {
                _attendanceLoading.value = false
            }
        }
    }

    fun toggleAttendance(memberId: Int) {
        val list = _attendanceList.value.toMutableList()
        val idx  = list.indexOfFirst { it.member.id == memberId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isPresent = !list[idx].isPresent)
            _attendanceList.value = list
        }
    }

    fun markAll(present: Boolean) {
        _attendanceList.value = _attendanceList.value.map { it.copy(isPresent = present) }
    }

    val presentCount get() = _attendanceList.value.count { it.isPresent }
    val totalCount   get() = _attendanceList.value.size

    // ── Guests ────────────────────────────────────────────────

    /** Add a guest and immediately persist the full list to DataStore. */
    fun addGuest(guest: GuestRecord) {
        val updated = _pendingGuests.value + guest
        _pendingGuests.value = updated
        persistGuests(updated)
    }

    /** Remove a single guest (e.g. after manual deletion). */
    fun removeGuest(guest: GuestRecord) {
        val updated = _pendingGuests.value - guest
        _pendingGuests.value = updated
        persistGuests(updated)
    }

    private fun persistGuests(guests: List<GuestRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                prefs.saveGuestsJson(guestsToJson(guests))
            } catch (e: Exception) {
                Log.e("AFM", "Failed to persist guests", e)
            }
        }
    }

    // ── JSON serialisation helpers ────────────────────────────

    private fun guestsToJson(guests: List<GuestRecord>): String {
        val arr = JSONArray()
        guests.forEach { g ->
            arr.put(JSONObject().apply {
                put("name",            g.name)
                put("phone",           g.phone)
                put("gender",          g.gender)
                put("subBranchId",     g.subBranchId ?: JSONObject.NULL)
                put("invitedBy",       g.invitedBy)
                put("wantsMembership", g.wantsMembership)
                put("prayerRequest",   g.prayerRequest)
                put("sessionId",       g.sessionId)
                put("visitDate",       g.visitDate)
            })
        }
        return arr.toString()
    }

    private fun guestsFromJson(json: String): List<GuestRecord> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GuestRecord(
                    name            = o.optString("name"),
                    phone           = o.optString("phone"),
                    gender          = o.optString("gender"),
                    subBranchId     = if (o.isNull("subBranchId")) null else o.optInt("subBranchId"),
                    invitedBy       = o.optString("invitedBy"),
                    wantsMembership = o.optBoolean("wantsMembership"),
                    prayerRequest   = o.optString("prayerRequest"),
                    sessionId       = o.optInt("sessionId"),
                    visitDate       = o.optString("visitDate")
                )
            }
        } catch (e: Exception) {
            Log.e("AFM", "Failed to parse guests JSON", e)
            emptyList()
        }
    }

    fun loadSubBranches() {
        viewModelScope.launch {
            try { _subBranches.value = client().getSubBranches() }
            catch (e: Exception) { Log.e("AFM", "SubBranches failed", e) }
        }
    }

    // ── Sync everything ───────────────────────────────────────
    fun syncAll(sessionId: Int) {
        viewModelScope.launch {
            _isSyncing.value  = true
            _syncResult.value = null
            try {
                val deviceId = prefs.deviceId.first()
                val c = client()

                // Sync attendance
                val records = _attendanceList.value.map {
                    AttendanceRecord(it.member.id, it.isPresent)
                }
                val attResult = c.syncAttendance(
                    AttendanceSyncPayload(sessionId, records, deviceId))

                // Sync guests
                var guestSynced = 0
                val sessionGuests = _pendingGuests.value.filter { it.sessionId == sessionId }
                if (sessionGuests.isNotEmpty()) {
                    val gResult = c.syncGuests(
                        GuestSyncPayload(sessionGuests, deviceId))
                    guestSynced = gResult.synced
                    // Remove only the synced session's guests, keep others
                    val remaining = _pendingGuests.value.filter { it.sessionId != sessionId }
                    _pendingGuests.value = remaining
                    persistGuests(remaining)
                }

                _syncResult.value =
                    "Synced ${attResult.total} attendance records" +
                            (if (guestSynced > 0) " and $guestSynced guest(s)" else "") + "."

            } catch (e: Exception) {
                _syncResult.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncResult() { _syncResult.value = null }

    // ── Restore persisted guests on startup ───────────────────
    private fun restoreGuests() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = prefs.guestsJson.first()
                val restored = guestsFromJson(json)
                _pendingGuests.value = restored
                Log.d("AFM", "Restored ${restored.size} guest(s) from storage")
            } catch (e: Exception) {
                Log.e("AFM", "Failed to restore guests", e)
            }
        }
    }

    // ── Check if already logged in on app start ───────────────
    init {
        restoreGuests()
        viewModelScope.launch {
            val token = prefs.token.first()
            val name  = prefs.userName.first()
            if (token.isNotEmpty() && name.isNotEmpty()) {
                try {
                    val valid = ApiClient(buildUrl(), token).ping()
                    if (valid) {
                        _loggedInUser.value = name
                        _isLoggedIn.value   = true
                    } else {
                        prefs.logout()
                    }
                } catch (_: Exception) {
                    _loggedInUser.value = name
                    _isLoggedIn.value   = true
                }
            }
        }
    }
}