# 🖱️📱 AI Virtual Mouse — Android

**Controla el móvil con la mano, sin tocar la pantalla.** La app usa la cámara frontal
para seguir tu mano y mueve un cursor en pantalla; con gestos toca, mantiene, arrastra,
vuelve atrás, va a Inicio, abre Recientes y desplaza. Funciona sobre **cualquier app**
y sigue activa aunque salgas de ella. El análisis se hace solo en el teléfono: las
imágenes **no se guardan ni se envían a ningún sitio**.

Creado por **Albán Morera Peralta** · albanmoreram.p@gmail.com
Software propietario, todos los derechos reservados (ver [`LICENSE`](LICENSE)).

## ¿Para qué sirve?

- Usar el móvil **sin tocarlo**: con las manos mojadas o sucias (cocinando, en el taller), con el móvil en un soporte, para ver vídeos o leer a distancia.
- Accesibilidad para quien no puede usar bien la pantalla táctil.
- Un ratón virtual como el que se ve al conectar un ratón por USB-C, pero controlado con la mano.

## Gestos

**Mano derecha**

| Gesto | Acción |
|---|---|
| ☝️ Índice estirado | Mover el cursor |
| 🤏 Pinza rápida | Tocar |
| 🤏 Pinza quieta (~1 s) | Mantener pulsado |
| 🤏 Pinza + mover la mano | Arrastrar / deslizar: cambiar de pantalla, bajar notificaciones, mover iconos |
| ✌️ Índice + medio juntos | Recientes (apps abiertas) |
| 👍 / 👎 Pulgar arriba / abajo | Desplazar arriba / abajo |

**Mano izquierda**

| Gesto | Acción |
|---|---|
| 🤏 Pinza índice + pulgar | Atrás |
| ✌️ Índice + medio juntos | Inicio |

**Cualquier mano:** ✊ puño ~1 s → pausar / reanudar.

La pinza de la mano derecha funciona como tu dedo en la pantalla: juntar los dedos = apoyarlo, mover la mano = arrastrarlo, separar = levantarlo.

## Funciones

- Cursor flotante con indicador de estado, sobre cualquier app.
- Servicio en segundo plano con notificación (**Pausar / reanudar** y **Parar**).
- Ajuste de sensibilidad y opción **Invertir manos** si las detecta al revés.
- Detección de la mano en el propio móvil con MediaPipe (GPU si está disponible).

## Cómo instalarla

**Opción A — APK (la más fácil):** descarga el `.apk` de la sección **Releases**, ábrelo en el móvil y permite instalar apps de origen desconocido. Requiere Android 8.0 o superior.

**Opción B — desde el código:** abre esta carpeta con Android Studio, conecta el móvil con la depuración USB activada y pulsa ▶. La guía paso a paso está en [`LEEME.md`](LEEME.md).

**Dentro de la app (3 pasos):**
1. Dar permisos de cámara y notificaciones.
2. Activar **AI Virtual Mouse** en Accesibilidad (necesario para tocar la pantalla por ti).
3. Pulsar **Iniciar ratón**.

Pon el móvil de pie en un soporte, con la cámara frontal mirando a tu mano, a unos 40–60 cm.

## Privacidad y límites

- Usa el permiso de Accesibilidad solo para mover el cursor y simular toques y gestos.
- Algunas pantallas protegidas no dejan tocar (apps de bancos, pantalla de bloqueo, permisos del sistema).
- Gasta batería y puede calentar el móvil con un uso continuo.
