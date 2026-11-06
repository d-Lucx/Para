package com.dlucx.para

internal const val Overlay = "para-overlay"
internal const val PositionOverlay = "para-position"

internal fun styleJson(
    archiveUrl: String, streetPacks: List<StreetPack>, background: String, primary: String, outline: String, minor: String,
    water: String, approach: String, arrival: String, land: String, landAlternate: String,
    detailPacks: List<DetailMapPack>, dark: Boolean,
): String {
    val outsideDetail = detailPacks.filter(DetailMapPack::rectangular)
        .joinToString("") { """["!",["within",${it.boundsJson}]],""" }
    fun line(id: String, source: String, kind: String, color: String, width: Double, dash: Boolean = false): String {
        val sourceLayer = if (source == "corridor") ""","source-layer":"lines"""" else ""
        val dashPaint = if (dash) ""","line-dasharray":[2,2]""" else ""
        return """{"id":"$id","type":"line","source":"$source"$sourceLayer,
            "filter":["==","kind","$kind"],"paint":{"line-color":"$color","line-width":$width$dashPaint}}"""
    }
    fun circle(kind: String, color: String, radius: Int) =
        """{"id":"$kind","type":"circle","source":"$Overlay","filter":["==","kind","$kind"],
            "paint":{"circle-color":"$color","circle-radius":$radius,"circle-stroke-color":"$background","circle-stroke-width":1}}"""
    fun streetLayers(kinds: List<Pair<String, String>>, casing: Boolean = false) = streetPacks.flatMap { pack ->
        kinds.map { (kind, color) ->
            val width = if (kind == "main") 2.5 else 1.3
            val edge = if (casing) 1.1 else 0.0
            """{"id":"$kind-${if (casing) "casing-" else ""}${pack.id}","type":"line","source":"streets-${pack.id}","source-layer":"roads",
                "filter":["==","kind","$kind"],"paint":{"line-color":"$color",
                "line-width":["interpolate",["linear"],["zoom"],8,${0.6 + edge / 2},14,${width + edge},17,${width * 2 + edge}]}}"""
        }
    }
    val landmarkKinds = "\"Bus station\",\"Ferry terminal\",\"Railway station\",\"Airport\",\"Hospital\",\"Transit stop\""
    val icon = """["match",["get","kind"],
        ["Restaurant","Cafe","Fast food"],"street-food",["Bank","ATM"],"street-bank",
        ["Hospital","Clinic","Pharmacy"],"street-health","Fuel","street-fuel",
        ["Bus station","Ferry terminal","Railway station","Airport","Transit stop"],"street-transit","street-place"]"""
    fun landmarkLayers(pack: StreetPack, detail: Boolean): List<String> {
        val id = (if (detail) "detail-" else "") + pack.id
        val filter = """["all",["!=","kind","Traffic signal"],["${if (detail) "!in" else "in"}","kind",$landmarkKinds]]"""
        val zoom = if (detail) 15 else 13
        return listOf(
            """{"id":"landmarks-$id","type":"symbol","source":"streets-${pack.id}","source-layer":"landmarks","minzoom":$zoom,
                "filter":$filter,"layout":{"icon-image":$icon,"icon-size":0.8,"text-optional":true,
                "text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":12,
                "text-anchor":"top","text-offset":[0,0.95],"text-max-width":12},
                "paint":{"text-color":"$primary","text-halo-color":"$land","text-halo-width":1.5}}""",
        )
    }
    val streetLabels = streetPacks.flatMap { pack -> listOf(
        """{"id":"road-names-${pack.id}","type":"symbol","source":"streets-${pack.id}","source-layer":"roads","minzoom":12,
            "filter":["all",$outsideDetail["in",["get","kind"],["literal",["main","local"]]]],
            "layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":11},
            "paint":{"text-color":"$outline","text-halo-color":"$land","text-halo-width":1.5}}""",
        """{"id":"road-oneway-${pack.id}","type":"symbol","source":"streets-${pack.id}","source-layer":"roads","minzoom":16,
            "filter":["in","oneway",1,-1],"layout":{"symbol-placement":"line","symbol-spacing":85,
                "icon-image":"street-oneway","icon-rotation-alignment":"map","icon-keep-upright":false,
                "icon-rotate":["case",["==",["get","oneway"],-1],180,0]}}""",
        """{"id":"traffic-signals-${pack.id}","type":"symbol","source":"streets-${pack.id}","source-layer":"landmarks","minzoom":16,
            "filter":["==","kind","Traffic signal"],"layout":{"icon-image":"street-signal","icon-size":0.8}}""",
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
    ) + streetLayers(listOf(
        "local" to if (dark) "#3D4342" else "#D8D2C7",
        "main" to if (dark) "#807356" else "#D4C8B2",
    ), casing = true) + streetLayers(listOf(
        "local" to if (dark) "#656A6C" else "#FFFFFF",
        "main" to if (dark) "#B39458" else "#FFE3A0",
    )) + detailPacks.map { pack ->
        """{"id":"${pack.id}-base","type":"fill","source":"${pack.id}-bounds","minzoom":11,
            "paint":{"fill-color":"${if (dark) "#2B302D" else "#F7F5ED"}"}}"""
    } + detailPacks.flatMap { pilotAreaLayers(dark, it.id) } +
        streetLayers(listOf("water" to water, "rail" to outline)) +
        // Regional detail uses the nationwide street labels so each name is drawn once.
        detailPacks.flatMap { pilotRoadLayers(dark, it.id, showLabels = it.rectangular) } + streetLabels + listOf(
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
        circle("live-pin", arrival, 7),
        circle("approach-handle", approach, 4),
        circle("arrival-handle", arrival, 4),
        """{"id":"places","type":"symbol","source":"corridor","source-layer":"places",
            "layout":{"icon-image":["get","name"],"icon-size":0.65,"icon-anchor":"bottom-left"}}""",
        namedLayer("national", false, 6.5, primary, background),
        namedLayer("pilot-stop", true, 9.0, primary, background),
        """{"id":"position-accuracy","type":"fill","source":"$PositionOverlay","filter":["==","kind","accuracy"],
            "paint":{"fill-color":"$primary","fill-opacity":0.18}}""",
        """{"id":"position-dot","type":"circle","source":"$PositionOverlay","filter":["==","kind","position"],
            "paint":{"circle-color":"$primary","circle-radius":10,"circle-stroke-color":"$background","circle-stroke-width":2}}""",
        """{"id":"position-heading","type":"symbol","source":"$PositionOverlay","filter":["==","kind","position"],
            "layout":{"visibility":"none","icon-image":"para-heading-arrow","icon-rotation-alignment":"map",
                "icon-allow-overlap":true,"icon-ignore-placement":true}}""",
    )
    val streetSources = streetPacks.joinToString(",") { """"streets-${it.id}":{"type":"vector","url":"${it.url}"}""" }
    val detailSources = detailPacks.joinToString("") {
        """"${it.id}":{"type":"vector","url":"${it.url}"},
        "${it.id}-bounds":{"type":"geojson","data":{"type":"Feature","geometry":${it.boundsJson},"properties":{}}},"""
    }
    return """{"version":8,"glyphs":"asset://glyphs/{fontstack}/{range}.pbf","sources":{
        $streetSources,
        $detailSources
        "corridor":{"type":"vector","url":"$archiveUrl"},
        "regions":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "region-names":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "named-places":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
        "$PositionOverlay":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
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
