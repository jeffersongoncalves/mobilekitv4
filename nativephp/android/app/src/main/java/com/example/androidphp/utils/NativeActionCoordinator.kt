package com.example.androidphp.utils

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.File

interface WebViewProvider {
    fun getWebView(): WebView
}


class NativeActionCoordinator : Fragment() {

    private var pendingCameraUri: Uri? = null

    // Camera launcher
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            Log.d("NativeActionCoordinator", "📸 cameraLauncher callback triggered. Success: $success")

            if (success) {
                val context = requireContext()
                val dst = File(context.cacheDir, "captured.jpg")

                context.contentResolver.openInputStream(pendingCameraUri!!)!!.use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                context.contentResolver.delete(pendingCameraUri!!, null, null)

                val payload = JSONObject().apply {
                    put("path", dst.absolutePath)
                }

                dispatch("Native\\Mobile\\Events\\Camera\\PhotoTaken", payload.toString())

            }else{
                Log.e("NativeActionCoordinator", "❌ Camera capture failed or was canceled")

            }
        }

        fun launchBiometricPrompt() {
            val context = requireContext()
            val activity = requireActivity()

            val biometricManager = BiometricManager.from(context)
            val status = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

            if (status != BiometricManager.BIOMETRIC_SUCCESS) {
                val message = when (status) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                        "This device has no biometric hardware."
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                        "Biometric hardware is currently unavailable."
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                        "No biometric credentials are enrolled."
                    else ->
                        "Biometric authentication is not available."
                }

                NativeActions.showToast(context, message)
                dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
            }

            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Log.d("Biometric", "✅ Auth succeeded")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": true}""")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Log.w("Biometric", "❌ Auth failed")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
                    }

                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        super.onAuthenticationError(code, msg)
                        Log.e("Biometric", "❌ Auth error: $msg")
                        dispatch("Native\\Mobile\\Events\\Biometric\\Completed", """{"success": false}""")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Biometric authentication required")
                .setNegativeButtonText("Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }

    fun launchPushTokenDispatch() {
        try{
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("PushToken", "❌ Failed to fetch token", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result ?: return@addOnCompleteListener
                Log.d("PushToken", "✅ Got FCM token: $token")

                val payload = JSONObject().apply {
                    put("token", token)
                }
                dispatch("Native\\Mobile\\Events\\PushNotification\\TokenGenerated", payload.toString())
            }
        } catch (e: Exception) {
            val context = requireContext()
            Log.e("TOKEN ERROR", "❌ FCM init error: ${e.localizedMessage}")
            NativeActions.showToast(context, "Failed to initialize push notifications.")
        }
    }

    // File picker launcher
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val payload = JSONObject().apply {
                put("uri", uri.toString())
            }
            dispatch("file:chosen", payload.toString())
        }

    fun launchCamera() {
        val context = requireContext()
        val resolver = context.contentResolver

        val photoUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "NativePHP_${System.currentTimeMillis()}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
        ) ?: return

        pendingCameraUri = photoUri
        Log.d("CAMERAFILE", pendingCameraUri.toString());
        cameraLauncher.launch(photoUri)
    }

    fun launchFilePicker(mime: String = "*/*") {
        filePicker.launch(arrayOf(mime))
    }

    private fun dispatch(event: String, payloadJson: String) {
        Log.d("JSFUNC", "native:$event");
        Log.d("JSFUNC", "$payloadJson");
        val eventForJs = event.replace("\\", "\\\\")
        val js = """
            (function () {
                const payload = $payloadJson;
                const detail = { event: "$event", payload };
                document.dispatchEvent(new CustomEvent("native-event", { detail }));
                if (window.Livewire && typeof window.Livewire.dispatch === 'function') {
                    window.Livewire.dispatch("native:$eventForJs", payload);
                }
                fetch('/_native/api/events', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: JSON.stringify({
                        event: "$eventForJs",
                        payload: payload
                    })
                }).then(response => response.json())
                  .then(data => console.log("API Event Dispatch Success:", JSON.stringify(data, null, 2)))
                  .catch(error => console.error("API Event Dispatch Error:", error));
            })();
        """.trimIndent()

        Log.d("NativeActionCoordinator", "📢 Dispatching JS event: $event")

        (activity as? WebViewProvider)?.getWebView()?.evaluateJavascript(js, null)
    }

    companion object {
        fun install(activity: FragmentActivity): NativeActionCoordinator =
            activity.supportFragmentManager.findFragmentByTag("NativeActionCoordinator") as? NativeActionCoordinator
                ?: NativeActionCoordinator().also {
                    activity.supportFragmentManager.beginTransaction()
                        .add(it, "NativeActionCoordinator")
                        .commitNow()
                }
    }
}
