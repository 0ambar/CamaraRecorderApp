package com.example.cameraapp.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    enum class PermissionGroup {
        CAMERA,
        AUDIO,
        STORAGE,
        LOCATION
    }

    private val permissionGroups = mapOf(
        PermissionGroup.CAMERA to arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
        PermissionGroup.AUDIO to arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
        PermissionGroup.STORAGE to arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
        PermissionGroup.LOCATION to arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    private var currentRequest: PermissionGroup? = null
    private val pendingRequests = mutableListOf<PermissionGroup>()
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var shouldShowRationale = false

    fun checkAllPermissions(activity: Activity, callback: (Boolean) -> Unit) {
        permissionCallback = callback
        pendingRequests.clear()
        shouldShowRationale = false

        permissionGroups.forEach { (group, permissions) ->
            if (!hasPermissions(activity, permissions)) {
                pendingRequests.add(group)
                // Verificar si debemos mostrar explicación para algún permiso
                if (permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
                    shouldShowRationale = true
                }
            }
        }

        if (pendingRequests.isEmpty()) {
            callback(true)
        } else if (shouldShowRationale) {
            callback(false)
        } else {
            requestNextPermission(activity)
        }
    }

    private fun requestNextPermission(activity: Activity) {
        if (pendingRequests.isEmpty()) {
            permissionCallback?.invoke(true)
            return
        }

        currentRequest = pendingRequests.removeAt(0)
        ActivityCompat.requestPermissions(
            activity,
            permissionGroups[currentRequest]!!,
            currentRequest!!.ordinal
        )
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        activity: Activity
    ): Boolean {
        val requestedGroup = PermissionGroup.entries.getOrNull(requestCode) ?: return false

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            requestNextPermission(activity)
            return true
        }
        return false
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}