package com.albanmorera.ratonvirtual

/**
 * AJUSTES (los mismos nombres que en el programa del ordenador cuando existen).
 * Las distancias "PX" son píxeles de la pantalla del teléfono.
 */
object Config {
    const val AUTOR = "Albán Morera Peralta"
    const val EMAIL = "albanmoreram.p@gmail.com"

    // --- Recuadro activo (fracción de la imagen de la cámara) ---
    const val MARGIN_X = 0.08f
    const val MARGIN_TOP = 0.06f
    const val MARGIN_BOTTOM = 0.16f
    const val SENSIBILIDAD_DEFECTO = 1.3f

    // --- Suavizado adaptativo ---
    const val ALPHA_LENTO = 0.20f
    const val ALPHA_RAPIDO = 0.85f
    const val VELOCIDAD_RAPIDA_PX = 110f
    const val DEAD_ZONE_PX = 2f

    // --- Pinza índice + pulgar = DEDO sobre la pantalla ---
    // Juntar = apoyar el dedo · separar = levantarlo. Rápida = toque,
    // quieta = mantener pulsado, moviendo la mano = arrastrar / deslizar.
    const val PINCH_THRESHOLD = 0.30f     // por debajo -> se apoya el dedo
    const val PINCH_RELEASE = 0.38f       // por encima -> se levanta
    const val PINCH_RELEASE_DELTA = 0.12f // o si se separa esto desde lo más cerrado
    const val RELEASE_GRACE = 0.07        // s que se espera antes de levantar (parpadeos)
    const val DRAG_START_PX = 14f         // movimiento para empezar a arrastrar (si no, es toque)
    const val PINCH_APPROACH = 0.55f
    const val DOUBLE_TAP_WINDOW = 0.8     // el 2º toque cae en el mismo punto que el 1º
    const val TAP_LOCK_RADIUS_PX = 120f
    const val REWIND_MAX_AGE = 0.6
    const val REWIND_MAX_PX = 150f
    const val CLIC_CERRANDO_DELTA = 0.05f
    const val FRENO_FACTOR = 0.35f
    const val FRENO_RECUPERACION = 0.5f
    const val TAP_MS = 50L                // duración de un toque corto
    const val HOLD_START_MS = 700L        // quieto este tiempo -> se apoya el dedo (pulsación larga)
    const val VF_DRAG_PX = 12f            // moverse esto -> se apoya el dedo y arrastra
    const val FOLLOW_FACTOR = 0.7f        // el dedo alcanza a la mano un 70 % en cada tramo
    const val FLING_WINDOW_MS = 160L      // velocidad = lo que se movió la mano en este tiempo
    const val FLING_STALE_MS = 220L       // si la mano llevaba este tiempo quieta -> sin impulso
    const val FLING_MIN_SPEED = 300f      // px/s mínimos para dar impulso al soltar
    const val FLING_EXTRA_S = 0.12f       // el dedo sigue 0,12 s con esa velocidad al soltar
    const val DRAG_ALPHA_MIN = 0.55f      // suavizado mínimo al arrastrar (menos retraso)
    const val FINGER_SEGMENT_MS = 40L     // el arrastre se envía a Android en tramos de 40 ms

    // --- Índice + medio juntos (derecha = Recientes, izquierda = Inicio) ---
    const val RIGHT_THRESHOLD = 0.27f
    const val RIGHT_RELEASE = 0.38f

    // --- Desplazar con el pulgar 👍 / 👎 ---
    const val THUMB_ANGLE = 40.0
    const val THUMB_EXT_MIN = 0.65f
    const val SCROLL_START_DELAY = 0.20
    const val SCROLL_INTERVAL = 0.40      // segundos entre dos deslizamientos
    const val SWIPE_MS = 300L             // duración de cada deslizamiento
    const val SCROLL_MIN = 0.20f          // recorrido (fracción del alto de pantalla) al empezar
    const val SCROLL_MAX = 0.45f          // recorrido tras mantenerlo SCROLL_ACCEL_TIME
    const val SCROLL_ACCEL_TIME = 1.5
    const val SCROLL_INVERTIDO = false

    // --- Mano izquierda: Atrás / Inicio ---
    const val WINDOW_COOLDOWN = 0.6
    const val SIDE_STABLE_FRAMES = 3

    // --- Pausa con el puño ---
    const val PAUSE_HOLD_TIME = 1.0

    const val HAND_GRACE_FRAMES = 4
}
