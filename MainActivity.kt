package org.example.privatebrowser

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/**
 * Minimal GeckoView browser:
 *  - every session is a private session (no history, cookies or cache written to disk)
 *  - Firefox WebExtensions are supported, so uBlock Origin, AdGuard, etc. install from addons.mozilla.org
 *  - all site data is wiped on launch and when the app goes away
 */
class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var urlBar: EditText
    private lateinit var session: GeckoSession

    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val runtime = getOrCreateRuntime()
        wipeAllData(runtime)
        installExtensionPrompt(runtime)

        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(true)
                .build()
        ).apply {
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    this@MainActivity.canGoBack = canGoBack
                }
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    if (url != null && !urlBar.hasFocus()) urlBar.setText(url)
                }
            }
            open(runtime)
        }

        geckoView = GeckoView(this).apply { setSession(session) }
        urlBar = EditText(this).apply {
            hint = "Search or enter address"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            // Stop the keyboard from learning what you type.
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    session.loadUri(normalize(text.toString()))
                    clearFocus()
                    true
                } else false
            }
        }
        val menu = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setBackgroundColor(0)
            setOnClickListener { showMenu(it, runtime) }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(urlBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(menu)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(geckoView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        session.loadUri(intent?.dataString ?: "https://duckduckgo.com")
    }

    private fun showMenu(anchor: android.view.View, runtime: GeckoRuntime) {
        PopupMenu(this, anchor).apply {
            menu.add("Install uBlock Origin")
            menu.add("Install extension from URL…")
            menu.add("Clear all data now")
            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Install uBlock Origin" -> installExtension(runtime, UBLOCK_URL)
                    "Install extension from URL…" -> promptForExtensionUrl(runtime)
                    "Clear all data now" -> wipeAllData(runtime)
                }
                true
            }
            show()
        }
    }

    private fun promptForExtensionUrl(runtime: GeckoRuntime) {
        val input = EditText(this).apply { hint = "https://addons.mozilla.org/…/addon.xpi" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Extension .xpi URL")
            .setView(input)
            .setPositiveButton("Install") { _, _ ->
                installExtension(runtime, input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installExtension(runtime: GeckoRuntime, url: String) {
        runtime.webExtensionController.install(url).accept(
            { ext: WebExtension? ->
                if (ext != null) {
                    // Extensions are OFF in private sessions unless explicitly allowed.
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                    toast("Installed ${ext.metaData.name}")
                }
            },
            { err -> toast("Install failed: ${err?.message}") }
        )
    }

    private fun installExtensionPrompt(runtime: GeckoRuntime) {
        // Auto-accept the permission prompt for installs the user explicitly started.
        // For a public release, show a real dialog listing the requested permissions here.
        runtime.webExtensionController.promptDelegate =
            object : WebExtensionController.PromptDelegate {
                override fun onInstallPrompt(
                    extension: WebExtension
                ): GeckoResult<WebExtension.PermissionPromptResponse>? =
                    GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true))
            }
    }

    private fun wipeAllData(runtime: GeckoRuntime) {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
    }

    private fun normalize(raw: String): String {
        val s = raw.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(".") && !s.contains(" ") -> "https://$s"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(s, "UTF-8")
        }
    }

    private fun toast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (canGoBack) session.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        // Best-effort wipe on exit; data is also wiped on next launch.
        runtime?.let { wipeAllData(it) }
        super.onDestroy()
    }

    // ---- runtime setup -----------------------------------------------------

    private fun getOrCreateRuntime(): GeckoRuntime {
        runtime?.let { return it }

        // Gecko prefs that stop anything being persisted, even outside private mode.
        val cfg = File(filesDir, "gecko-config.yaml")
        cfg.writeText(
            """
            prefs:
              browser.cache.disk.enable: false
              browser.cache.offline.enable: false
              browser.sessionstore.max_tabs_undo: 0
              browser.sessionstore.resume_from_crash: false
              places.history.enabled: false
              signon.rememberSignons: false
              browser.formfill.enable: false
              network.cookie.lifetimePolicy: 2
              privacy.sanitize.sanitizeOnShutdown: true
              privacy.clearOnShutdown.cache: true
              privacy.clearOnShutdown.cookies: true
              privacy.clearOnShutdown.history: true
              privacy.clearOnShutdown.offlineApps: true
              privacy.clearOnShutdown.sessions: true
              datareporting.healthreport.uploadEnabled: false
              toolkit.telemetry.enabled: false
              browser.safebrowsing.downloads.remote.enabled: false
            """.trimIndent()
        )

        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(cfg.absolutePath)
            .remoteDebuggingEnabled(false)
            .crashHandler(null)
            .build()

        return GeckoRuntime.create(this, settings).also { runtime = it }
    }

    companion object {
        private const val UBLOCK_URL =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        @Volatile private var runtime: GeckoRuntime? = null
    }
}
