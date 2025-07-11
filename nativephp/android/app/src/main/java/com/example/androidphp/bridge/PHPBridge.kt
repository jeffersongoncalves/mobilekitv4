package com.example.androidphp.bridge

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import android.Manifest
import androidx.core.content.ContextCompat
import com.example.androidphp.network.PHPRequest
import com.example.androidphp.security.LaravelCookieStore
import com.example.androidphp.utils.NativeActions
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import com.example.androidphp.utils.NativeActionCoordinator
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PHPBridge(private val context: Context) {
    private var lastPostData: String? = null
    private val requestDataMap = ConcurrentHashMap<String, String>()
    private val phpExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    var pendingPhotoPath: String? = null

    private val nativePhpScript: String
        get() = "${getLaravelPath()}/vendor/nativephp/mobile/bootstrap/android/native.php"

    external fun nativeExecuteScript(filename: String): String
    external fun nativeSetEnv(name: String, value: String, overwrite: Int): Int
    external fun runArtisanCommand(command: String): String
    external fun initialize()
    external fun setRequestInfo(method: String, uri: String, postData: String?)
    external fun getLaravelPublicPath(): String
    external fun getLaravelRootPath(): String
    external fun shutdown()
    external fun nativeHandleRequestOnce(
        method: String,
        uri: String,
        postData: String?,
        scriptPath: String
    ): String


    companion object {
        private const val TAG = "PHPBridge"
        private const val MAX_REQUEST_AGE = 5 * 60 * 1000L

        init {
            System.loadLibrary("compat")
            System.loadLibrary("php")
            System.loadLibrary("php_wrapper")
        }
    }

    fun handleLaravelRequest(request: PHPRequest): String {
        val future = phpExecutor.submit<String> {
            request.headers.forEach { (key, value) ->
                val envKey = "HTTP_" + key.replace("-", "_").uppercase()
                nativeSetEnv(envKey, value, 1)
            }

            val cookieHeader = LaravelCookieStore.asCookieHeader()
            nativeSetEnv("HTTP_COOKIE", cookieHeader, 1)

            Log.d(TAG, "🍪 Sent HTTP_COOKIE to native: $cookieHeader")

            initialize()

            val output = nativeHandleRequestOnce(
                request.method,
                request.uri,
                request.body,
                nativePhpScript
            )

            val processedOutput = processRawPHPResponse(output)
            processedOutput
        }

        return future.get()
    }

    // New function to store request data with a key
    fun storeRequestData(key: String, data: String) {
        requestDataMap[key] = data
        Log.d(TAG, "🔑 Stored request data with key: $key (length=${data.length})")

        // Also update last post data for backward compatibility
        lastPostData = data

        // Clean up old requests occasionally
        if (requestDataMap.size > 10) {
            cleanupOldRequests()
        }
    }

    // Clean up old request data
    private fun cleanupOldRequests() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()

        // Find keys with timestamps older than MAX_REQUEST_AGE
        requestDataMap.keys.forEach { key ->
            if (key.contains("-")) {
                val timestampStr = key.substringAfterLast("-")
                try {
                    val timestamp = timestampStr.toLong()
                    if (now - timestamp > MAX_REQUEST_AGE) {
                        keysToRemove.add(key)
                    }
                } catch (e: NumberFormatException) {
                    // Key doesn't have a valid timestamp format, ignore
                }
            }
        }

        // Remove old entries
        keysToRemove.forEach { requestDataMap.remove(it) }
        if (keysToRemove.isNotEmpty()) {
            Log.d(TAG, "🧹 Cleaned up ${keysToRemove.size} old request entries")
        }
    }

    fun getLastPostData(): String? {
        return lastPostData
    }

    fun getLaravelPath(): String {
        val storageDir = context.getDir("storage", Context.MODE_PRIVATE)
        return "${storageDir.absolutePath}/laravel"
    }

    fun processRawPHPResponse(response: String): String {
        // Log the first 200 characters to understand the response format
        Log.d(TAG, "🔍 Response first 200 chars: ${response.take(200)}")

        // Check for Set-Cookie headers regardless of response format
        if (response.contains("Set-Cookie:", ignoreCase = true)) {
            Log.d(TAG, "🍪 Found Set-Cookie in raw response!")

            // Extract all Set-Cookie lines
            val setCookieLines = response.split("\r\n")
                .filter { it.startsWith("Set-Cookie:", ignoreCase = true) }

            setCookieLines.forEach { cookieLine ->
                Log.d(TAG, "🍪 Cookie line: $cookieLine")

                // Extract the cookie value (after "Set-Cookie:")
                val cookieValue = cookieLine.substringAfter(":", "").trim()
                if (cookieValue.isNotEmpty()) {
                    // Manually set this cookie
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setCookie("http://127.0.0.1", cookieValue)
                    Log.d(TAG, "🍪 Manually set cookie: $cookieValue")
                }
            }

            // Make sure to flush the cookies
            CookieManager.getInstance().flush()
            Log.d(TAG, "🍪 Flushed cookies after extraction")
        } else {
            Log.d(TAG, "⚠️ No Set-Cookie headers found in the response")
        }

        // Continue with your existing logic for different response types
        if (response.trim().startsWith("{") && response.trim().endsWith("}")) {
            try {
                val json = JSONObject(response)
                if (json.has("message") && json.getString("message")
                        .contains("CSRF token mismatch")
                ) {
                    Log.e(TAG, "CSRF token mismatch detected. Adding 419 status.")
                    return "HTTP/1.1 419 Page Expired\r\n" +
                            "Content-Type: application/json\r\n" +
                            "X-CSRF-Error: true\r\n" +
                            "\r\n" +
                            response
                }

                // Regular JSON response
                return "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "\r\n" +
                        response
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON response", e)
            }
        }

        // If it already has headers (check for common header fields)
        if (response.contains("Content-Type:", ignoreCase = true) ||
            response.contains("Set-Cookie:", ignoreCase = true)
        ) {

            // It has some headers, but might not have the status line
            // Add a status line if it doesn't have one
            if (!response.startsWith("HTTP/")) {
                return "HTTP/1.1 200 OK\r\n" + response
            }
            return response
        }

        // Default case: assume it's just content without headers
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "\r\n" +
                response
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun nativeVibrate() {
        NativeActions.vibrate(context)
    }

    fun nativeShowToast(message: String) {
        NativeActions.showToast(context, message)
    }

    fun nativeShowAlert(title: String, message: String) {
        NativeActions.showAlert(context, title, message)
    }

    fun nativeShare(title: String, message: String) {
        NativeActions.share(context, title, message)
    }

    fun nativeToggleFlashlight() {
        NativeActions.toggleFlashlight(context)
    }

    fun nativeOpenCamera() {

        // Launch camera on UI thread without blocking
        Handler(Looper.getMainLooper()).post {
            val activity = context as? Activity
            if (activity == null) {
                Log.e("PHPBridge", "❌ Context is not an Activity!")
                return@post
            }

            val permissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                val activity = context as? FragmentActivity ?: return@post
                val coord = NativeActionCoordinator.install(activity)
                coord.launchCamera()
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1001)
            }
        }
    }

    fun nativeStartBiometric() {
            Handler(Looper.getMainLooper()).post {
                NativeActionCoordinator.install(context as FragmentActivity)
                    .launchBiometricPrompt()
            }
        }

    fun nativeGetPushToken() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1002 // use your own request code
                )
                return // wait for result before continuing
            }
        }

        Handler(Looper.getMainLooper()).post {
            NativeActionCoordinator.install(context as FragmentActivity).launchPushTokenDispatch()
        }
    }

    // Secure storage implementation
    private val securePrefs: EncryptedSharedPreferences by lazy {
        try {
            // Create or retrieve the master key for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // Create encrypted shared preferences
            EncryptedSharedPreferences.create(
                context,
                "nativephp_secure_storage",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences", e)
            throw e
        }
    }

    /**
     * Securely store a value using Android Keystore encryption
     * @param key The key to store the value under
     * @param value The value to store securely
     * @return true if successfully stored, false otherwise
     */
    fun nativeSecureSet(key: String, value: String): Boolean {
        return try {
            Log.d(TAG, "🔐 Storing secure value for key: $key")
            securePrefs.edit().putString(key, value).apply()
            Log.d(TAG, "✅ Successfully stored secure value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to store secure value", e)
            false
        }
    }

    /**
     * Retrieve a securely stored value
     * @param key The key to retrieve the value for
     * @return The stored value or null if not found
     */
    fun nativeSecureGet(key: String): String? {
        return try {
            Log.d(TAG, "🔓 Retrieving secure value for key: $key")
            val value = securePrefs.getString(key, null)
            if (value != null) {
                Log.d(TAG, "✅ Successfully retrieved secure value")
                Log.d(TAG, "🔍 Retrieved value: $value")
                Log.d(TAG, "🔍 Value length: ${value.length}")
            } else {
                Log.d(TAG, "⚠️ No value found for key: $key")
            }
            value
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to retrieve secure value", e)
            null
        }
    }
}