package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.LetterOpenSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** get_my_letter_opens RPC 반환 row. */
@Serializable
data class LetterOpenRow(
    @SerialName("letter_id") val letterId: String,
    @SerialName("open_count") val openCount: Int,
    @SerialName("last_opened_at") val lastOpenedAt: String,
) {
    fun toDomain(): LetterOpenSummary = LetterOpenSummary(
        letterId = letterId,
        openCount = openCount,
        lastOpenedAt = Instant.parse(lastOpenedAt),
    )
}
