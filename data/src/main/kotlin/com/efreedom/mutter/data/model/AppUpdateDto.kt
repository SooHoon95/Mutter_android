package com.efreedom.mutter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** app_config 테이블 row(강제 업데이트 원격 설정). 공개 읽기(anon 허용). */
@Serializable
data class AppConfigRow(
    val platform: String,
    @SerialName("min_version") val minVersion: String,
)
