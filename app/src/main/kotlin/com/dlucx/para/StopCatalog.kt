package com.dlucx.para

import android.content.res.AssetManager
import java.text.Normalizer
import java.util.Locale

internal data class TransitStop(
    val id: String,
    val name: String,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
)

internal class StopCatalog private constructor(private val entries: List<Entry>) {
    fun search(query: String): List<TransitStop> {
        val terms = searchTerms(query)
        if (terms.isEmpty() && query.isNotBlank()) return emptyList()

        return entries.asSequence()
            .filter { entry -> terms.all { term -> entry.terms.any { it.startsWith(term) } } }
            .map { it.stop }
            .toList()
    }

    private data class Entry(val stop: TransitStop, val terms: List<String>)

    companion object {
        private val marks = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")

        fun load(assets: AssetManager): StopCatalog {
            val stops = assets.open("corridor-stops.tsv").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }
                    .map { line ->
                        val fields = line.split('\t')
                        check(fields.size in 5..6) { "Invalid bundled transit stop" }
                        val stop = TransitStop(
                            id = fields[0],
                            name = fields[1],
                            locality = fields[2],
                            latitude = fields[3].toDouble(),
                            longitude = fields[4].toDouble(),
                        )
                        val aliases = fields.getOrElse(5) { "" }
                        Entry(stop, searchTerms("${stop.name} ${stop.locality} $aliases"))
                    }
                    .toList()
            }
            return StopCatalog(stops)
        }

        private fun searchTerms(value: String): List<String> {
            val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            return normalized.replace(marks, "").split(separators).filter { it.isNotEmpty() }
        }
    }
}
