package com.example.sondenit.data

import org.json.JSONObject

data class SleepSession(
    val id: String,
    val displayName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val createdAt: Long,
) {
    val isActive: Boolean get() = endedAt == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("startedAt", startedAt)
        if (endedAt != null) put("endedAt", endedAt) else put("endedAt", JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SleepSession = SleepSession(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            startedAt = json.getLong("startedAt"),
            endedAt = if (json.isNull("endedAt")) null else json.getLong("endedAt"),
            createdAt = json.getLong("createdAt"),
        )
    }
}
