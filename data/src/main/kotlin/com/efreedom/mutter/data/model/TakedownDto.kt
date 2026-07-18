package com.efreedom.mutter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** report_takedown RPC 파라미터. */
@Serializable
data class TakedownParams(
    @SerialName("p_letter_id") val letterId: String? = null,
    @SerialName("p_track_ref") val trackRef: String? = null,
    @SerialName("p_claimant") val claimant: String,
    @SerialName("p_contact") val contact: String,
    @SerialName("p_reason") val reason: String,
)
