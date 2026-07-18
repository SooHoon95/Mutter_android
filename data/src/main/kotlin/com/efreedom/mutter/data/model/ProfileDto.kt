package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.Profile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** profiles 테이블 row. */
@Serializable
data class ProfileDto(
    val id: String,
    val nickname: String? = null,
) {
    fun toDomain(): Profile = Profile(id = id, nickname = nickname)
}

/** profiles upsert 입력. */
@Serializable
data class ProfileUpsertDto(
    val id: String,
    val nickname: String,
    @SerialName("updated_at") val updatedAt: String,
)
