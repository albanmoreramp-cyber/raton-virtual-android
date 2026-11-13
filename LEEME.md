# AI Virtual Mouse para Android 🖱️📱

Creado por **Albán Morera Peralta** · albanmoreram.p@gmail.com

Controla el móvil con la mano usando la cámara frontal (versión 1).

## Gestos

| Mano | Gesto | Acción |
|---|---|---|
| Derecha | 👉 Índice estirado | Mover el cursor |
| Derecha | 🤏 Pinza rápida | Tocar |
| Derecha | 🤏 Pinza quieta | Mantener pulsado |
| Derecha | 🤏 Pinza + mover la mano | Arrastrar / deslizar (pasar de pantalla, bajar notificaciones, subir desde la barra de abajo...) |
| Derecha | ✌️ Índice + medio juntos | Recientes (apps abiertas) |
| Derecha | 👍 / 👎 Pulgar arriba / abajo | Desplazar arriba / abajo |
| Izquierda | 🤏 Pinza índice + pulgar | Atrás |
| Izquierda | ✌️ Índice + medio juntos | Inicio |
| Cualquiera | ✊ Puño 1 s | Pausar / reanudar |

La pinza de la mano derecha funciona como tu dedo en la pantalla: juntar los
dedos = apoyarlo, mover la mano = arrastrarlo, separar = levantarlo.

## Cómo instalarla en el móvil (solo la primera vez)

### A) En el móvil: activar la depuración USB
1. **Ajustes → Información del teléfono** y toca **7 veces** "Número de compilación"
   (puede estar dentro de "Información del software"). Te pedirá el PIN.
2. **Ajustes → Sistema → Opciones de desarrollador** → activa **Depuración USB**.

### B) En el ordenador: abrir el proyecto
1. Instala **Android Studio**: https://developer.android.com/studio
2. Descomprime este ZIP. En Android Studio: **File → Open** y elige la carpeta
   **RatonVirtual**.
3. Espera a que termine de preparar el proyecto (abajo pone "Gradle sync"). La
   primera vez tarda unos minutos porque descarga lo necesario.
   - Si te ofrece actualizar el "Android Gradle Plugin", puedes aceptarlo.
   - Si te pide instalar un SDK, acepta.

### C) Instalarla
1. Conecta el móvil por USB. En el móvil sale "¿Permitir depuración USB?": **Permitir**.
2. Arriba en Android Studio aparece tu **Nothing Phone**. Pulsa el botón verde **▶ (Run)**.
3. La app se instala y se abre sola.

> El modelo de la mano (`hand_landmarker.task`) se descarga solo al compilar.
> Si falla, copia el tuyo (el mismo que usa el programa del ordenador) en
> `app/src/main/assets/`.

### D) Dentro de la app
1. **Dar permisos** (cámara y notificaciones).
2. **Abrir Accesibilidad** → busca "AI Virtual Mouse" → actívalo.
   - Si sale en gris / "Ajuste restringido": en la app pulsa **Abrir información
     de la app** → ⋮ (arriba a la derecha) → **Permitir ajustes restringidos**, y
     vuelve a activarlo.
3. **Iniciar ratón**. Ya puedes salir de la app: el cursor sigue funcionando.
   Para pararlo: la notificación "AI Virtual Mouse activo" → **Parar**.

### Primera prueba
- Pon el móvil **de pie en un soporte**, con la cámara frontal mirando a tu mano,
  a unos 40-60 cm.
- Haz la **pinza con la mano izquierda**: debería ir **Atrás**. Si en vez de eso
  hace un toque, cambia **"Invertir manos"** en la app.
- Si el cursor va muy rápido o muy lento, usa **− / +** en la app.

## Si algo falla
- **Error al compilar**: copia el texto rojo del panel "Build" de Android Studio y
  pásamelo.
- **La app se cierra o no hace nada**: en Android Studio, pestaña **Logcat**, escribe
  `RatonVirtual` en el buscador y pásame lo que salga.

## Limitaciones conocidas
- Algunas pantallas protegidas no dejan que la app toque (apps de bancos, la
  pantalla de bloqueo, los ajustes de permisos).
- Gasta batería y calienta el móvil con el uso continuo.
- Las imágenes de la cámara se analizan solo en el teléfono: no se guardan ni se
  envían a ningún sitio.
