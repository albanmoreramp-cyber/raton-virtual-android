package com.albanmorera.ratonvirtual

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Pantalla de la app: los 3 pasos para ponerlo en marcha (permisos,
 * accesibilidad, iniciar), los ajustes y la tabla de gestos.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var sensLabel: TextView
    private var dp = 1f

    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(32))
        }

        root.addView(txt("AI Virtual Mouse 🖱️", 24f, bold = true))
        root.addView(txt("Controla el móvil con la mano usando la cámara frontal", 15f,
            color = Color.DKGRAY))

        status = txt("", 15f, bold = true).apply {
            setPadding(px(12), px(10), px(12), px(10))
            setBackgroundColor(Color.rgb(240, 236, 255))
        }
        root.addView(spacer(12))
        root.addView(status)

        // --- Paso 1 ---
        root.addView(header("1. Permisos"))
        root.addView(txt("La cámara (para ver tu mano) y las notificaciones (Android muestra un " +
            "aviso fijo mientras se usa la cámara)."))
        root.addView(button("Dar permisos") { askPermissions() })

        // --- Paso 2 ---
        root.addView(header("2. Activar en Accesibilidad"))
        root.addView(txt("Busca \"AI Virtual Mouse\" en Accesibilidad (puede estar dentro de " +
            "\"Apps instaladas\" o \"Aplicaciones descargadas\") y actívalo."))
        root.addView(button("Abrir Accesibilidad") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(txt("¿El interruptor sale en gris o dice \"Ajuste restringido\"? Es normal al " +
            "instalar una app fuera de la Play Store: pulsa el botón de abajo, toca ⋮ (arriba a la " +
            "derecha) → \"Permitir ajustes restringidos\", y vuelve a activarlo.", 13f,
            color = Color.DKGRAY))
        root.addView(button("Abrir información de la app") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)))
        })

        // --- Paso 3 ---
        root.addView(header("3. Iniciar"))
        root.addView(txt("Pon el móvil de pie (en un soporte), con la cámara frontal mirando a tu " +
            "mano, a unos 40-60 cm. Después puedes salir de la app: el cursor sigue funcionando."))
        startButton = button("Iniciar ratón") { toggleMouse() }
        root.addView(startButton)

        // --- Ajustes ---
        root.addView(header("Ajustes"))
        val invert = Switch(this).apply {
            text = "Invertir manos (actívalo o desactívalo si la mano derecha va Atrás en vez de tocar)"
            textSize = 15f
            isChecked = Prefs.invertHands(this@MainActivity)
            setPadding(0, px(6), 0, px(6))
            setOnCheckedChangeListener { _, checked ->
                Prefs.setInvertHands(this@MainActivity, checked)
                HandMouseService.instance?.applySettings()
            }
        }
        root.addView(invert)
        val sensRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sensLabel = txt("", 15f)
        sensRow.addView(button(" − ") { changeSensitivity(-0.1f) })
        sensRow.addView(sensLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { leftMargin = px(12) })
        sensRow.addView(button(" + ") { changeSensitivity(0.1f) })
        root.addView(sensRow)

        // --- Gestos ---
        root.addView(header("Gestos"))
        root.addView(txt(
            "MANO DERECHA\n" +
                "👉  Índice estirado → mover el cursor\n" +
                "🤏  La pinza índice + pulgar es tu DEDO en la pantalla:\n" +
                "      • pinza rápida → tocar\n" +
                "      • pinza quieta → mantener pulsado\n" +
                "      • pinza y mover la mano → arrastrar / deslizar\n" +
                "        (pasar de pantalla, bajar notificaciones, subir desde\n" +
                "        la barra de abajo...)\n" +
                "✌️  Índice + medio juntos → Recientes (apps abiertas)\n" +
                "👍  Pulgar arriba → desplazar hacia arriba\n" +
                "👎  Pulgar abajo → desplazar hacia abajo\n\n" +
                "MANO IZQUIERDA\n" +
                "🤏  Pinza índice + pulgar → Atrás\n" +
                "✌️  Índice + medio juntos → Inicio\n\n" +
                "CUALQUIER MANO\n" +
                "✊  Puño 1 s → pausar / reanudar\n\n" +
                "El punto del cursor indica el estado: morado = moviendo, naranja = despacio " +
                "(vas a tocar), verde = dedo apoyado, azul = desplazando, rojo = pausa, gris = no ve la mano.",
            15f))

        root.addView(spacer(20))
        root.addView(txt("Creado por ${Config.AUTOR}\n${Config.EMAIL}", 13f, color = Color.GRAY))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // ------------------------------------------------------------------ acciones
    private fun hasCamera() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun toggleMouse() {
        val service = HandMouseService.instance
        when {
            !hasCamera() -> toast("Primero da el permiso de cámara (paso 1)")
            service == null -> toast("Primero activa AI Virtual Mouse en Accesibilidad (paso 2)")
            service.running -> service.stopMouse()
            else -> {
                val error = service.startMouse()
                if (error != null) toast(error)
                else toast("¡En marcha! Ya puedes salir de la app (mano izquierda: índice + medio = Inicio)")
            }
        }
        refresh()
    }

    private fun changeSensitivity(delta: Float) {
        val s = (Prefs.sensitivity(this) + delta).coerceIn(1.0f, 3.0f)
        Prefs.setSensitivity(this, s)
        HandMouseService.instance?.applySettings()
        refresh()
    }

    private fun refresh() {
        val service = HandMouseService.instance
        val lines = mutableListOf<String>()
        lines += if (hasCamera()) "✅ Permiso de cámara" else "❌ Falta el permiso de cámara"
        lines += if (service != null) "✅ Accesibilidad activada" else "❌ Accesibilidad sin activar"
        lines += if (service?.running == true) "🟢 Ratón funcionando" else "⚪ Ratón parado"
        status.text = lines.joinToString("\n")
        startButton.text = if (service?.running == true) "Parar ratón" else "Iniciar ratón"
        sensLabel.text = String.format(Locale.US, "Velocidad del cursor: %.1f", Prefs.sensitivity(this))
    }

    // ------------------------------------------------------------- vistas sencillas
    private fun txt(t: String, size: Float = 15f, bold: Boolean = false,
                     color: Int = Color.BLACK) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun header(t: String) = txt(t, 18f, bold = true).apply {
        setPadding(0, px(20), 0, px(4))
        setTextColor(Color.rgb(91, 33, 182))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun spacer(h: Int) = TextView(this).apply { height = px(h) }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
