package com.dlucx.para

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class SavedDestination(val id: String, val draft: DestinationDraft)

internal class SavedDestinations(context: Context) {
    private val file = EncryptedFile(context, "destinations.enc", "para.saved-destinations")

    fun load(): List<SavedDestination> = synchronized(lock) { read() }

    fun save(id: String?, draft: DestinationDraft): List<SavedDestination> = synchronized(lock) {
        val entry = SavedDestination(id ?: UUID.randomUUID().toString(), draft)
        val entries = read()
        val updated = if (entries.any { it.id == entry.id }) {
            entries.map { if (it.id == entry.id) entry else it }
        } else {
            listOf(entry) + entries
        }
        write(updated)
        updated
    }

    fun delete(id: String): List<SavedDestination> = synchronized(lock) {
        val updated = read().filterNot { it.id == id }
        write(updated)
        updated
    }

    private fun read(): List<SavedDestination> {
        val array = JSONArray(file.read() ?: return emptyList())
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SavedDestination(
                id = item.getString("id"),
                draft = DestinationDraft(
                    name = item.getString("name"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    approachMeters = item.getInt("approachMeters"),
                    arrivalMeters = item.getInt("arrivalMeters"),
                    backupMinutes = if (item.isNull("backupMinutes")) null else item.getInt("backupMinutes"),
                ),
            )
        }
    }

    private fun write(entries: List<SavedDestination>) {
        val array = JSONArray()
        entries.forEach { (id, draft) ->
            array.put(
                JSONObject()
                    .put("id", id)
                    .put("name", draft.name)
                    .put("latitude", draft.latitude)
                    .put("longitude", draft.longitude)
                    .put("approachMeters", draft.approachMeters)
                    .put("arrivalMeters", draft.arrivalMeters)
                    .put("backupMinutes", draft.backupMinutes ?: JSONObject.NULL),
            )
        }
        file.write(array.toString())
    }

    private companion object {
        val lock = Any()
    }
}

