package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.InboxItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** get_my_inbox RPC 반환 row. */
@Serializable
data class InboxRow(
    @SerialName("letter_id") val letterId: String,
    val token: String,
    val title: String,
    @SerialName("saved_at") val savedAt: String,
) {
    fun toDomain(): InboxItem = InboxItem(
        letterId = letterId,
        token = token,
        title = title,
        savedAt = Instant.parse(savedAt),
    )
}
