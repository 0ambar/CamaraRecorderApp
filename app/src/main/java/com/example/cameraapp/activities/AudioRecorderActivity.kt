package com.example.cameraapp.activities

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
                startRecording()
            }
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(192000)

            val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.mp4")
            setOutputFile(file.absolutePath)

            try {
                prepare()
                start()

                isRecording = true
                btnRecord.setImageResource(R.drawable.ic_stop)
                startTime = System.currentTimeMillis()
                startTimer()

                // Monitorear niveles de audio
                monitorAudioLevel()
            } catch (e: IOException) {
                Log.e("AudioRecorder", "prepare() failed", e)
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Guardar metadatos en la base de datos
                val file = File(externalMediaDirs.first(), "${startTime}.mp4")
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
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }

        mediaRecorder = null
        isRecording = false
        btnRecord.setImageResource(R.drawable.ic_mic)
        stopTimer()
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
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

    companion object {
        private const val REQUEST_RECORD_AUDIO = 101
    }
}