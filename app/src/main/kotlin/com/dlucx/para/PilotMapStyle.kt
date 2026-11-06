package com.dlucx.para

internal const val PilotBoundsJson = """{"type":"Polygon","coordinates":[[[125.51,6.99],[125.89,6.99],[125.89,7.48],[125.51,7.48],[125.51,6.99]]]}"""

internal fun pilotAreaLayers(dark: Boolean, id: String): List<String> {
    val green = if (dark) "#293E32" else "#E3EEDD"
    val building = if (dark) "#46443F" else "#EAE5DC"
    val water = if (dark) "#244B60" else "#CBE6F6"
    return listOf(
        """{"id":"$id-landuse","type":"fill","source":"$id","source-layer":"landuse","minzoom":11,
            "paint":{"fill-color":["match",["get","kind"],["commercial","retail","industrial","residential"],"${if (dark) "#303334" else "#F2EEE6"}","$green"]}}""",
        """{"id":"$id-water","type":"fill","source":"$id","source-layer":"water","minzoom":11,"paint":{"fill-color":"$water"}}""",
        """{"id":"$id-buildings","type":"fill","source":"$id","source-layer":"buildings","minzoom":15,
            "paint":{"fill-color":"$building","fill-outline-color":"${if (dark) "#646058" else "#D3CBBF"}"}}""",
    )
}

internal fun pilotRoadLayers(dark: Boolean, id: String): List<String> {
    val major = """["in",["get","kind"],["literal",["motorway","motorway_link","trunk","trunk_link","primary","primary_link","secondary","secondary_link"]]]"""
    val width = """["interpolate",["linear"],["zoom"],11,["case",$major,1.5,0.6],14,["case",$major,4,2],17,["case",$major,12,6]]"""
    return listOf(
        """{"id":"$id-road-casing","type":"line","source":"$id","source-layer":"road-detail","minzoom":11,
            "paint":{"line-color":"${if (dark) "#807356" else "#D4C8B2"}","line-width":["interpolate",["linear"],["zoom"],11,["case",$major,3,2.1],14,["case",$major,5.5,3.5],17,["case",$major,13.5,7.5]]}}""",
        """{"id":"$id-road-fill","type":"line","source":"$id","source-layer":"road-detail","minzoom":11,
            "paint":{"line-color":["case",$major,"${if (dark) "#B39458" else "#FFE3A0"}","${if (dark) "#656A6C" else "#FFFFFF"}"],"line-width":$width}}""",
        """{"id":"$id-paths","type":"line","source":"$id","source-layer":"paths","minzoom":14,
            "paint":{"line-color":["case",["any",["in",["get","access"],["literal",["private","no"]]],["==",["get","foot"],"no"]],"#A36B6B","${if (dark) "#B5B7B0" else "#888D87"}"],
                "line-dasharray":[2,2],"line-width":1.3}}""",
        """{"id":"$id-road-labels","type":"symbol","source":"$id","source-layer":"road-detail","minzoom":13,
            "layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["Inter-Regular"],"text-size":11},
            "paint":{"text-color":"${if (dark) "#E4DFD4" else "#64553E"}","text-halo-color":"${if (dark) "#303334" else "#FAF7F1"}","text-halo-width":1.3}}""",
    )
}
