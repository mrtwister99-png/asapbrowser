package com.example.actimelteambrowser.data

import org.json.JSONObject

/** Jedno ulozene prihlaseni v trezoru hesel. Ve fajlu vault.enc lezi jen zasifrovane. */
data class VaultEntry(
    val id: Long,
    val domain: String,      // napr. seznam.cz
    val username: String,
    val password: String,
    val note: String = "",
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("domain", domain)
        .put("username", username)
        .put("password", password)
        .put("note", note)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = VaultEntry(
            id = o.optLong("id"),
            domain = o.optString("domain"),
            username = o.optString("username"),
            password = o.optString("password"),
            note = o.optString("note"),
            createdAt = o.optLong("createdAt")
        )
    }
}
