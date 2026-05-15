package com.example.sondenit.data

import org.json.JSONObject

data class SleepSession(
    val id: String,
    val displayName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val createdAt: Long,
    val notes: String = "",
) {
    val isActive: Boolean get() = endedAt == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("startedAt", startedAt)
        if (endedAt != null) put("endedAt", endedAt) else put("endedAt", JSONObject.NULL)
        put("createdAt", createdAt)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): SleepSession = SleepSession(
            id = json.getString("id"),
            displayName = json.getString("displayName").withoutDuplicatedCatalanDe(),
            startedAt = json.getLong("startedAt"),
            endedAt = if (json.isNull("endedAt")) null else json.getLong("endedAt"),
            createdAt = json.getLong("createdAt"),
            notes = json.optString("notes", ""),
        )
    }
}

private fun String.withoutDuplicatedCatalanDe(): String =
    replace(Regex("\\bde\\s+de\\b"), "de")
