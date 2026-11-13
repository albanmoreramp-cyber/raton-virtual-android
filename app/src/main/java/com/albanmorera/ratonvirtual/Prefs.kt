package com.albanmorera.ratonvirtual

import android.content.Context

/** Ajustes que se guardan en el teléfono (invertir manos, velocidad del cursor). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    // Con la cámara frontal MediaPipe da las manos al revés (como en el ordenador),
    // así que por defecto se invierten. Clave nueva para no heredar el ajuste viejo.
    fun invertHands(c: Context): Boolean = sp(c).getBoolean("invertir_manos_v2", true)
    fun setInvertHands(c: Context, v: Boolean) = sp(c).edit().putBoolean("invertir_manos_v2", v).apply()

    fun sensitivity(c: Context): Float = sp(c).getFloat("sensibilidad", Config.SENSIBILIDAD_DEFECTO)
    fun setSensitivity(c: Context, v: Float) = sp(c).edit().putFloat("sensibilidad", v).apply()
}
