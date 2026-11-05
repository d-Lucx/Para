package com.dlucx.para

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class TripRecord(val id: String, val summary: TripSummary)

internal class TripHistory(context: Context) {
    private val pending = EncryptedFile(context, "trip-pending.enc", "para.trip-history")
    private val saved = EncryptedFile(context, "trip-history.enc", "para.trip-history")

    fun checkpoint(record: TripRecord) = synchronized(lock) { pending.write(record.encode().toString()) }
    fun pending(): TripRecord? = synchronized(lock) { pending.read()?.let { decode(JSONObject(it)) } }
    fun load(): List<TripRecord> = synchronized(lock) { readSaved() }
    fun discard() = synchronized(lock) { pending.delete() }
    fun save(record: TripRecord): List<TripRecord> = synchronized(lock) {
        val updated = listOf(record) + readSaved().filterNot { it.id == record.id }
        writeSaved(updated)
        pending.delete()
        updated
    }
    fun delete(record: TripRecord): List<TripRecord> = synchronized(lock) {
        readSaved().filterNot { it.id == record.id }.also(::writeSaved)
    }

    private fun readSaved(): List<TripRecord> {
        val entries = JSONArray(saved.read() ?: "[]")
        return List(entries.length()) { decode(entries.getJSONObject(it)) }
    }

    private fun writeSaved(records: List<TripRecord>) {
        saved.write(JSONArray(records.map { it.encode() }).toString())
    }

    private fun TripRecord.encode() = JSONObject()
        .put("id", id)
        .put("name", summary.destination.name)
        .put("latitude", summary.destination.latitude)
        .put("longitude", summary.destination.longitude)
        .put("elapsed", summary.elapsedMillis)
        .put("distance", summary.recordedDistanceMeters)
        .put("outcome", summary.outcome.name)
        .put("segments", JSONArray(summary.segments.map { segment ->
            JSONArray(segment.map { JSONArray(listOf(it.latitude, it.longitude, it.fixMillis)) })
        }))

    private fun decode(value: JSONObject): TripRecord {
        val segments = value.getJSONArray("segments")
        return TripRecord(
            value.getString("id"),
            TripSummary(
                destination = DestinationDraft(
                    value.getString("name"), value.getDouble("latitude"), value.getDouble("longitude"),
                ),
                elapsedMillis = value.getLong("elapsed"),
                recordedDistanceMeters = value.getDouble("distance"),
                outcome = TripOutcome.valueOf(value.getString("outcome")),
                segments = List(segments.length()) { index ->
                    val segment = segments.getJSONArray(index)
                    List(segment.length()) {
                        val point = segment.getJSONArray(it)
                        TripPoint(point.getDouble(0), point.getDouble(1), point.getLong(2))
                    }
                },
            ),
        )
    }

    companion object {
        private val lock = Any()
        fun newId(): String = UUID.randomUUID().toString()
    }
}
