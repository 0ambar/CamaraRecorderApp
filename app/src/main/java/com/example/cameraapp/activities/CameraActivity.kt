package com.example.cameraapp.activities

import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.HandlerThread
import android.view.TextureView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.cameraapp.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraMetadata
import android.icu.text.SimpleDateFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.example.cameraapp.database.AppDatabase
import com.example.cameraapp.database.entities.PhotoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class CameraActivity : AppCompatActivity() {
    private lateinit var textureView: TextureView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var isFlashOn = false
    private var facingFront = false // Bandera para rastrear qué cámara está activa
    private var cameraIdList: Array<String> = emptyArray() // Lista de cámaras disponibles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        textureView = findViewById(R.id.textureView)
        btnCapture = findViewById(R.id.btnCapture)
        btnFlash = findViewById(R.id.btnFlash)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        btnCapture.setOnClickListener { takePicture() }
        btnFlash.setOnClickListener { toggleFlash() }
        btnSwitchCamera.setOnClickListener { switchCamera() }

        textureView.surfaceTextureListener = surfaceTextureListener
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun openCamera() {
        try {
            cameraIdList = cameraManager.cameraIdList

            // Si no hay cámaras disponibles, mostrar error
            if (cameraIdList.isEmpty()) {
                Toast.makeText(this, "No se encontraron cámaras", Toast.LENGTH_SHORT).show()
                return
            }

            // Determinar qué cámara usar basado en el estado actual
            cameraId = if (facingFront) {
                cameraIdList.find { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                } ?: cameraIdList[0] // Si no encuentra frontal, usa la primera disponible
            } else {
                cameraIdList.find { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraIdList[0] // Si no encuentra trasera, usa la primera disponible
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId!!, stateCallback, null)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Error al acceder a la cámara", Toast.LENGTH_SHORT).show()
        }
    }


    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }


    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture?.apply {
                setDefaultBufferSize(textureView.width, textureView.height)
            }
            val surface = Surface(texture)

            previewRequestBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            ).apply {
                this?.addTarget(surface)
                this?.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )

                // Configurar orientación basada en la cámara frontal/trasera
                if (facingFront) {
                    this?.set(CaptureRequest.JPEG_ORIENTATION, 270) // Rotación para cámara frontal
                } else {
                    this?.set(CaptureRequest.JPEG_ORIENTATION, 90) // Rotación para cámara trasera
                }

                if (isFlashOn) {
                    this?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
            }

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            previewRequestBuilder?.build()?.let { request ->
                                session.setRepeatingRequest(
                                    request,
                                    null,
                                    backgroundHandler
                                )
                            }
                        } catch (e: CameraAccessException) {
                            Log.e("Camera", "Error al configurar vista previa: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            this@CameraActivity,
                            "Error al configurar cámara",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e("Camera", "Error de acceso a cámara: ${e.message}")
        }
    }

    private fun updatePreview() {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        backgroundHandler?.post {
            try {
                previewRequestBuilder?.build()
                    ?.let { cameraCaptureSession?.setRepeatingRequest(it, null, null) }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun takePicture() {
        if (cameraDevice == null || !textureView.isAvailable) {
            Toast.makeText(this, "Cámara no lista", Toast.LENGTH_SHORT).show()
            return
        }

        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/CameraApp/Photos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        try {
            // 1. Crear archivo para la fotos
            val file = File(
                    storageDir,
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
            )


            // 2. Configurar ImageReader
            val imageReader = ImageReader.newInstance(
                textureView.width,
                textureView.height,
                ImageFormat.JPEG,
                1
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // 4. Guardar imagen
                            saveImageToFile(image, file)

                            // 5. Escanear el archivo para que aparezca en la galería
                            MediaScannerConnection.scanFile(
                                this@CameraActivity,
                                arrayOf(file.absolutePath),
                                arrayOf("image/jpeg"),
                                null
                            )

                            runOnUiThread {
                                Toast.makeText(
                                    this@CameraActivity,
                                    "Foto guardada en Galería",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } finally {
                            image.close()
                            restartPreview()
                        }
                    }
                }, backgroundHandler)
            }

            // 3. Crear sesión de captura temporal
            cameraDevice?.createCaptureSession(
                listOf(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            // 4. Configurar y ejecutar captura
                            val captureBuilder = cameraDevice?.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                this?.addTarget(imageReader.surface)
                                this?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                this?.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                if (isFlashOn) {
                                    this?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                                }
                            }

                            captureBuilder?.build()?.let { request ->
                                session.capture(request, null, backgroundHandler)
                            }
                        } catch (e: CameraAccessException) {
                            Log.e("Camera", "Error al capturar: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("Camera", "Configuración de captura fallida")
                    }
                },
                backgroundHandler
            )

        } catch (e: Exception) {
            Log.e("Camera", "Error al tomar foto: ${e.message}")
            Toast.makeText(this, "Error al capturar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartPreview() {
        try {
            // Recrear la sesión de vista previa
            createCameraPreviewSession()
        } catch (e: Exception) {
            Log.e("Camera", "Error al reiniciar vista previa: ${e.message}")
        }
    }
    @SuppressLint("SimpleDateFormat")
    private fun saveImageToFile(image: Image, file: File) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            FileOutputStream(file).use { stream ->
                stream.write(bytes)
                stream.flush()

                // Guardar metadatos en Room
                val metadata = PhotoMetadata(
                    id = file.name.hashCode(),
                    filePath = file.absolutePath,
                    dateTaken = System.currentTimeMillis(),
                    flashUsed = isFlashOn
                )

                GlobalScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@CameraActivity)
                        .mediaDao()
                        .insertPhoto(metadata)
                }
            }
        } catch (e: Exception) {
            Log.e("Camera", "Error al guardar imagen: ${e.message}")
            throw e
        }
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        btnFlash.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun switchCamera() {
        // Cerrar la cámara actual antes de cambiar
        cameraCaptureSession?.close()
        cameraCaptureSession = null

        cameraDevice?.close()
        cameraDevice = null

        // Alternar entre frontal/trasera
        facingFront = !facingFront

        // Reabrir la cámara con la nueva configuración
        openCamera()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
    }
}