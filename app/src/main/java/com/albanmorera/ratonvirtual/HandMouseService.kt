package com.albanmorera.ratonvirtual

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max

/**
 * El ratón virtual. Es un servicio de ACCESIBILIDAD porque solo así una app
 * puede tocar la pantalla dentro de otras apps (dispatchGesture) y pulsar
 * Atrás / Inicio. Además:
 *   • abre la cámara FRONTAL (CameraX) y analiza la mano con MediaPipe,
 *   • dibuja el cursor encima de todo (ventana de accesibilidad),
 *   • muestra una notificación fija (Android lo exige para usar la cámara
 *     con la app en segundo plano) con "Pausar" y "Parar".
 */
class HandMouseService : AccessibilityService(), LifecycleOwner, MouseActions {

    companion object {
        private const val TAG = "RatonVirtual"
        private const val CHANNEL = "raton"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.albanmorera.ratonvirtual.PARAR"
        const val ACTION_PAUSE = "com.albanmorera.ratonvirtual.PAUSAR"

        /** El servicio, si está activado en Accesibilidad (si no, null). */
        @Volatile
        var instance: HandMouseService? = null
            private set
    }

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = registry

    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var landmarker: HandLandmarker? = null
    private var forceCpu = false
    private var errors = 0
    private var lastTs = 0L
    private lateinit var engine: GestureEngine
    private var cursor: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    @Volatile
    private var invertHands = false

