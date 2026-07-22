package com.efreedom.mutter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * report_takedown RPC 파라미터.
 *
 * p_letter_id·p_track_ref는 서버 default가 없어(마이그레이션 0012) null도 반드시 전송해야
 * 함수가 매칭된다. `= null` 기본값을 두면 encodeDefaults=false로 생략돼 신고가 실패한다.
 */
@Serializable
data class TakedownParams(
    @SerialName("p_letter_id") val letterId: String?,
    @SerialName("p_track_ref") val trackRef: String?,
    @SerialName("p_claimant") val claimant: String,
    @SerialName("p_contact") val contact: String,
    @SerialName("p_reason") val reason: String,
)
