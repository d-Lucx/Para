package com.dlucx.para

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A malformed style can compile successfully but prevent every map layer rendering. */
class OfflineMapStyleTest {
    @Test fun lightStyleHasValidLayerAndSourceStructure() = verify(dark = false)
    @Test fun darkStyleHasValidLayerAndSourceStructure() = verify(dark = true)

    private fun verify(dark: Boolean) {
        val manifest = JsonParser.parseString(File("src/main/assets/streets/index.json").readText()).asJsonArray
        val packs = manifest.map { it.asJsonObject.get("id").asString }.map { StreetPack(it, "pmtiles://file:///streets-$it.pmtiles") }
        val json = styleJson("pmtiles://file:///corridor.pmtiles", packs, "#FFFFFF", "#006A68", "#6E7B7A",
            "#CDD7D6", "#99C8DE", "#875000", "#B4232B", "#EFF2F9", "#E8ECF2",
            listOf(DetailMapPack("pilot", "pmtiles://file:///pilot.pmtiles", PilotBoundsJson),
                DetailMapPack("tagum-north", "pmtiles://file:///tagum-north.pmtiles",
                    """{"type":"Polygon","coordinates":[[[125.51,7.48],[125.89,7.48],[125.89,7.72],[125.51,7.72],[125.51,7.48]]]}"""),
                DetailMapPack("region-xi", "pmtiles://file:///region-xi.pmtiles",
                    """{"type":"Polygon","coordinates":[[[125,6],[126,6],[126,8],[125,8],[125,6]],[[125.51,6.99],[125.51,7.48],[125.89,7.48],[125.89,6.99],[125.51,6.99]]]}""",
                    rectangular = false)),
            dark)
        val style = JsonParser.parseString(json).asJsonObject
        val layers = style.getAsJsonArray("layers").map { it.asJsonObject }
        val ids = layers.map { it.get("id").asString }
        assertEquals("Duplicate IDs break native styles", ids.size, ids.toSet().size)
        assertTrue("The old pilot rectangle must stay hidden", "coverage" !in ids)
        for (id in listOf("approach", "pilot-paths")) {
            val paint = layers.single { it.get("id").asString == id }.getAsJsonObject("paint")
            assertTrue("Keep the existing dash styling on $id", paint.has("line-dasharray"))
        }
        val sources = style.getAsJsonObject("sources").keySet()
        val regionRings = style.getAsJsonObject("sources").getAsJsonObject("region-xi-bounds")
            .getAsJsonObject("data").getAsJsonObject("geometry").getAsJsonArray("coordinates")
        assertEquals("The regional background must leave the pilot hole uncovered", 2, regionRings.size())
        layers.filter { it.has("source") }.forEach { assertTrue("Unknown source on ${it.get("id")}", it.get("source").asString in sources) }
        assertTrue("Map must remain local", style.get("glyphs").asString.startsWith("asset://"))
        for (id in listOf("national-labels", "pilot-stop-labels")) {
            val layout = layers.single { it.get("id").asString == id }.getAsJsonObject("layout")
            assertTrue("Catalog labels must render without per-name images", !layout.has("icon-image"))
            assertEquals("Inter-Regular", layout.getAsJsonArray("text-font")[0].asString)
        }
        sources.forEach { name ->
            val source = style.getAsJsonObject("sources").getAsJsonObject(name)
            if (source.has("url")) assertTrue(source.get("url").asString.startsWith("pmtiles://file:///"))
        }
        layers.forEach { layer ->
            layer.getAsJsonObject("paint")?.entrySet()?.forEach { (name, expression) ->
                if (expression.toString().contains("[\"zoom\"]")) {
                    assertTrue("Zoom must be the input of the outer camera ramp: $name", expression.isJsonArray &&
                        expression.asJsonArray[0].asString in listOf("interpolate", "step"))
                }
            }
        }
        File("build/style-probes/${if (dark) "dark" else "light"}.json").apply { parentFile?.mkdirs(); writeText(json) }
    }
}
