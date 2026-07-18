package com.efreedom.mutter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** upsert_push_token RPC 파라미터. */
@Serializable
data class UpsertPushTokenParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_platform") val platform: String,
    @SerialName("p_device_id") val deviceId: String? = null,
)
