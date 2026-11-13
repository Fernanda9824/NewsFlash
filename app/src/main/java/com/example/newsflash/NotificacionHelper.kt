package com.example.newsflash

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificacionHelper {

    // Identificador único del canal de notificaciones.
    private const val ID_CANAL = "canal_noticias"

    // Nombre que aparecerá en los ajustes del dispositivo.
    private const val NOMBRE_CANAL = "Noticias de última hora"

    // Identificador de la notificación.
    private const val ID_NOTIFICACION = 1001

    // Crea el canal de notificaciones.
    fun crearCanal(context: Context) {

        // Los canales son necesarios desde Android 8.0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val canal = NotificationChannel(
                ID_CANAL,
                NOMBRE_CANAL,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "Alertas cuando se detectan noticias nuevas."
            }

            // Registramos el canal en el sistema Android.
            val administrador =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            administrador.createNotificationChannel(canal)
        }
    }

    // Construye y muestra una notificación con la noticia recibida.
    // Devuelve true si pudo enviarla.
    fun mostrarNoticia(
        context: Context,
        titulo: String
    ): Boolean {

        // Verificamos el permiso de Android 13 o superior.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permiso = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permiso != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // También comprobamos que las notificaciones estén habilitadas.
        val administrador =
            NotificationManagerCompat.from(context)

        if (!administrador.areNotificationsEnabled()) {
            return false
        }

        // Creamos el canal por seguridad antes de publicar.
        crearCanal(context)

        // Preparamos el Intent para abrir NewsFlash al tocar la alerta.
        val intent = Intent(context, MainActivity::class.java)

        val pendiente = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // Construimos el diseño de la notificación.
        val notificacion =
            NotificationCompat.Builder(context, ID_CANAL)

                // Icono pequeño que mostrará Android.
                .setSmallIcon(android.R.drawable.ic_dialog_info)

                // Encabezado de la alerta.
                .setContentTitle("¡Nueva noticia en NewsFlash!")

                // Presentamos el titular.
                .setContentText(titulo)

                // Permite mostrar el titular completo al expandirla.
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(titulo)
                )

                // Importancia alta para las versiones anteriores a O.
                .setPriority(NotificationCompat.PRIORITY_HIGH)

                // Permite retirar la alerta cuando el usuario la toca.
                .setAutoCancel(true)

                // Asociamos la acción de abrir la aplicación.
                .setContentIntent(pendiente)

                .build()

        return try {

            // Publicamos la notificación en Android.
            administrador.notify(ID_NOTIFICACION, notificacion)

            true

        } catch (error: SecurityException) {

            // Evitamos que la aplicación falle por falta de permisos.
            false
        }
    }
}
