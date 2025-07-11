package com.example.androidphp.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.androidphp.bridge.PHPBridge
import com.example.androidphp.bridge.LaravelEnvironment
import com.example.androidphp.databinding.ActivityMainBinding
import com.example.androidphp.network.WebViewManager
import android.webkit.WebView
import androidx.activity.addCallback
import com.example.androidphp.utils.NativeActionCoordinator
import com.example.androidphp.utils.WebViewProvider
import com.example.androidphp.security.LaravelCookieStore
import com.acsbendi.requestinspectorwebview.BuildConfig
import java.io.File
import android.widget.Toast



class MainActivity : AppCompatActivity(), WebViewProvider {
    private lateinit var binding: ActivityMainBinding
    private val phpBridge = PHPBridge(this)
    private lateinit var laravelEnv: LaravelEnvironment<Any?>
    private lateinit var webViewManager: WebViewManager
    private lateinit var coord: NativeActionCoordinator
    private var pendingDeepLink: String? = null


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.splashOverlay.visibility = View.VISIBLE
        LaravelCookieStore.init(applicationContext)
        binding.webView.settings.mediaPlaybackRequiresUserGesture = false

        handleDeepLinkIntent(intent)
        startHotReloadWatcher()
        initializeEnvironmentAsync {
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.splashOverlay.visibility = View.GONE
                }
                .start()

            webViewManager = WebViewManager(this, binding.webView, phpBridge)
            webViewManager.setup()
            coord = NativeActionCoordinator.install(this)

            val target = pendingDeepLink ?: "/"
            val fullUrl = "http://127.0.0.1$target"
            Log.d("DeepLink", "🚀 Loading final URL: $fullUrl")
            binding.webView.loadUrl(fullUrl)

            pendingDeepLink = null
        }

        onBackPressedDispatcher.addCallback(this) {
            val webView = binding.webView

            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

     override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "🌀 Config changed: orientation = ${newConfig.orientation}")
    }

    private fun initializeEnvironmentAsync(onReady: () -> Unit) {
        Thread {
            Log.d("LaravelInit", "📦 Starting async Laravel extraction...")
            laravelEnv = LaravelEnvironment(this)
            laravelEnv.initialize()

            Log.d("LaravelInit", "✅ Laravel environment ready — continuing")

            Handler(Looper.getMainLooper()).post {
                onReady()
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        Log.d("DeepLink", "🌐 Received deep link: $uri")

        val path = uri.path ?: "/"
        val query = uri.query
        val laravelUrl = buildString {
            append(path)
            if (!query.isNullOrBlank()) {
                append("?")
                append(query)
            }
        }

        Log.d("DeepLink", "📦 Saving deep link for later: $laravelUrl")
        pendingDeepLink = laravelUrl
    }


    private fun initializeEnvironment() {
        clearAllCookies()
        laravelEnv = LaravelEnvironment(this)
        laravelEnv.initialize()

    }

    fun clearAllCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        Log.d("CookieInfo", "All cookies cleared")
    }

    override fun onDestroy() {
        super.onDestroy()
        laravelEnv.cleanup()
        phpBridge.shutdown()
    }

    override fun getWebView(): WebView {
        return binding.webView
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("Permission", "✅ Location permission granted")
                // Optionally re-trigger the location fetch
            } else {
                Log.e("Permission", "❌ Location permission denied")
            }
        }
    }

    private fun startHotReloadWatcher() {
        if (isDebugVersion()) {
            // Configure WebView for development
            with(binding.webView.settings) {
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                domStorageEnabled = false
                databaseEnabled = false
            }

            Thread {
                val appStorageDir = File(filesDir.parent, "app_storage")
                val reloadSignalPath = "${appStorageDir.absolutePath}/laravel/storage/framework/reload_signal.json"
                val reloadFile = File(reloadSignalPath)
                var lastModified: Long = 0

                Log.d("HotReload", "🔍 Watching for reload signal at: $reloadSignalPath")

                while (true) {
                    try {
                        if (reloadFile.exists() && reloadFile.lastModified() > lastModified) {
                            lastModified = reloadFile.lastModified()

                            Log.d("HotReload", "🔥 Reload signal detected!")

                            runOnUiThread {
                                // More aggressive cache clearing
                                binding.webView.stopLoading()
                                binding.webView.clearCache(true)
                                binding.webView.clearHistory()
                                binding.webView.clearFormData()

                                // Get current URL and add cache busting
                                val currentUrl = binding.webView.url ?: "http://127.0.0.1/"
                                val separator = if (currentUrl.contains("?")) "&" else "?"
                                val cacheBustUrl = "${currentUrl}${separator}_cb=${System.currentTimeMillis()}"

                                Log.d("HotReload", "🔄 Loading URL with cache bust: $cacheBustUrl")

                                // Small delay then reload with cache busting
                                Handler(Looper.getMainLooper()).postDelayed({
                                    binding.webView.loadUrl(cacheBustUrl)
                                }, 100)

                                Toast.makeText(this@MainActivity, "🔥 Hot reloaded", Toast.LENGTH_SHORT).show()
                            }
                        }

                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        Log.d("HotReload", "Hot reload watcher interrupted")
                        break
                    }
                }
            }.start()
        }
    }

    private fun isDebugVersion(): Boolean {
        return try {
            val appStorageDir = File(filesDir.parent, "app_storage")
            val versionFile = File(appStorageDir, "laravel/.version")

            if (versionFile.exists()) {
                val version = versionFile.readText().trim()
                Log.d("HotReload", "Version file contents: '$version'")
                version == "DEBUG"
            } else {
                Log.d("HotReload", "Version file not found at: ${versionFile.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Log.e("HotReload", "Error reading version file: ${e.message}")
            false
        }
    }
}