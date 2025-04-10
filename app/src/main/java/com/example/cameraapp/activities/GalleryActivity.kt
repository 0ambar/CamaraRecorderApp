package com.example.cameraapp.activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cameraapp.R
import com.example.cameraapp.adapters.GalleryAdapter
import com.example.cameraapp.databinding.ActivityGalleryBinding
import com.example.cameraapp.models.MediaItem
import android.Manifest
import android.annotation.SuppressLint
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import java.io.File

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: GalleryAdapter
    private val mediaList = mutableListOf<MediaItem>()

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 101
        private const val REQUEST_PERMISSION_SETTINGS = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(mediaList)
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 3)
            adapter = this@GalleryActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun checkAndRequestPermissions() {
        // Para Android 13+ necesitamos permisos diferentes
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        when {
            // Verificar si ya tenemos todos los permisos necesarios
            permissionsToRequest.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            } -> {
                loadMedia()
            }

            // Mostrar explicación si es necesario
            permissionsToRequest.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            } -> {
                showPermissionExplanationDialog(permissionsToRequest)
            }

            // Solicitar permisos directamente
            else -> {
                requestStoragePermission(permissionsToRequest)
            }
        }
    }

    private fun showPermissionExplanationDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios")
            .setMessage("La aplicación necesita acceso a tus archivos multimedia para mostrar las fotos y grabaciones")
            .setPositiveButton("Entendido") { _, _ ->
                requestStoragePermission(permissions)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestStoragePermission(permissions: Array<String>) {
        ActivityCompat.requestPermissions(
            this,
            permissions,
            REQUEST_STORAGE_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    loadMedia()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso requerido")
            .setMessage("Has denegado el permiso. ¿Deseas ir a configuración para activarlo?")
            .setPositiveButton("Configuración") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivityForResult(intent, REQUEST_PERMISSION_SETTINGS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSION_SETTINGS) {
            checkAndRequestPermissions()
        }
    }

    private fun loadMedia() {
        loadImages()
        loadAudios()

        if (mediaList.isEmpty()) {
            Toast.makeText(this, "No se encontraron archivos multimedia", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("Range", "NotifyDataSetChanged")
    private fun loadImages() {
        mediaList.clear() // Limpiar lista antes de cargar

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection,
            projection,
            null, // Sin filtro WHERE
            null, // Sin argumentos para el filtro
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                // Verificar si el archivo existe antes de agregarlo
                if (File(path).exists()) {
                    mediaList.add(MediaItem(path, MediaItem.TYPE_IMAGE))
                }
            }
            adapter.notifyDataSetChanged()

            // Debug: Mostrar cantidad de imágenes encontradas
            Log.d("GalleryActivity", "Imágenes encontradas: ${mediaList.size}")
        } ?: run {
            Log.e("GalleryActivity", "Cursor es nulo")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadAudios() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.DATA} LIKE ?",
            arrayOf("%${Environment.DIRECTORY_MUSIC}/CameraApp%"),
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)

                mediaList.add(MediaItem(path, MediaItem.TYPE_AUDIO))
            }
            adapter.notifyDataSetChanged()
        }
    }
}