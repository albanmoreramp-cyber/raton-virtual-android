package com.albanmorera.ratonvirtual

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/** Un punto de la mano en píxeles de la imagen (z escalado como x). */
data class P(val x: Float, val y: Float, val z: Float)

/**
 * Geometría de la mano: las MISMAS reglas que el programa del ordenador
 * (raton_virtual.py): dedos estirados, puño, pulgar arriba/abajo, pinza...
 */
class Hand(private val p: List<P>) {

    companion object {
        const val WRIST = 0
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_PIP = 6
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_PIP = 10
        const val MIDDLE_TIP = 12
        const val RING_PIP = 14
        const val RING_TIP = 16
        const val PINKY_PIP = 18
        const val PINKY_TIP = 20
    }

    operator fun get(i: Int): P = p[i]

    /** Distancia en la imagen (2D). */
    fun d(a: Int, b: Int): Float = hypot(p[a].x - p[b].x, p[a].y - p[b].y)

    /** Distancia 3D (con profundidad): dos dedos uno delante del otro no "se tocan". */
    fun d3(a: Int, b: Int): Float {
        val dx = p[a].x - p[b].x
        val dy = p[a].y - p[b].y
        val dz = p[a].z - p[b].z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Dedo estirado: su punta está claramente más lejos de la muñeca que su PIP. */
    fun extended(tip: Int, pip: Int): Boolean = d(WRIST, tip) > d(WRIST, pip) * 1.1f

    val index: Boolean by lazy { extended(INDEX_TIP, INDEX_PIP) }
    val middle: Boolean by lazy { extended(MIDDLE_TIP, MIDDLE_PIP) }
    val ring: Boolean by lazy { extended(RING_TIP, RING_PIP) }
    val pinky: Boolean by lazy { extended(PINKY_TIP, PINKY_PIP) }

    private val handSize: Float by lazy { d3(WRIST, MIDDLE_MCP).takeIf { it > 0f } ?: 1f }

    /** Distancia índice-pulgar dividida por el tamaño de la mano (valor "Pinza"). */
    val pinchRatio: Float by lazy { d3(INDEX_TIP, THUMB_TIP) / handSize }

    /** Distancia índice-medio dividida por el tamaño de la mano (valor "V"). */
    val vRatio: Float by lazy { d3(INDEX_TIP, MIDDLE_TIP) / handSize }

    /** El índice no está metido en un puño (evita clics al cerrar la mano). */
    fun indexNotCurled(): Boolean = d(WRIST, INDEX_TIP) > d(WRIST, INDEX_MCP)

    /** True si en la articulación b el dedo se dobla menos de maxDeg grados. */
    private fun bendOk(a: Int, b: Int, c: Int, maxDeg: Double): Boolean {
        val v1x = p[b].x - p[a].x
        val v1y = p[b].y - p[a].y
        val v2x = p[c].x - p[b].x
        val v2y = p[c].y - p[b].y
        val n1 = hypot(v1x, v1y)
        val n2 = hypot(v2x, v2y)
        if (n1 == 0f || n2 == 0f) return false
        return (v1x * v2x + v1y * v2y) / (n1 * n2) >= cos(Math.toRadians(maxDeg))
    }

    /** Pulgar FUERA de la mano: lejos del nudillo del índice, de los dedos doblados y recto. */
    fun thumbExtended(): Boolean {
        val palm = d(WRIST, MIDDLE_MCP).takeIf { it > 0f } ?: 1f
        return d(THUMB_TIP, INDEX_MCP) > Config.THUMB_EXT_MIN * palm &&
            d(THUMB_TIP, MIDDLE_PIP) > 0.5f * palm &&
            bendOk(THUMB_MCP, THUMB_IP, THUMB_TIP, 45.0)
    }

    /** +1 = pulgar ARRIBA 👍, -1 = pulgar ABAJO 👎, 0 = ninguno (resto de dedos cerrados). */
    fun thumbDirection(): Int {
        if (index || middle || ring || pinky) return 0
        if (!thumbExtended()) return 0
        val dx = p[THUMB_TIP].x - p[THUMB_MCP].x
        val dy = p[THUMB_TIP].y - p[THUMB_MCP].y
        val len = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        val limit = cos(Math.toRadians(Config.THUMB_ANGLE))
        return when {
            -dy / len >= limit -> 1
            dy / len >= limit -> -1
            else -> 0
        }
    }

    /** Puño ✊: ningún dedo estirado, índice metido y el pulgar recogido. */
    fun isFist(): Boolean =
        !index && !middle && !ring && !pinky && !indexNotCurled() && !thumbExtended()
}
