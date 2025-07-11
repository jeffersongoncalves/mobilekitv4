package com.example.androidphp.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import android.hardware.camera2.CameraManager
import com.example.androidphp.bridge.PHPBridge
import com.example.androidphp.network.WebViewManager
import org.json.JSONObject

object NativeActions {
    private const val TAG = "NativeActions"
    private var currentPhotoPath: String? = null
    private var flashlightState = false

    private const val CAMERA_REQUEST_CODE = 9999

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ServiceCast")
    fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null) {
                val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                Log.e(TAG, "❌ Vibrator service is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in vibrate(): ${e.message}", e)
        }
    }

    fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, "✅ Toast displayed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing toast: ${e.message}", e)
            }
        }
    }

    fun showAlert(context: Context, title: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
                Log.d(TAG, "✅ Alert displayed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing alert: ${e.message}", e)
            }
        }
    }

    fun share(context: Context,title: String, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openCamera(activity: Activity, phpBridge: PHPBridge) {

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val imageFile = File(activity.cacheDir, "captured.jpg")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", imageFile)

        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        activity.startActivityForResult(intent, 101)

        // Store this somewhere accessible so you can send it back later
        phpBridge.pendingPhotoPath = imageFile.absolutePath
    }

    fun toggleFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                flashlightState = !flashlightState
                cameraManager.setTorchMode(cameraId, flashlightState)
                Log.d("NativeActions", "🔦 Flashlight toggled ${if (flashlightState) "ON" else "OFF"}")
            } else {
                Log.e("NativeActions", "❌ No flashlight found")
            }
        } catch (e: Exception) {
            Log.e("NativeActions", "❌ Error toggling flashlight", e)
        }
    }
}
