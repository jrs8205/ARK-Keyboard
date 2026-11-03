package org.jarsi.ark.settings.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Kontrastivahti asetussivulle: WCAG 2:n kontrastisuhde ja tekstivärin
 * valinta sen mukaan. Värit ARGB-kokonaislukuina, jotta logiikka pysyy
 * puhtaana Kotlinina ja testattavana ilman Composea.
 *
 * Teksti vaatii AAA-tason 7:1, säätimet ja kuvakkeet 3:1. Dynaamiset
 * Material You -paletit täyttävät nämä sävyjen vaaleuseroilla, mutta
 * valmistajan muokkaama paletti voi poiketa; silloin teksti putoaa
 * varaväriin (onSurface) eikä koskaan alle rajan.
 */
object Contrast {

    /** Kontrastisuhde 1.0–21.0 WCAG 2:n kaavalla, järjestyksestä riippumatta. */
    fun ratio(argb1: Int, argb2: Int): Double {
        val l1 = luminance(argb1)
        val l2 = luminance(argb2)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /**
     * Tekstiväri taustalle [background]: [preferred], jos se täyttää
     * [minimum]-rajan, muuten [fallback], jos se täyttää; muuten näistä
     * kahdesta se, jonka suhde on suurempi.
     */
    fun textOn(background: Int, preferred: Int, fallback: Int, minimum: Double = 7.0): Int {
        val preferredRatio = ratio(background, preferred)
        if (preferredRatio >= minimum) return preferred
        val fallbackRatio = ratio(background, fallback)
        if (fallbackRatio >= minimum) return fallback
        return if (preferredRatio >= fallbackRatio) preferred else fallback
    }

    /** Järjestelmän fonttiskaala rajattuna: asetussivu kasvaa enintään [maximum]-kertaiseksi. */
    fun cappedFontScale(system: Float, maximum: Float = 1.5f): Float = min(system, maximum)

    private fun luminance(argb: Int): Double {
        val r = channel(argb shr 16 and 0xFF)
        val g = channel(argb shr 8 and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
