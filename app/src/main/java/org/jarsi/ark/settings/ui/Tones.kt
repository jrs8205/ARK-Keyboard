package org.jarsi.ark.settings.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sävyn vaaleuden asettaminen CIELAB-avaruudessa (LCH). Luminanssi
 * riippuu vain vaaleudesta L*, joten tone 90 -pinta ja tone 30 -teksti
 * antavat vähintään 7:1 sävystä riippumatta. Tätä käytetään, kun laitteen
 * dynaaminen paletti antaa kortille keskitumman pohjan (Android 16:n
 * paletti antaa primaryContainerille tone 50:n ja valkoisen tekstin,
 * mitattu 3.11.2026), jotta kortti pysyy värillisenä ja silti AAA.
 */
object Tones {

    /** Sävykulma asteina 0–360 CIELAB-avaruudessa. */
    fun hueOf(argb: Int): Double {
        val (l, a, b) = lab(argb)
        if (l <= 0.0) return 0.0
        val hue = Math.toDegrees(atan2(b, a))
        return if (hue < 0) hue + 360.0 else hue
    }

    /** Kylläisyys CIELAB-avaruudessa; harmaalla nolla. */
    fun chromaOf(argb: Int): Double {
        val (_, a, b) = lab(argb)
        return kotlin.math.sqrt(a * a + b * b)
    }

    /**
     * Sama sävy kuin [argb], mutta vaaleus [tone] ja kylläisyys enintään
     * [chroma]. Harmaalla lähteellä sävykulma on kohinaa, joten kylläisyys
     * rajataan lähteen omaan eikä johdettu väri värjäydy.
     */
    fun tone(argb: Int, tone: Double, chroma: Double): Int =
        lchToArgb(tone, minOf(chroma, chromaOf(argb)), hueOf(argb))

    /**
     * LCH → sRGB. Jos väri ei mahdu sRGB-avaruuteen, kylläisyyttä
     * vähennetään askel kerrallaan, kunnes se mahtuu; vaaleus ei muutu.
     */
    fun lchToArgb(l: Double, c: Double, h: Double): Int {
        var chroma = c
        while (chroma >= 0.0) {
            val rad = Math.toRadians(h)
            val rgb = labToLinearRgb(l, chroma * cos(rad), chroma * sin(rad))
            if (rgb.all { it >= -0.002 && it <= 1.002 }) {
                return argb(rgb.map { gamma(it.coerceIn(0.0, 1.0)) })
            }
            chroma -= 1.0
        }
        val gray = gamma(labToLinearRgb(l, 0.0, 0.0)[0].coerceIn(0.0, 1.0))
        return argb(listOf(gray, gray, gray))
    }

    private fun lab(argb: Int): Triple<Double, Double, Double> {
        val r = linear((argb shr 16 and 0xFF) / 255.0)
        val g = linear((argb shr 8 and 0xFF) / 255.0)
        val b = linear((argb and 0xFF) / 255.0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun labToLinearRgb(l: Double, a: Double, b: Double): List<Double> {
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = 0.95047 * fInverse(fx)
        val y = fInverse(fy)
        val z = 1.08883 * fInverse(fz)
        return listOf(
            3.2406 * x - 1.5372 * y - 0.4986 * z,
            -0.9689 * x + 1.8758 * y + 0.0415 * z,
            0.0557 * x - 0.2040 * y + 1.0570 * z,
        )
    }

    private const val DELTA = 6.0 / 29.0

    private fun f(t: Double): Double =
        if (t > DELTA * DELTA * DELTA) Math.cbrt(t) else t / (3 * DELTA * DELTA) + 4.0 / 29.0

    private fun fInverse(t: Double): Double =
        if (t > DELTA) t * t * t else 3 * DELTA * DELTA * (t - 4.0 / 29.0)

    private fun linear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun gamma(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun argb(rgb: List<Double>): Int {
        val r = (rgb[0] * 255).roundToInt().coerceIn(0, 255)
        val g = (rgb[1] * 255).roundToInt().coerceIn(0, 255)
        val b = (rgb[2] * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
