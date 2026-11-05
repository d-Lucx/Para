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
            "#CDD7D6", "#99C8DE", "#875000", "#B4232B", "#EFF2F9", "#E8ECF2", "pmtiles://file:///pilot.pmtiles", dark)
        val style = JsonParser.parseString(json).asJsonObject
        val layers = style.getAsJsonArray("layers").map { it.asJsonObject }
        val ids = layers.map { it.get("id").asString }
        assertEquals("Duplicate IDs break native styles", ids.size, ids.toSet().size)
        val sources = style.getAsJsonObject("sources").keySet()
        layers.filter { it.has("source") }.forEach { assertTrue("Unknown source on ${it.get("id")}", it.get("source").asString in sources) }
        assertTrue("Map must remain local", style.get("glyphs").asString.startsWith("asset://"))
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
