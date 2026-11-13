package com.example.newsflash

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

object ProgramadorNoticias {

    // Nombre único que identifica la tarea periódica.
    const val NOMBRE_TAREA = "actualizacion_automatica_newsflash"

    // Restricciones que debe cumplir el dispositivo.
    private fun crearRestricciones(): Constraints {

        return Constraints.Builder()

            // La tarea necesita conexión a Internet.
            .setRequiredNetworkType(NetworkType.CONNECTED)

            .build()
    }

    // Programa la consulta automática de noticias.
    fun programarNoticias(context: Context) {

        // Creamos una tarea que se repite cada 15 minutos.
        val solicitud =
            PeriodicWorkRequestBuilder<NoticiasWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(crearRestricciones())
                .build()

        // KEEP evita registrar varias tareas periódicas iguales.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                NOMBRE_TAREA,
                ExistingPeriodicWorkPolicy.KEEP,
                solicitud
            )
    }

    // Programa una consulta que se ejecutará una sola vez.
    // El parámetro permite solicitar una notificación de prueba.
    fun revisarAhora(
        context: Context,
        modoPrueba: Boolean
    ): UUID {

        val solicitud =
            OneTimeWorkRequestBuilder<NoticiasWorker>()

                // La consulta también necesita Internet.
                .setConstraints(crearRestricciones())

                // Enviamos al Worker el modo de ejecución.
                .setInputData(
                    workDataOf(
                        NoticiasWorker.CLAVE_MODO_PRUEBA to modoPrueba
                    )
                )
                .build()

        // Enviamos el trabajo a WorkManager.
        WorkManager.getInstance(context).enqueue(solicitud)

        // Devolvemos su identificador para observar su ejecución.
        return solicitud.id
    }
}
