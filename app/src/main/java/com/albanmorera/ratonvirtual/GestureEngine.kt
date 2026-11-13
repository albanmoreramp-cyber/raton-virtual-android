package com.albanmorera.ratonvirtual

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Lo que el motor de gestos le pide al servicio (dedo en la pantalla, cursor...). */
interface MouseActions {
    fun moveCursor(x: Float, y: Float)
    /** Apoyar el dedo en (x, y). */
    fun fingerDown(x: Float, y: Float)
    /** Mover el dedo apoyado a (x, y). */
    fun fingerMove(x: Float, y: Float)
    /** Levantar el dedo. */
    fun fingerUp()
    fun swipe(x: Float, y1: Float, y2: Float, durationMs: Long)
    fun back()
    fun home()
    fun recents()
    fun status(state: CursorState, label: String)
}

enum class CursorState { NO_HAND, MOVING, SLOW, PINCH, SCROLL, PAUSED }

/**
 * Convierte la mano de cada frame en acciones. Basado en GestureController del
 * programa del ordenador (suavizado, freno al ir a tocar, punto "rebobinado",
 * pausa con el puño...), pero la pinza funciona como un DEDO de verdad:
 * juntar = apoyar, mover la mano = arrastrar, separar = levantar.
 */
class GestureEngine(
    var screenW: Float,
    var screenH: Float,
    private val actions: MouseActions,
) {
    var sensitivity = Config.SENSIBILIDAD_DEFECTO

    // Cursor (posición suavizada)
    var curX = screenW / 2f
        private set
    var curY = screenH / 2f
        private set

    // Pinza = dedo
    private var pinchArmed = true
    var fingerIsDown = false
        private set
    private var dragging = false
    private var pressKx = 0f            // nudillo del índice al apoyar el dedo (cámara)
    private var pressKy = 0f
    private var selKx = 0f              // nudillo suavizado mientras se arrastra
    private var selKy = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var pinchMin = 1f
    private var releaseSince: Double? = null

    // 2º toque en el mismo punto
    private var lastTapTime: Double? = null
    private var lockUntil = 0.0
    private var lockX = 0f
    private var lockY = 0f
    private var lockValid = false
    private var lockKx = 0f
    private var lockKy = 0f

    // Punto "de antes de cerrar los dedos" y freno al ir a tocar
    private var openPos: Pair<Float, Float>? = null
    private var openPosT = 0.0
    private val pinchHist = ArrayDeque<Pair<Double, Float>>()
    private var aimUntil = 0.0

    // Freno (desfase entre la mano y el cursor)
    private var prevAx = 0f
    private var prevAy = 0f
    private var prevAbsT = -1.0
    private var offX = 0f
    private var offY = 0f

    // Desplazar
    private var scrollDir = 0
    private var scrollSince = 0.0
    private var lastSwipe = -10.0

    // Índice + medio (derecha = Recientes, izquierda = Inicio) y pinza izquierda (Atrás)
    private var vArmed = true
    private var winPinchArmed = true
    private var lastNavTime = -10.0
    private var stableSide: String? = null
    private var pendingSide: String? = null
    private var pendingCount = 0

    // Pausa
    var paused = false
        private set
    private var fistStart: Double? = null
    private var fistArmed = true

    private var lostFrames = 0
    private var label = ""
    private var labelUntil = 0.0

    private fun show(text: String, now: Double, seconds: Double = 0.8) {
        label = text
        labelUntil = now + seconds
    }

    fun togglePause(now: Double) = setPaused(!paused, now)

    private fun setPaused(p: Boolean, now: Double) {
        paused = p
        if (p) {
            resetScroll()
            releaseFinger(null, now)
        }
        show(if (p) "PAUSA" else "ACTIVADO", now, 1.0)
    }

    // ----------------------------------------------------------------- mapeo
    private fun box(w: Float, h: Float) = floatArrayOf(
        Config.MARGIN_X * w, Config.MARGIN_TOP * h, w - Config.MARGIN_X * w, h - Config.MARGIN_BOTTOM * h,
    )

    private fun mapToScreen(x: Float, y: Float, w: Float, h: Float): Pair<Float, Float> {
        val b = box(w, h)
        val nx = (((x - b[0]) / (b[2] - b[0]) - 0.5f) * sensitivity + 0.5f).coerceIn(0f, 1f)
        val ny = (((y - b[1]) / (b[3] - b[1]) - 0.5f) * sensitivity + 0.5f).coerceIn(0f, 1f)
        return Pair(nx * (screenW - 1f), ny * (screenH - 1f))
    }

    private fun clampX(x: Float) = x.coerceIn(1f, screenW - 2f)
    private fun clampY(y: Float) = y.coerceIn(1f, screenH - 2f)

    /** Píxeles de pantalla por píxel de cámara. */
    private fun scale(w: Float): Float {
        val b = box(w, 1f)
        return screenW * sensitivity / (b[2] - b[0])
    }

    private fun adaptiveAlpha(distPx: Float): Float {
        val t = min(distPx / Config.VELOCIDAD_RAPIDA_PX, 1f)
        return Config.ALPHA_LENTO + (Config.ALPHA_RAPIDO - Config.ALPHA_LENTO) * t
    }

    // ----------------------------------------------------------------- mover
    private fun moveCursor(ix: Float, iy: Float, w: Float, h: Float,
                           kx: Float, ky: Float, slow: Boolean, now: Double) {
        val (ax, ay) = mapToScreen(ix, iy, w, h)
        var dx = 0f
        var dy = 0f
        if (prevAbsT >= 0 && now - prevAbsT <= 0.25) {
            dx = ax - prevAx
            dy = ay - prevAy
        }
        prevAx = ax
        prevAy = ay
        prevAbsT = now

        // Tras un toque el cursor se queda clavado un momento (para un 2º toque)
        if (now < lockUntil && lockValid) {
            if (hypot(kx - lockKx, ky - lockKy) * scale(w) < Config.TAP_LOCK_RADIUS_PX) return
            lockUntil = 0.0
            lastTapTime = null
        }

        // Freno: el cursor recorre solo FRENO_FACTOR de lo que se mueve la mano;
        // el desfase se recupera poco a poco al moverla sin frenar.
        if (slow) {
            offX += dx * (Config.FRENO_FACTOR - 1f)
            offY += dy * (Config.FRENO_FACTOR - 1f)
        } else {
            val off = hypot(offX, offY)
            if (off > 0f) {
                val k = max(0f, off - hypot(dx, dy) * Config.FRENO_RECUPERACION) / off
                offX *= k
                offY *= k
            }
        }
        val tx = clampX(ax + offX)
        val ty = clampY(ay + offY)
        offX = tx - ax
        offY = ty - ay

        val alpha = adaptiveAlpha(hypot(tx - curX, ty - curY))
        val nx = curX + (tx - curX) * alpha
        val ny = curY + (ty - curY) * alpha
        val dead = Config.DEAD_ZONE_PX * (if (slow) 0.5f else 1f)
        if (abs(nx - curX) > dead || abs(ny - curY) > dead) {
            curX = nx
            curY = ny
            actions.moveCursor(curX, curY)
        }
    }

    private fun go(x: Float, y: Float) {
        curX = x
        curY = y
        prevAbsT = -1.0
        actions.moveCursor(curX, curY)
    }

    // --------------------------------------------- freno al ir a tocar / punto
    private fun noteOpen(pinch: Float, now: Double) {
        if (pinch > Config.PINCH_APPROACH) {
            openPos = Pair(curX, curY)
            openPosT = now
        }
        pinchHist.addLast(Pair(now, pinch))
        while (pinchHist.isNotEmpty() && now - pinchHist.first().first > 0.2) pinchHist.removeFirst()
        if (pinch > Config.PINCH_APPROACH) {
            aimUntil = 0.0
        } else if (pinchHist.maxOf { it.second } - pinch >= Config.CLIC_CERRANDO_DELTA) {
            aimUntil = now + 0.25                 // los dedos se están cerrando
        }
    }

    private fun rewound(now: Double): Pair<Float, Float> {
        val pos = openPos
        if (pos != null && now - openPosT <= Config.REWIND_MAX_AGE &&
            hypot(pos.first - curX, pos.second - curY) <= Config.REWIND_MAX_PX) {
            return Pair(clampX(pos.first), clampY(pos.second))
        }
        return Pair(clampX(curX), clampY(curY))
    }

    // ------------------------------------------------------------ pinza = dedo
    private fun pressFinger(kx: Float, ky: Float, w: Float, pinch: Float, now: Double) {
        val last = lastTapTime
        val again = last != null && now - last <= Config.DOUBLE_TAP_WINDOW && lockValid &&
            hypot(kx - lockKx, ky - lockKy) * scale(w) < Config.TAP_LOCK_RADIUS_PX
        // 2º toque seguido: en el MISMO punto que el 1º. Si no, donde estaba el
        // cursor antes de cerrar los dedos (al cerrarlos la punta del índice se mueve).
        val (x, y) = if (again) Pair(lockX, lockY) else rewound(now)
        go(x, y)
        fingerX = x
        fingerY = y
        actions.fingerDown(x, y)
        fingerIsDown = true
        dragging = false
        pressKx = kx; pressKy = ky
        selKx = kx; selKy = ky
        pinchMin = pinch
        releaseSince = null
        pinchArmed = false
        show("●", now, 0.3)
    }

    /** Con el dedo apoyado: arrastrar siguiendo el NUDILLO (no se mueve al hacer la pinza). */
    private fun updateFinger(kx: Float, ky: Float, w: Float, now: Double) {
        val sc = scale(w)
        if (!dragging) {
            if (hypot(kx - pressKx, ky - pressKy) * sc < Config.DRAG_START_PX) return
            dragging = true                        // se ha movido: ya no es un toque
        }
        val alpha = max(adaptiveAlpha(hypot(kx - selKx, ky - selKy) * sc), Config.DRAG_ALPHA_MIN)
        val nkx = selKx + (kx - selKx) * alpha
        val nky = selKy + (ky - selKy) * alpha
        val dx = (nkx - selKx) * sc
        val dy = (nky - selKy) * sc
        selKx = nkx; selKy = nky
        if (abs(dx) > Config.DEAD_ZONE_PX || abs(dy) > Config.DEAD_ZONE_PX) {
            fingerX = clampX(fingerX + dx)
            fingerY = clampY(fingerY + dy)
            curX = fingerX
            curY = fingerY
            actions.fingerMove(fingerX, fingerY)
            actions.moveCursor(curX, curY)
        }
    }

    /** Levanta el dedo. tip = punta del índice ahora (para que el cursor no salte). */
    private fun releaseFinger(tip: Pair<Float, Float>?, now: Double) {
        if (!fingerIsDown) return
        actions.fingerUp()
        fingerIsDown = false
        releaseSince = null
        if (dragging) {
            lastTapTime = null
            show("", now, 0.0)
        } else {
            // Toque: el cursor se queda clavado un momento por si das otro toque
            lastTapTime = now
            lockUntil = now + Config.DOUBLE_TAP_WINDOW
            lockX = fingerX; lockY = fingerY
            lockKx = pressKx; lockKy = pressKy
            lockValid = true
            show("TOQUE", now, 0.4)
        }
        dragging = false
        // Que el cursor siga donde está y alcance a la mano poco a poco
        if (tip != null) {
            offX = curX - tip.first
            offY = curY - tip.second
        }
        prevAbsT = -1.0
    }

    // ------------------------------------------------------------- desplazar
    private fun updateScroll(dir: Int, now: Double) {
        if (dir != scrollDir) {
            scrollDir = dir
            scrollSince = now
            return
        }
        val held = now - scrollSince
        if (held < Config.SCROLL_START_DELAY) return
        if (now - lastSwipe >= Config.SCROLL_INTERVAL) {
            val t = min((held - Config.SCROLL_START_DELAY) / Config.SCROLL_ACCEL_TIME, 1.0).toFloat()
            val dist = screenH * (Config.SCROLL_MIN + (Config.SCROLL_MAX - Config.SCROLL_MIN) * t)
            // Lejos de los bordes: arriba están las notificaciones y abajo el gesto de Inicio
            val top = screenH * 0.12f
            val bottom = screenH * 0.88f
            val yc = curY.coerceIn(top + dist / 2f, max(top + dist / 2f, bottom - dist / 2f))
            val x = curX.coerceIn(screenW * 0.12f, screenW * 0.88f)
            // Pulgar arriba = ver lo de arriba = el "dedo" baja por la pantalla
            var down = dir > 0
            if (Config.SCROLL_INVERTIDO) down = !down
            val y1 = if (down) yc - dist / 2f else yc + dist / 2f
            val y2 = if (down) yc + dist / 2f else yc - dist / 2f
            actions.swipe(x, y1.coerceIn(top, bottom), y2.coerceIn(top, bottom), Config.SWIPE_MS)
            lastSwipe = now
        }
    }

    private fun resetScroll() {
        scrollDir = 0
    }

    // ------------------------------------------------------------------ pausa
    private fun updatePause(fist: Boolean, now: Double) {
        if (!fist) {
            fistStart = null
            fistArmed = true
            return
        }
        if (!fistArmed) return
        val start = fistStart ?: now.also { fistStart = it }
        if (now - start >= Config.PAUSE_HOLD_TIME) {
            fistArmed = false
            fistStart = null
            setPaused(!paused, now)
        }
    }

    // ------------------------------------------------------------- navegación
    private fun nav(what: String, now: Double) {
        if (now - lastNavTime < Config.WINDOW_COOLDOWN) return
        lastNavTime = now
        when (what) {
            "atras" -> { actions.back(); show("ATRÁS", now) }
            "inicio" -> { actions.home(); show("INICIO", now) }
            "recientes" -> { actions.recents(); show("RECIENTES", now) }
        }
    }

    private fun stabilize(raw: String?): String? {
        if (raw == stableSide || stableSide == null) {
            stableSide = raw
            pendingCount = 0
        } else if (raw == pendingSide) {
            pendingCount++
            if (pendingCount >= Config.SIDE_STABLE_FRAMES) {
                stableSide = raw
                pendingCount = 0
            }
        } else {
            pendingSide = raw
            pendingCount = 1
        }
        return stableSide
    }

    // ---------------------------------------------------- bucle de cada frame
    /**
     * @param hand mano detectada (null = no hay mano)
     * @param rawSide "derecha" / "izquierda" (ya corregido con "invertir manos")
     * @param w @param h tamaño de la imagen de la cámara
     * @param now segundos (reloj monotónico)
     */
    fun process(hand: Hand?, rawSide: String?, w: Float, h: Float, now: Double) {
        if (hand == null) {
            lostFrames++
            if (lostFrames > Config.HAND_GRACE_FRAMES) {
                releaseFinger(null, now)
                resetScroll()
                updatePause(false, now)
                stableSide = null
                pendingCount = 0
                actions.status(if (paused) CursorState.PAUSED else CursorState.NO_HAND, currentLabel(now))
            }
            return
        }
        lostFrames = 0

        val kx = hand[Hand.INDEX_MCP].x
        val ky = hand[Hand.INDEX_MCP].y
        val ix = hand[Hand.INDEX_TIP].x
        val iy = hand[Hand.INDEX_TIP].y
        val pinch = hand.pinchRatio
        val v = hand.vRatio
        val pinchClosing = pinch < Config.PINCH_RELEASE
        val twoFingers = hand.index && hand.middle && !hand.ring && !hand.pinky

        noteOpen(pinch, now)
        val fist = hand.isFist()
        updatePause(fist && !fingerIsDown, now)

        val side = stabilize(rawSide)
        val leftHand = side == "izquierda"
        val aiming = now < aimUntil && !leftHand

        var state = if (aiming) CursorState.SLOW else CursorState.MOVING
        when {
            paused -> {
                state = CursorState.PAUSED
                resetScroll()
            }
            // ---- Dedo apoyado (pinza cerrada): arrastrar / mantener ----
            fingerIsDown -> {
                state = CursorState.PINCH
                pinchMin = min(pinchMin, pinch)
                val opened = pinch > Config.PINCH_RELEASE ||
                    (pinch > Config.PINCH_THRESHOLD + 0.02f && pinch >= pinchMin + Config.PINCH_RELEASE_DELTA)
                if (opened) {
                    val since = releaseSince ?: now.also { releaseSince = it }
                    if (now - since >= Config.RELEASE_GRACE) {
                        val (ax, ay) = mapToScreen(ix, iy, w, h)
                        releaseFinger(Pair(ax, ay), now)
                        pinchArmed = false        // para el siguiente, separar del todo
                    }
                } else {
                    releaseSince = null
                    updateFinger(kx, ky, w, now)
                    if (dragging) show("ARRASTRANDO", now, 0.2)
                }
            }
            fist -> resetScroll()
            else -> {
                val dir = if (pinchClosing) 0 else hand.thumbDirection()
                if (dir == 0) resetScroll()
                when {
                    dir != 0 -> {
                        updateScroll(dir, now)
                        state = CursorState.SCROLL
                    }
                    // Mano IZQUIERDA: pinza = Atrás, índice + medio juntos = Inicio
                    leftHand && pinchClosing -> {
                        state = CursorState.PINCH
                        if (winPinchArmed && pinch < Config.PINCH_THRESHOLD && hand.indexNotCurled()) {
                            nav("atras", now)
                            winPinchArmed = false
                        }
                    }
                    // Mano DERECHA: pinza = apoyar el dedo
                    pinchClosing -> {
                        state = CursorState.PINCH
                        if (pinchArmed && pinch < Config.PINCH_THRESHOLD && hand.indexNotCurled()) {
                            pressFinger(kx, ky, w, pinch, now)
                        }
                    }
                    // Índice + medio: separados mueven; juntos = Recientes (der.) / Inicio (izq.)
                    twoFingers && v < Config.RIGHT_RELEASE -> {
                        state = CursorState.PINCH
                        if (vArmed && v < Config.RIGHT_THRESHOLD) {
                            nav(if (leftHand) "inicio" else "recientes", now)
                            vArmed = false
                        }
                    }
                    twoFingers || hand.index -> moveCursor(ix, iy, w, h, kx, ky, aiming, now)
                }
            }
        }

        // Rearme (los dedos tienen que separarse)
        if (pinch > Config.PINCH_RELEASE) {
            pinchArmed = true
            winPinchArmed = true
        }
        if (v > Config.RIGHT_RELEASE || !twoFingers) vArmed = true
        actions.status(state, currentLabel(now))
    }

    private fun currentLabel(now: Double) = if (now < labelUntil) label else ""
}
