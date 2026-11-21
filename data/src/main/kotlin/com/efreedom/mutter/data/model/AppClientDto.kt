package com.efreedom.mutter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * report_app_client RPC 파라미터.
 * 기본값을 두지 않는다 — encodeDefaults=false라 기본값과 같은 필드는 생략되고, PostgREST가 함수 시그니처를 못 찾는다.
 */
@Serializable
data class ReportAppClientParams(
    @SerialName("p_platform") val platform: String,
    @SerialName("p_app_version") val appVersion: String,
    @SerialName("p_build") val build: String,
)
