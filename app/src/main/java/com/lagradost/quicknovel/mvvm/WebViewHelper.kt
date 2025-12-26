package com.lagradost.quicknovel.utils

import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SessionCookieProvider {
    private const val LOGIN_URL = "https://m.webnovel.com"

    suspend fun getValidCookie(context: Context): String = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            var isResumed = false

            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isResumed) return
                    //isResumed = true

                    val cookies = cookieManager.getCookie(LOGIN_URL)
                    if (cookies.contains("_csrfToken")) {
                        isResumed = true
                        cont.resume(cookies)
                        webView.destroy()
                    } else {
                        view?.postDelayed({ onPageFinished(view, url) }, 1000)
                    }
                }
            }

            webView.loadUrl(LOGIN_URL)
        }
    }
}

object WebViewHelper {
    fun configureWebView(webView: WebView) {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        settings.userAgentString =
            WebSettings.getDefaultUserAgent(webView.context)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }
}


class CloudflareWebViewLoader(
    context: Context
) {

    companion object {
        private const val TAG = "CFWebViewLoader"
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }

    private val webView: WebView = WebView(context).apply {
        WebViewHelper.configureWebView(this)
        visibility = View.GONE
    }

    /**
     * Loads a Cloudflare-protected page and extracts HTML from the DOM.
     *
     * @param url The page URL
     * @param selector CSS selector to extract (e.g. "div#chapter")
     * @param timeoutMs Optional timeout
     */
    suspend fun load(
        url: String,
        selector: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): String? = withTimeoutOrNull(timeoutMs) {

        suspendCancellableCoroutine { cont ->

            Log.d(TAG, "Loading URL: $url")
            Log.d(TAG, "Using selector: $selector")

            webView.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    Log.d(TAG, "onPageFinished: $finishedUrl")

                    val js = """
                        (function() {
                            const el = document.querySelector("$selector");
                            if (!el) return null;

                            el.querySelectorAll(
                                "script, iframe, ins, noscript"
                            ).forEach(e => e.remove());

                            return el.innerHTML;
                        })();
                    """.trimIndent()

                    view.evaluateJavascript(js) { result ->

                        Log.d(TAG, "evaluateJavascript returned")

                        if (!cont.isActive) {
                            Log.w(TAG, "Coroutine cancelled before JS result")
                            return@evaluateJavascript
                        }

                        if (result == null || result == "null") {
                            Log.e(TAG, "DOM element not found: $selector")
                            cont.resume(null)
                            return@evaluateJavascript
                        }

                        val decoded = decodeJsString(result)

                        Log.d(
                            TAG,
                            "Extracted HTML length=${decoded.length}"
                        )

                        cont.resume(decoded)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    Log.e(TAG, "WebView error: ${error}")

                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            webView.loadUrl(url)

            cont.invokeOnCancellation {
                Log.w(TAG, "Coroutine cancelled, stopping WebView")
                webView.stopLoading()
            }
        }
    }

    /**
     * Decodes JSON-escaped JS strings returned by evaluateJavascript()
     */
    private fun decodeJsString(raw: String): String {
        return JSONObject("""{"v":$raw}""").getString("v")
    }
}