    /** True mientras la cámara y el cursor están funcionando. */
    var running = false
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP -> stopMouse()
                ACTION_PAUSE -> executor?.execute { engine.togglePause(nowSec()) }
            }
        }
    }

    // ------------------------------------------------------------- ciclo de vida
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registry.currentState = Lifecycle.State.CREATED
        val (w, h) = screenSize()
        engine = GestureEngine(w, h, this)
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP)
            addAction(ACTION_PAUSE)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (running) stopMouse()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
        instance = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::engine.isInitialized) return
        val (w, h) = screenSize()
        val ex = executor
        if (ex != null) {
            ex.execute { engine.screenW = w; engine.screenH = h }
        } else {
            engine.screenW = w
            engine.screenH = h
        }
        analysis?.targetRotation = displayRotation()
    }

    // ------------------------------------------------------------ iniciar / parar
    /** Arranca cámara + cursor. Devuelve null si va bien, o el motivo del error. */
    fun startMouse(): String? {
        if (running) return null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            return "Falta el permiso de cámara (paso 1)"
        }
        applySettings()
        try {
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground", e)
            return "Android no deja usar la cámara ahora: ${e.message}"
        }
        val ex = Executors.newSingleThreadExecutor()
        executor = ex
        running = true
        showCursor()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running) return@addListener
            try {
                val provider = future.get()
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
                val a = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(displayRotation())
                    .build()
                a.setAnalyzer(ex) { proxy -> analyze(proxy) }
                provider.unbindAll()
                registry.currentState = Lifecycle.State.RESUMED
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, a)
                cameraProvider = provider
                analysis = a
            } catch (e: Exception) {
                Log.e(TAG, "camara", e)
                toast("No se pudo abrir la cámara: ${e.message}")
                stopMouse()
            }
        }, ContextCompat.getMainExecutor(this))
        return null
    }

    /** Para cámara + cursor (el servicio sigue activado en Accesibilidad). */
    fun stopMouse() {
        running = false
        finger.release()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        cameraProvider = null
        analysis = null
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.currentState = Lifecycle.State.CREATED
        }
        val ex = executor
        executor = null
        ex?.execute {
            landmarker?.close()
            landmarker = null
        }
        ex?.shutdown()
        hideCursor()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    /** Vuelve a leer los ajustes (invertir manos, velocidad). */
    fun applySettings() {
        invertHands = Prefs.invertHands(this)
        val s = Prefs.sensitivity(this)
        val ex = executor
        if (ex != null) {
            ex.execute { engine.sensitivity = s }
        } else {
            engine.sensitivity = s
        }
    }

    // --------------------------------------------------------------- cámara + IA
    private fun createLandmarker(): HandLandmarker? {
        val delegates = if (forceCpu) listOf(Delegate.CPU) else listOf(Delegate.GPU, Delegate.CPU)
        for (delegate in delegates) {
            try {
                val base = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(delegate)
                    .build()
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.4f)
                    .setMinTrackingConfidence(0.4f)
                    .build()
                val lm = HandLandmarker.createFromOptions(this, options)
                landmarker = lm
                forceCpu = delegate == Delegate.CPU
                Log.i(TAG, "Detector de manos listo ($delegate)")
                return lm
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo usar $delegate", e)
            }
        }
        main.post {
            toast("No se pudo cargar el modelo de la mano")
            stopMouse()
        }
        return null
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!running) return
            val lm = landmarker ?: createLandmarker() ?: return
            val raw = proxy.toBitmap()
            // Girar según el móvil y hacer ESPEJO (igual que en el ordenador)
            val m = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val ts = max(SystemClock.uptimeMillis(), lastTs + 1)
            lastTs = ts
            val result = lm.detectForVideo(BitmapImageBuilder(bmp).build(), ts)
            handleResult(result, bmp.width.toFloat(), bmp.height.toFloat())
            errors = 0
        } catch (e: Exception) {
            Log.e(TAG, "analizar", e)
            if (++errors > 5 && !forceCpu) {      // la GPU falla: probar con el procesador
                landmarker?.close()
                landmarker = null
                forceCpu = true
            }
        } finally {
            proxy.close()
        }
    }

    private fun handleResult(r: HandLandmarkerResult, w: Float, h: Float) {
        val now = nowSec()
        val hands = r.landmarks()
        if (hands.isEmpty()) {
            engine.process(null, null, w, h, now)
            return
        }
        val pts = hands[0].map { P(it.x() * w, it.y() * h, it.z() * w) }
        var side: String? = null
        val hd = r.handedness()
        if (hd.isNotEmpty() && hd[0].isNotEmpty()) {
            side = when (hd[0][0].categoryName()) {
                "Right" -> "derecha"
                "Left" -> "izquierda"
                else -> null
            }
            if (side != null && invertHands) side = if (side == "derecha") "izquierda" else "derecha"
        }
        engine.process(Hand(pts), side, w, h, now)
    }

    private fun nowSec() = SystemClock.uptimeMillis() / 1000.0

    // ------------------------------------------------------ acciones (MouseActions)
    override fun moveCursor(x: Float, y: Float) {
        main.post { placeCursor(x, y) }
    }

    override fun fingerDown(x: Float, y: Float) {
        main.post { finger.press(x, y) }
    }

    override fun fingerMove(x: Float, y: Float) {
        main.post { finger.move(x, y) }
    }

    override fun fingerUp() {
        main.post { finger.release() }
    }

    override fun swipe(x: Float, y1: Float, y2: Float, durationMs: Long) {
        main.post { gesture(stroke(x, y1, x, y2, 0, durationMs)) }
    }

    override fun back() {
        main.post { performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    override fun home() {
        main.post { performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    override fun recents() {
        main.post { performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
    }

    override fun status(state: CursorState, label: String) {
        main.post { cursor?.update(state, label) }
    }

    private fun stroke(x1: Float, y1: Float, x2: Float, y2: Float, start: Long, duration: Long):
        GestureDescription.StrokeDescription {
        val path = Path().apply {
            moveTo(max(0f, x1), max(0f, y1))
            if (x1 != x2 || y1 != y2) lineTo(max(0f, x2), max(0f, y2))
        }
        return GestureDescription.StrokeDescription(path, start, duration)
    }

    private fun gesture(vararg strokes: GestureDescription.StrokeDescription) {
        if (finger.isDown) return               // no interrumpir un dedo apoyado
        try {
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "gesto", e)
        }
    }

    /**
     * DEDO VIRTUAL: Android no deja "apoyar un dedo" y moverlo libremente, pero sí
     * enviar un gesto en TRAMOS encadenados (continueStroke). Cada tramo dura
     * FINGER_SEGMENT_MS; al terminar uno se envía el siguiente hasta la última
     * posición de la mano. Si la mano no se mueve, el tramo es "quieto" (el dedo
     * sigue apoyado -> Android lo entiende como mantener pulsado).
     * Todo en el hilo principal.
     */
    private inner class VirtualFinger {
        var isDown = false
            private set
        private var stroke: GestureDescription.StrokeDescription? = null
        private var inFlight = false
        private var upRequested = false
        private var lastX = 0f
        private var lastY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private var generation = 0                // cada pulsación nueva = otra generación

        /** Aviso de Android al acabar cada tramo (se ignoran los de pulsaciones viejas). */
        private fun callbackFor(gen: Int) = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (gen != generation) return
                inFlight = false
                next()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (gen != generation) return
                inFlight = false
                reset()                           // Android lo ha interrumpido
            }
        }

        // El dedo NO se apoya al cerrar la pinza: se decide después.
        //  - abrir rápido sin mover  -> un toque corto (tap) de verdad
        //  - mover la mano           -> el dedo se apoya YA en el punto inicial y arrastra
        //  - mantener quieto         -> se apoya pasado HOLD_START_MS (pulsación larga)
        private var pending = false
        private var pressX = 0f
        private var pressY = 0f
        private val startHold = Runnable { if (pending) activate() }

        fun press(x: Float, y: Float) {
            if (isDown || pending) return
            pending = true
            samples.clear()
            pressX = max(0f, x); pressY = max(0f, y)
            targetX = pressX; targetY = pressY
            main.postDelayed(startHold, Config.HOLD_START_MS)
        }

        // Últimas posiciones de la mano (para calcular la velocidad al soltar)
        private val samples = ArrayDeque<Triple<Long, Float, Float>>()

        fun move(x: Float, y: Float) {
            targetX = max(0f, x)
            targetY = max(0f, y)
            val t = SystemClock.uptimeMillis()
            samples.addLast(Triple(t, targetX, targetY))
            while (samples.size > 2 && t - samples.first().first > Config.FLING_WINDOW_MS) samples.removeFirst()
            if (pending && hypot(targetX - pressX, targetY - pressY) > Config.VF_DRAG_PX) activate()
        }

        fun release() {
            if (pending) {                      // toque corto
                pending = false
                main.removeCallbacks(startHold)
                gesture(stroke(pressX, pressY, pressX, pressY, 0, Config.TAP_MS))
                return
            }
            if (!isDown) return
            upRequested = true
            if (!inFlight) next()
        }

        private fun activate() {
            pending = false
            main.removeCallbacks(startHold)
            generation++
            isDown = true
            upRequested = false
            lastX = pressX; lastY = pressY
            val s = GestureDescription.StrokeDescription(
                segment(lastX, lastY, lastX, lastY), 0, Config.FINGER_SEGMENT_MS, true)
            stroke = s
            send(s)
        }

        private fun next() {
            val prev = stroke ?: return
            if (!isDown) return
            val last = upRequested
            val nx: Float
            val ny: Float
            if (last) {
                // Al soltar: llegar a la mano y SEGUIR un poco con su velocidad,
                // como cuando deslizas con el dedo (si no, la página vuelve atrás).
                val (vx, vy) = velocity()
                val (sw, sh) = screenSize()
                nx = (targetX + vx * Config.FLING_EXTRA_S).coerceIn(0f, sw - 1f)
                ny = (targetY + vy * Config.FLING_EXTRA_S).coerceIn(0f, sh - 1f)
            } else {
                // Seguir a la mano con suavidad (sin tramos quietos y saltos)
                val dx = targetX - lastX
                val dy = targetY - lastY
                if (hypot(dx, dy) < 2f) {
                    nx = targetX; ny = targetY
                } else {
                    nx = lastX + dx * Config.FOLLOW_FACTOR
                    ny = lastY + dy * Config.FOLLOW_FACTOR
                }
            }
            val s = try {
                prev.continueStroke(segment(lastX, lastY, nx, ny), 0,
                    Config.FINGER_SEGMENT_MS, !last)
            } catch (e: Exception) {
                Log.w(TAG, "dedo", e)
                reset()
                return
            }
            lastX = nx; lastY = ny
            if (last) {
                isDown = false
                stroke = null
                upRequested = false
            } else {
                stroke = s
            }
            send(s)
        }

        /** Velocidad de la mano (px/s) justo antes de soltar; 0 si ya estaba parada. */
        private fun velocity(): Pair<Float, Float> {
            val now = SystemClock.uptimeMillis()
            if (samples.size < 2 || now - samples.last().first > Config.FLING_STALE_MS) return Pair(0f, 0f)
            val a = samples.first()
            val b = samples.last()
            val dt = (b.first - a.first) / 1000f
            if (dt <= 0.02f) return Pair(0f, 0f)
            val vx = (b.second - a.second) / dt
            val vy = (b.third - a.third) / dt
            return if (hypot(vx, vy) < Config.FLING_MIN_SPEED) Pair(0f, 0f) else Pair(vx, vy)
        }

        private fun send(s: GestureDescription.StrokeDescription) {
            try {
                inFlight = dispatchGesture(GestureDescription.Builder().addStroke(s).build(),
                    callbackFor(generation), null)
            } catch (e: Exception) {
                Log.w(TAG, "dedo", e)
                inFlight = false
            }
            if (!inFlight) reset()
        }

        private fun reset() {
            pending = false
            main.removeCallbacks(startHold)
            isDown = false
            stroke = null
            upRequested = false
        }

        private fun segment(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
    }

    private val finger = VirtualFinger()

    // -------------------------------------------------------------------- cursor
    private fun windowManager() = getSystemService(WINDOW_SERVICE) as WindowManager

    private fun showCursor() {
        if (cursor != null) return
        val v = CursorView(this)
        val p = WindowManager.LayoutParams(
            v.viewW, v.viewH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (engine.curX - v.tipOffset).toInt()
            y = (engine.curY - v.tipOffset).toInt()
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager().addView(v, p)
            cursor = v
            cursorParams = p
        } catch (e: Exception) {
            Log.e(TAG, "cursor", e)
        }
    }

    private fun placeCursor(x: Float, y: Float) {
        val v = cursor ?: return
        val p = cursorParams ?: return
        p.x = (x - v.tipOffset).toInt()
        p.y = (y - v.tipOffset).toInt()
        try {
            windowManager().updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    private fun hideCursor() {
        val v = cursor ?: return
        try {
            windowManager().removeView(v)
        } catch (_: Exception) {
        }
        cursor = null
        cursorParams = null
    }

    // ------------------------------------------------------------------ utilidades
    private fun screenSize(): Pair<Float, Float> {
        val wm = windowManager()
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width().toFloat(), b.height().toFloat())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels.toFloat(), m.heightPixels.toFloat())
        }
    }

    private fun displayRotation(): Int =
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    private fun toast(text: String) {
        main.post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ratón virtual", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getBroadcast(this, 1, Intent(ACTION_STOP).setPackage(packageName), flags)
        val pause = PendingIntent.getBroadcast(this, 2, Intent(ACTION_PAUSE).setPackage(packageName), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_cursor)
            .setContentTitle("AI Virtual Mouse activo")
            .setContentText("Puño 1 s = pausa · Creado por ${Config.AUTOR}")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Pausar / reanudar", pause)
            .addAction(0, "Parar", stop)
            .build()
    }
}
