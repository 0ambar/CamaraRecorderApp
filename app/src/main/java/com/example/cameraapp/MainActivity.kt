package com.example.cameraapp

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.cameraapp.R
import com.example.cameraapp.activities.AudioRecorderActivity
import com.example.cameraapp.activities.CameraActivity
import com.example.cameraapp.activities.GalleryActivity
import com.example.cameraapp.utils.PermissionUtils

class MainActivity : AppCompatActivity() {
    private var isCheckingPermissions = false
    private var permissionDeniedDialog: AlertDialog? = null

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val THEME_IPN = "theme_ipn"
        private const val THEME_ESCOM = "theme_escom"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Obtener preferencias antes de aplicar el tema
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        applySavedTheme(sharedPref)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
        setupThemeSelector(sharedPref)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun applySavedTheme(sharedPref: SharedPreferences) {
        when (sharedPref.getString(KEY_THEME, THEME_IPN)) {
            THEME_ESCOM -> setTheme(R.style.Theme_CameraApp_Blue)
            else -> setTheme(R.style.Theme_CameraApp)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<Button>(R.id.btnAudio).setOnClickListener {
            startActivity(Intent(this, AudioRecorderActivity::class.java))
        }

        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    private fun setupThemeSelector(sharedPref: SharedPreferences) {
        val radioTheme = findViewById<RadioGroup>(R.id.radioTheme)

        // Establecer selección actual
        when (sharedPref.getString(KEY_THEME, THEME_IPN)) {
            THEME_IPN -> radioTheme.check(R.id.radioIPN)
            THEME_ESCOM -> radioTheme.check(R.id.radioESCOM)
        }

        radioTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioIPN -> THEME_IPN
                R.id.radioESCOM -> THEME_ESCOM
                else -> THEME_IPN
            }

            sharedPref.edit { putString(KEY_THEME, theme) }
            restartActivity()
        }
    }

    private fun restartActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!isCheckingPermissions) {
            checkPermissions()
        }
    }

    private fun checkPermissions() {
        isCheckingPermissions = true
        PermissionUtils.checkAllPermissions(this) { allGranted ->
            isCheckingPermissions = false
            if (!allGranted) {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionUtils.onRequestPermissionsResult(requestCode, grantResults, this)
    }

    private fun showPermissionDeniedDialog() {
        permissionDeniedDialog?.dismiss()
        permissionDeniedDialog = AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("Para usar todas las funciones, necesitamos los siguientes permisos:\n\n- Cámara\n- Almacenamiento\n- Micrófono")
            .setPositiveButton("Configuración") { _, _ -> openAppSettings() }
            .setNegativeButton("Continuar", null)
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionDeniedDialog?.dismiss()
        permissionDeniedDialog = null
    }
}