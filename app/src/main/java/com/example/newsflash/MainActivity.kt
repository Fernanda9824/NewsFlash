package com.example.newsflash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import androidx.work.WorkInfo
import androidx.work.WorkManager

import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Declaramos las referencias de los elementos de la pantalla.
    private lateinit var tvEstado: TextView
    private lateinit var tvNoticia: TextView
    private lateinit var tvFecha: TextView

    private lateinit var btnSincronizar: Button
    private lateinit var btnProbar: Button
    private lateinit var btnAbrir: Button

    // Guardamos el enlace de la última noticia.
    private var enlaceNoticia = ""

    // Controlador para solicitar el permiso de notificaciones.
    private val permisoNotificaciones =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permitido ->

            if (permitido) {

                tvEstado.text =
                    "Permiso concedido. NewsFlash puede enviar alertas."

            } else {

                tvEstado.text =
                    "Debes permitir las notificaciones para recibir alertas."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Conectamos la actividad con el diseño XML.
        setContentView(R.layout.activity_main)

        // Relacionamos las variables con los elementos visuales.
        tvEstado = findViewById(R.id.tvEstado)
        tvNoticia = findViewById(R.id.tvNoticia)
        tvFecha = findViewById(R.id.tvFecha)

        btnSincronizar = findViewById(R.id.btnSincronizar)
        btnProbar = findViewById(R.id.btnProbarNotificacion)
        btnAbrir = findViewById(R.id.btnAbrirNoticia)

        // Creamos el canal que utilizarán las notificaciones.
        NotificacionHelper.crearCanal(this)

        // Solicitamos el permiso si la versión de Android lo requiere.
        solicitarPermiso()

        // Registramos la tarea automática de 15 minutos.
        ProgramadorNoticias.programarNoticias(this)

        // Consultamos los datos guardados.
        actualizarDatos()

        // Observamos los cambios de la tarea periódica.
        observarTareaPeriodica()

        // Evento del botón de sincronización manual.
        btnSincronizar.setOnClickListener {

            ejecutarConsulta(false)
        }

        // Evento del botón de prueba.
        btnProbar.setOnClickListener {

            if (tienePermisoNotificaciones()) {

                // Ejecutamos el mismo Worker, pero forzando una alerta.
                ejecutarConsulta(true)

            } else {

                Toast.makeText(
                    this,
                    "Permite las notificaciones para realizar la prueba.",
                    Toast.LENGTH_LONG
                ).show()

                solicitarPermiso()
            }
        }

        // Evento para abrir el enlace de la noticia.
        btnAbrir.setOnClickListener {

            abrirNoticia()
        }
    }

    // Solicita el permiso de notificaciones de Android 13 en adelante.
    private fun solicitarPermiso() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permiso = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permiso != PackageManager.PERMISSION_GRANTED) {

                permisoNotificaciones.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // Comprueba si se permite enviar notificaciones.
    private fun tienePermisoNotificaciones(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    // Ejecuta una consulta y observa su resultado.
    private fun ejecutarConsulta(modoPrueba: Boolean) {

        // Solicitamos a ProgramadorNoticias iniciar el Worker.
        val idTrabajo = ProgramadorNoticias.revisarAhora(
            this,
            modoPrueba
        )

        tvEstado.text = "Esperando la consulta de noticias..."

        // Observamos el estado de ejecución del trabajo.
        observarTrabajo(idTrabajo)
    }

    // Vigila el estado de un trabajo individual.
    private fun observarTrabajo(id: UUID) {

        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(id)
            .observe(this) { informacion ->

                if (informacion != null) {

                    when (informacion.state) {

                        WorkInfo.State.RUNNING -> {

                            tvEstado.text =
                                "Consultando noticias en Internet..."
                        }

                        WorkInfo.State.SUCCEEDED -> {

                            // Actualizamos la pantalla cuando finaliza.
                            actualizarDatos()
                        }

                        WorkInfo.State.FAILED -> {

                            tvEstado.text =
                                "No se pudo completar la consulta."
                        }

                        else -> {

                            if (informacion.state == WorkInfo.State.ENQUEUED) {
                                tvEstado.text =
                                    "Trabajo pendiente o esperando conexión..."
                            }
                        }
                    }
                }
            }
    }

    // Observa la tarea automática registrada por WorkManager.
    private fun observarTareaPeriodica() {

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(
                ProgramadorNoticias.NOMBRE_TAREA
            )
            .observe(this) { trabajos ->

                // Si WorkManager está ejecutando el Worker,
                // lo indicamos en la interfaz.
                if (trabajos.any {
                        it.state == WorkInfo.State.RUNNING
                    }) {

                    tvEstado.text =
                        "Actualizando información de noticias..."

                } else {

                    actualizarDatos()
                }
            }
    }

    // Recupera los datos guardados por el Worker.
    private fun actualizarDatos() {

        val preferencias = getSharedPreferences(
            DatosNewsFlash.NOMBRE_PREFERENCIAS,
            MODE_PRIVATE
        )

        // Recuperamos el estado de la última sincronización.
        val estado = preferencias.getString(
            DatosNewsFlash.ESTADO,
            "Esperando la primera sincronización."
        )

        // Recuperamos el último titular.
        val titulo = preferencias.getString(
            DatosNewsFlash.ULTIMO_TITULO,
            "Aún no hay noticias disponibles."
        )

        // Recuperamos el enlace de la noticia.
        enlaceNoticia = preferencias.getString(
            DatosNewsFlash.ULTIMO_ENLACE,
            ""
        ).orEmpty()

        // Recuperamos la fecha de consulta.
        val fecha = preferencias.getString(
            DatosNewsFlash.FECHA,
            "Pendiente"
        )

        // Mostramos la información en pantalla.
        tvEstado.text = estado
        tvNoticia.text = titulo
        tvFecha.text = "Última consulta: $fecha"

        // El botón solo estará disponible si tenemos un enlace.
        btnAbrir.isEnabled = enlaceNoticia.isNotBlank()
    }

    // Abre la noticia mediante el navegador del dispositivo.
    private fun abrirNoticia() {

        if (enlaceNoticia.isNotBlank()) {

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(enlaceNoticia)
            )

            startActivity(intent)

        } else {

            Toast.makeText(
                this,
                "Todavía no hay una noticia para abrir.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Actualizamos la información cuando volvemos a la actividad.
    override fun onResume() {
        super.onResume()

        if (::tvEstado.isInitialized) {
            actualizarDatos()
        }
    }
}
