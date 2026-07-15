package com.unitytunnel.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SessionStartRequest(val device_id: String, val server_id: String)
data class SessionStartResponse(val session_id: String, val balance: Long)

data class HeartbeatRequest(val session_id: String, val device_id: String)
data class HeartbeatResponse(val status: String, val remaining: Long)

data class SessionEndRequest(val session_id: String)
data class SessionEndResponse(val success: Boolean)

data class RewardGrantRequest(val device_id: String, val reward_type: String)
data class RewardGrantResponse(val success: Boolean, val new_balance: Long, val ads_today: Int)

interface TunnelApiService {
    @POST("/session/start")
    suspend fun startSession(@Body request: SessionStartRequest): Response<SessionStartResponse>

    @POST("/session/heartbeat")
    suspend fun heartbeat(@Body request: HeartbeatRequest): Response<HeartbeatResponse>

    @POST("/session/end")
    suspend fun endSession(@Body request: SessionEndRequest): Response<SessionEndResponse>

    @POST("/reward/grant")
    suspend fun grantReward(@Body request: RewardGrantRequest): Response<RewardGrantResponse>
}
