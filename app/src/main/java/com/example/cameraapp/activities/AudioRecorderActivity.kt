package com.example.cameraapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.cameraapp.R
import com.example.cameraapp.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderActivity : AppCompatActivity() {
    private lateinit var btnRecord: ImageButton
    private lateinit var tvTimer: TextView
    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var progressBarLevel: ProgressBar

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var startTime: Long = 0
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var sensitivity = 50
    private var outputFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_recorder)

        btnRecord = findViewById(R.id.btnRecord)
        tvTimer = findViewById(R.id.tvTimer)
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        progressBarLevel = findViewById(R.id.progressBarLevel)

        seekBarSensitivity.progress = sensitivity
        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionsAndStartRecording()
            }
        }
    }

    private fun checkPermissionsAndStartRecording() {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9- necesita WRITE_EXTERNAL_STORAGE para DCIM
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Android 10-12
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 13+
            requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            startRecording()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startRecording()
            } else {
                // Verifica si algún permiso fue denegado permanentemente
                val shouldShowRationale = permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (!shouldShowRationale) {
                    // Permiso denegado permanentemente, guiar al usuario a configuración
                    showPermissionSettingsDialog()
                } else {
                    Toast.makeText(this, "Permisos necesarios no concedidos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("Para grabar audio, necesitas conceder los permisos en configuración")
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startRecording() {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            Toast.makeText(this, "Almacenamiento externo no disponible", Toast.LENGTH_LONG).show()
            return
        }

        try {
            outputFile = getOutputFile()

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
                setOutputFile(outputFile?.absolutePath)

                try {
                    prepare()
                    start()

                    isRecording = true
                    btnRecord.setImageResource(R.drawable.ic_stop)
                    startTime = System.currentTimeMillis()
                    startTimer()
                    monitorAudioLevel()
                } catch (e: IOException) {
                    Log.e("AudioRecorder", "prepare() failed", e)
                    Toast.makeText(this@AudioRecorderActivity,
                        "Error al preparar la grabación: ${e.message}",
                        Toast.LENGTH_LONG).show()
                    release()
                    mediaRecorder = null
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al crear archivo: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("AudioRecorder", "Error al crear archivo", e)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime


                // Guardar metadatos en la base de datos
                outputFile?.let { file ->
                    val audioMetadata = com.example.cameraapp.database.entities.AudioMetadata(
                        id = file.name.hashCode(),
                        filePath = file.absolutePath,
                        dateRecorded = startTime,
                        duration = duration,
                        sensitivity = sensitivity
                    )

                    GlobalScope.launch(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@AudioRecorderActivity).mediaDao().insertAudio(audioMetadata)
                    }
                }

                Toast.makeText(
                    this@AudioRecorderActivity,
                    "Audio guardado en: CameraApp/Audios",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IllegalStateException) {
            Log.e("AudioRecorder", "Error al detener la grabación", e)
            Toast.makeText(this, "Error al detener la grabación", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error general al detener la grabación", e)
            Toast.makeText(this, "Error al guardar el audio", Toast.LENGTH_SHORT).show()
        } finally {
            mediaRecorder = null
            isRecording = false
            btnRecord.setImageResource(R.drawable.ic_mic)
            stopTimer()
            outputFile = null
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            @SuppressLint("DefaultLocale")
            override fun run() {
                val millis = System.currentTimeMillis() - startTime
                val seconds = (millis / 1000).toInt()
                val minutes = seconds / 60
                val hours = minutes / 60

                tvTimer.text = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable as Runnable)
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable as Runnable)
        tvTimer.text = "00:00:00"
    }

    private fun monitorAudioLevel() {
        val audioLevelRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    val level = (amplitude / 32767.0 * 100 * (sensitivity / 100.0)).toInt()
                    progressBarLevel.progress = level.coerceIn(0, 100)
                    progressBarLevel.postDelayed(this, 100)
                }
            }
        }
        progressBarLevel.post(audioLevelRunnable)
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
    }

    private fun getOutputFile(): File {
        val mediaDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Para Android 10+ (API 29+)
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "CameraApp/Audios"
            ).apply {
                if (!exists()) mkdirs()
            }
        } else {
            // Para versiones anteriores
            File(
                Environment.getExternalStorageDirectory(),
                "DCIM/CameraApp/Audios"
            ).apply {
                if (!exists()) mkdirs()
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(mediaDir, "AUD_${timeStamp}.mp4")
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 101
    }
}