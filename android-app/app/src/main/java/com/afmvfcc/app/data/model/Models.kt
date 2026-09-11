package com.afmvfcc.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(val token: String, val userId: Int, val name: String)

@Serializable
data class Session(
    val id: Int,
    val name: String,
    val date: String,
    val ministry: String = "",
    val presentCount: Int = 0
)

@Serializable
data class Member(
    val id: Int,
    val name: String,
    val phone: String = "",
    val subBranch: String = "",
    val ministry: String = "",
    val isPresent: Boolean = false
)

@Serializable
data class SubBranch(val id: Int, val name: String)

@Serializable
data class AttendanceRecord(val memberId: Int, val isPresent: Boolean)

@Serializable
data class AttendanceSyncPayload(
    val sessionId: Int,
    val records: List<AttendanceRecord>,
    val deviceId: String
)

@Serializable
data class GuestRecord(
    val name: String,
    val phone: String = "",
    val gender: String = "",
    val subBranchId: Int? = null,
    val invitedBy: String = "",
    val wantsMembership: Boolean = false,
    val prayerRequest: String = "",
    val sessionId: Int? = null,
    val visitDate: String
)

@Serializable
data class GuestSyncPayload(val guests: List<GuestRecord>, val deviceId: String)

@Serializable
data class SyncResult(val inserted: Int = 0, val updated: Int = 0,
                      val total: Int = 0, val synced: Int = 0)

@Serializable
data class NewSessionRequest(val name: String, val date: String, val ministryId: Int? = null)

data class AttendanceState(val member: Member, var isPresent: Boolean)
