package com.dlucx.para

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import org.maplibre.android.maps.Style

internal fun Style.addStreetSymbols() {
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 1.6f
        strokeCap = Paint.Cap.ROUND
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fun symbol(name: String, color: Int, draw: Canvas.() -> Unit) {
        val image = createBitmap(22, 22)
        val canvas = Canvas(image)
        fill.color = color
        canvas.drawCircle(11f, 11f, 10.5f, ink)
        canvas.drawCircle(11f, 11f, 9f, fill)
        canvas.draw()
        addImage(name, image)
    }
    symbol("street-food", 0xFF9B4A1D.toInt()) {
        drawLine(7f, 6f, 7f, 16f, ink)
        drawLine(10f, 6f, 10f, 10f, ink)
        drawLine(7f, 10f, 10f, 10f, ink)
        drawLine(14f, 6f, 14f, 16f, ink)
        drawLine(14f, 11f, 16f, 11f, ink)
    }
    symbol("street-bank", 0xFF31578A.toInt()) {
        drawLine(5f, 9f, 11f, 6f, ink)
        drawLine(11f, 6f, 17f, 9f, ink)
        drawLine(6f, 16f, 16f, 16f, ink)
        for (x in listOf(7f, 11f, 15f)) drawLine(x, 10f, x, 14f, ink)
    }
    symbol("street-health", 0xFF9C343C.toInt()) {
        drawRect(6f, 9.5f, 16f, 12.5f, ink)
        drawRect(9.5f, 6f, 12.5f, 16f, ink)
    }
    symbol("street-fuel", 0xFF765025.toInt()) {
        drawRoundRect(6f, 5f, 13f, 17f, 1f, 1f, ink)
        drawLine(8f, 8f, 11f, 8f, fill)
        drawLine(13f, 12f, 16f, 12f, ink)
        drawLine(16f, 12f, 16f, 8f, ink)
    }
    symbol("street-transit", 0xFF006A68.toInt()) {
        drawRoundRect(5.5f, 6f, 16.5f, 15f, 2f, 2f, ink)
        drawRect(7.5f, 8f, 14.5f, 11f, fill)
        drawLine(8f, 15f, 8f, 17f, ink)
        drawLine(14f, 15f, 14f, 17f, ink)
    }
    symbol("street-place", 0xFF45615E.toInt()) { drawCircle(11f, 11f, 3f, ink) }
    symbol("street-signal", 0xFF283038.toInt()) {
        drawRoundRect(7f, 3.5f, 15f, 18.5f, 2f, 2f, ink)
        listOf(0xFFC8343B, 0xFFCC8900, 0xFF268052).forEachIndexed { index, color ->
            fill.color = color.toInt()
            drawCircle(11f, 7f + 4f * index, 1.6f, fill)
        }
    }
    val arrow = createBitmap(14, 12)
    val canvas = Canvas(arrow)
    for ((color, width) in listOf(0xFFF7F5ED.toInt() to 4f, 0xFF526060.toInt() to 2f)) {
        ink.color = color
        ink.strokeWidth = width
        canvas.drawLine(5f, 3f, 9f, 6f, ink)
        canvas.drawLine(9f, 6f, 5f, 9f, ink)
    }
    addImage("street-oneway", arrow)
}
