package com.example.newsflash

import android.content.Context
import android.util.Xml

import androidx.work.Worker
import androidx.work.WorkerParameters

import org.xmlpull.v1.XmlPullParser

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Worker encargado de consultar las noticias en segundo plano.
class NoticiasWorker(
    context: Context,
    parametros: WorkerParameters
) : Worker(context, parametros) {

    companion object {

        // Clave que permite identificar el modo de prueba.
        const val CLAVE_MODO_PRUEBA = "modo_prueba"

        // Dirección del servicio RSS de Google News en México.
        private const val URL_NOTICIAS =
            "https://news.google.com/rss?hl=es-419&gl=MX&ceid=MX:es-419"
    }

    // Representa los datos principales de una noticia.
    data class Noticia(
        val titulo: String,
        val enlace: String,
        val identificador: String
    )

    // Esta función es ejecutada automáticamente por WorkManager.
    override fun doWork(): Result {

        // Recuperamos las preferencias donde guardamos la información.
        val preferencias = applicationContext.getSharedPreferences(
            DatosNewsFlash.NOMBRE_PREFERENCIAS,
            Context.MODE_PRIVATE
        )

        try {

            // Consultamos la noticia más reciente del servicio externo.
            val noticia = consultarNoticia()

            // Recuperamos el identificador de la última noticia guardada.
            val idAnterior =
                preferencias.getString(DatosNewsFlash.ULTIMO_ID, null)

            // Comprobamos si la ejecución es una prueba manual.
            val modoPrueba =
                inputData.getBoolean(CLAVE_MODO_PRUEBA, false)

            // Detectamos si el identificador cambió.
            val esNueva =
                idAnterior != null &&
                        idAnterior != noticia.identificador

            // Si es la primera consulta, guardamos la noticia
            // como referencia para futuras comparaciones.
            if (idAnterior == null || esNueva) {

                preferencias.edit()
                    .putString(
                        DatosNewsFlash.ULTIMO_ID,
                        noticia.identificador
                    )
                    .apply()
            }

            // Solo se genera una alerta cuando hay una noticia nueva
            // o cuando el usuario solicita una prueba.
            val necesitaAlerta = esNueva || modoPrueba

            val notificacionEnviada =
                if (necesitaAlerta) {

                    NotificacionHelper.mostrarNoticia(
                        applicationContext,
                        noticia.titulo
                    )

                } else {
                    false
                }

            // Definimos el resultado de la consulta.
            val nuevoEstado = when {

                modoPrueba && notificacionEnviada ->
                    "Prueba: notificación enviada correctamente."

                modoPrueba ->
                    "Prueba realizada, pero no hay permiso para notificar."

                idAnterior == null ->
                    "Sincronización inicial completada."

                esNueva && notificacionEnviada ->
                    "Noticia nueva detectada. Alerta enviada."

                esNueva ->
                    "Noticia nueva detectada, pero no pudo notificarse."

                else ->
                    "Sin novedades. La sincronización fue correcta."
            }

            // Obtenemos la fecha y hora actuales.
            val fechaActual = SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale("es", "MX")
            ).format(Date())

            // Guardamos toda la información obtenida.
            preferencias.edit()

                .putString(
                    DatosNewsFlash.ULTIMO_TITULO,
                    noticia.titulo
                )

                .putString(
                    DatosNewsFlash.ULTIMO_ENLACE,
                    noticia.enlace
                )

                .putString(
                    DatosNewsFlash.ESTADO,
                    nuevoEstado
                )

                .putString(
                    DatosNewsFlash.FECHA,
                    fechaActual
                )

                .apply()

            // Indicamos a WorkManager que la tarea terminó correctamente.
            return Result.success()

        } catch (error: Exception) {

            // Si ocurre un problema, guardamos el mensaje de error.
            preferencias.edit()

                .putString(
                    DatosNewsFlash.ESTADO,
                    "Error al consultar las noticias: ${error.message}"
                )

                .putString(
                    DatosNewsFlash.FECHA,
                    obtenerFecha()
                )

                .apply()

            // Solicitamos que WorkManager vuelva a intentar la tarea.
            return Result.retry()
        }
    }

    // Realiza la conexión con el servicio externo y obtiene el RSS.
    private fun consultarNoticia(): Noticia {

        // Creamos la conexión HTTP con Google News.
        val conexion =
            URL(URL_NOTICIAS).openConnection() as HttpURLConnection

        try {

            conexion.requestMethod = "GET"

            // Tiempo máximo para establecer la conexión.
            conexion.connectTimeout = 12000

            // Tiempo máximo para recibir los datos.
            conexion.readTimeout = 12000

            // Identificamos nuestra aplicación en la solicitud.
            conexion.setRequestProperty(
                "User-Agent",
                "NewsFlash Android"
            )

            // Comprobamos que el servidor respondió correctamente.
            if (conexion.responseCode !in 200..299) {

                throw IOException(
                    "Respuesta del servidor: ${conexion.responseCode}"
                )
            }

            // Leemos la respuesta XML recibida.
            conexion.inputStream.use { entrada ->

                val lector = Xml.newPullParser()

                lector.setInput(entrada, "UTF-8")

                var evento = lector.eventType

                // Variables para reconocer los datos del primer artículo.
                var dentroNoticia = false

                var etiquetaActual = ""

                var titulo = ""
                var enlace = ""
                var identificador = ""

                // Recorremos el documento XML.
                while (evento != XmlPullParser.END_DOCUMENT) {

                    when (evento) {

                        XmlPullParser.START_TAG -> {

                            // Detectamos el inicio de una noticia.
                            if (lector.name == "item") {

                                dentroNoticia = true

                            } else if (dentroNoticia) {

                                etiquetaActual = lector.name
                            }
                        }

                        XmlPullParser.TEXT -> {

                            // Obtenemos los valores de las etiquetas RSS.
                            if (dentroNoticia) {

                                when (etiquetaActual) {

                                    "title" ->
                                        titulo += lector.text

                                    "link" ->
                                        enlace += lector.text

                                    "guid" ->
                                        identificador += lector.text
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {

                            // El primer item contiene la noticia más reciente.
                            if (
                                lector.name == "item" &&
                                dentroNoticia
                            ) {

                                // Si el identificador está vacío usamos el enlace.
                                val idFinal =
                                    identificador.trim().ifBlank {
                                        enlace.trim()
                                    }

                                if (
                                    titulo.isNotBlank() &&
                                    enlace.isNotBlank() &&
                                    idFinal.isNotBlank()
                                ) {

                                    return Noticia(
                                        titulo.trim(),
                                        enlace.trim(),
                                        idFinal
                                    )
                                }

                                dentroNoticia = false

                            } else if (dentroNoticia) {

                                etiquetaActual = ""
                            }
                        }
                    }

                    evento = lector.next()
                }
            }

            // Se produce un error si el documento no contiene noticias.
            throw IOException("No se encontró una noticia en el RSS.")

        } finally {

            // Cerramos la conexión para liberar recursos.
            conexion.disconnect()
        }
    }

    // Devuelve la fecha y hora de una ejecución.
    private fun obtenerFecha(): String {

        return SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale("es", "MX")
        ).format(Date())
    }
}
