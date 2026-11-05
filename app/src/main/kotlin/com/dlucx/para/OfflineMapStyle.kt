package com.dlucx.para

internal const val Overlay = "para-overlay"

internal fun styleJson(
    archiveUrl: String, streetPacks: List<StreetPack>, background: String, primary: String, outline: String, minor: String,
    water: String, approach: String, arrival: String, land: String, landAlternate: String,
    pilotUrl: String, dark: Boolean,
): String {
    fun line(id: String, source: String, kind: String, color: String, width: Double, dash: Boolean = false): String {
        val sourceLayer = if (source == "corridor") ""","source-layer":"lines"""" else ""
        val dashPaint = if (dash) ""","line-dasharray":[2,2]""" else ""
        return """{"id":"$id","type":"line","source":"$source"$sourceLayer,
            "filter":["==","kind","$kind"],"paint":{"line-color":"$color","line-width":$width$dashPaint}}"""
    }
    fun circle(kind: String, color: String, radius: Int) =
        """{"id":"$kind","type":"circle","source":"$Overlay","filter":["==","kind","$kind"],
            "paint":{"circle-color":"$color","circle-radius":$radius,"circle-stroke-color":"$background","circle-stroke-width":1}}"""
    fun streetLayers(kinds: List<Pair<String, String>>) = streetPacks.flatMap { pack ->
        kinds.map { (kind, color) ->
            """{"id":"$kind-${pack.id}","type":"line","source":"streets-${pack.id}","source-layer":"roads",
                "filter":["==","kind","$kind"],"paint":{"line-color":"$color",
                "line-width":["interpolate",["linear"],["zoom"],8,0.6,14,${if (kind == "main") 2.5 else 1.3}]}}"""
        }
    }
    val landmarkKinds = "\"Bus station\",\"Ferry terminal\",\"Railway station\",\"Airport\",\"Hospital\",\"Transit stop\""
    fun landmarkLayers(pack: StreetPack, detail: Boolean): List<String> {
        val id = (if (detail) "detail-" else "") + pack.id
        val filter = """["${if (detail) "!in" else "in"}","kind",$landmarkKinds]"""
        val zoom = if (detail) 15 else 13
        return listOf(
            """{"id":"landmark-points-$id","type":"circle","source":"streets-${pack.id}","source-layer":"landmarks","minzoom":$zoom,
                "filter":$filter,"paint":{"circle-color":"$primary","circle-radius":2.5}}""",
            """{"id":"landmarks-$id","type":"symbol","source":"streets-${pack.id}","source-layer":"landmarks","minzoom":$zoom,
                "filter":$filter,"layout":{"text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":12,
                "text-anchor":"top","text-offset":[0,0.5],"text-max-width":12},
                "paint":{"text-color":"$primary","text-halo-color":"$land","text-halo-width":1.5}}""",
        )
    }
    val streetLabels = streetPacks.flatMap { pack -> listOf(
        """{"id":"road-names-${pack.id}","type":"symbol","source":"streets-${pack.id}","source-layer":"roads","minzoom":13,
            "filter":["!",["within",$PilotBoundsJson]],
            "layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":11},
            "paint":{"text-color":"$outline","text-halo-color":"$land","text-halo-width":1.5}}""",
    ) + landmarkLayers(pack, false) + landmarkLayers(pack, true) }
    val layers = listOf(
        """{"id":"background","type":"background","paint":{"background-color":"$background"}}""",
        """{"id":"regional-land","type":"fill","source":"regions","paint":{
            "fill-color":["match",["get","shade"],1,"$landAlternate",2,"$land","$minor"],
            "fill-opacity":0.54}}""",
        """{"id":"regional-boundaries","type":"line","source":"regions",
            "paint":{"line-color":"$outline","line-width":1}}""",
        """{"id":"regional-name-labels","type":"symbol","source":"region-names","maxzoom":7.5,
            "layout":{"icon-image":["concat","region:",["get","name"]],"icon-size":0.7}}""",
        line("coast", "corridor", "coast", water, 2.0),
    ) + streetLayers(listOf("local" to minor, "main" to outline)) + listOf(
        """{"id":"pilot-base","type":"fill","source":"pilot-bounds","minzoom":11,
            "paint":{"fill-color":"${if (dark) "#2B302D" else "#F7F5ED"}"}}""",
    ) + pilotAreaLayers(dark) + streetLayers(listOf("water" to water, "rail" to outline)) + pilotRoadLayers(dark) + streetLabels + listOf(
        line("coverage", Overlay, "boundary", outline, 2.0, dash = true),
        line("track", Overlay, "track", primary, 3.5),
        line("approach", Overlay, "approach", approach, 2.0, dash = true),
        line("arrival", Overlay, "arrival", arrival, 2.0),
        circle("track-point", primary, 3),
        circle("track-start", if (dark) "#87D5A0" else "#277C48", 5),
        circle("track-end", primary, 5),
        """{"id":"track-endpoint-labels","type":"symbol","source":"$Overlay",
            "filter":["in","kind","track-start","track-end"],
            "layout":{"text-field":["match",["get","kind"],"track-start","Start","Last recorded point"],
                "text-font":["Inter-Regular"],"text-size":11,"text-offset":[0,1.3],"text-anchor":"top"},
            "paint":{"text-color":"$primary","text-halo-color":"$background","text-halo-width":1.5}}""",
        circle("pin", primary, 6),
        circle("approach-handle", approach, 4),
        circle("arrival-handle", arrival, 4),
        """{"id":"places","type":"symbol","source":"corridor","source-layer":"places",
            "layout":{"icon-image":["get","name"],"icon-size":0.65,"icon-anchor":"bottom-left"}}""",
        namedLayer("national", false, 6.5, primary, background),
        namedLayer("pilot-stop", true, 9.0, primary, background),
    )
    val streetSources = streetPacks.joinToString(",") { """"streets-${it.id}":{"type":"vector","url":"${it.url}"}""" }
    return """{"version":8,"glyphs":"asset://glyphs/{fontstack}/{range}.pbf","sources":{
        $streetSources,
        "pilot":{"type":"vector","url":"$pilotUrl"},
        "pilot-bounds":{"type":"geojson","data":{"type":"Feature","geometry":$PilotBoundsJson,"properties":{}}},
        "corridor":{"type":"vector","url":"$archiveUrl"},
        "regions":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "region-names":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "named-places":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "$Overlay":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}
        },"layers":[""" + layers.joinToString(",") + "]}"
}

private fun namedLayer(id: String, pilot: Boolean, minZoom: Double, color: String, halo: String) =
    """{"id":"$id-points","type":"circle","source":"named-places","minzoom":$minZoom,
        "filter":["==","pilot",$pilot],"paint":{"circle-radius":4,"circle-color":"$color"}},
        {"id":"$id-labels","type":"symbol","source":"named-places","minzoom":$minZoom,
        "filter":["==","pilot",$pilot],"layout":{
            "text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":12,
            "text-anchor":"bottom-left","text-offset":[0.4,-0.3]},
            "paint":{"text-color":"$color","text-halo-color":"$halo","text-halo-width":1.5}}"""
