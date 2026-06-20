package com.example.alphatabtest

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.Settings
import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.contracts.ExperimentalContracts

class AlphaTabBenchmarkRunner(
    private val activity: Activity,
    private val host: FrameLayout
) {
    suspend fun run(config: BenchmarkConfig = BenchmarkConfig()): BenchmarkReport = withContext(Dispatchers.Main) {
        val tex = AlphaTexFactory.createBars(config.bars)
        host.removeAllViews()

        val webView = runWebViewBenchmark(tex, config)
        host.removeAllViews()
        delay(250L)

        val native = runNativeBenchmark(tex, config)
        BenchmarkReport(config, webView, native)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun runWebViewBenchmark(
        tex: String,
        config: BenchmarkConfig
    ): RendererBenchmarkResult {
        val webView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }

        val bridge = WebBenchmarkBridge()
        webView.addJavascriptInterface(bridge, "BenchmarkBridge")
        host.addView(webView)

        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <script src="alphaTab.js"></script>
              <style>
                html, body { margin: 0; padding: 0; background: white; }
                #alphaTab { width: 980px; min-height: 1200px; }
              </style>
            </head>
            <body>
              <div id="alphaTab"></div>
              <script>
                const element = document.getElementById('alphaTab');
                const api = new alphaTab.AlphaTabApi(element, {
                  tex: true,
                  core: {
                    useWorkers: false,
                    enableLazyLoading: false,
                    fontDirectory: 'file:///android_asset/font/'
                  },
                  player: {
                    enablePlayer: false
                  }
                });
                let startedAt = 0;
                api.error.on(e => BenchmarkBridge.error(String(e)));
                api.postRenderFinished.on(() => {
                  requestAnimationFrame(() => {
                    BenchmarkBridge.done(Math.round(performance.now() - startedAt));
                  });
                });
                window.renderAlphaTex = function(tex) {
                  startedAt = performance.now();
                  api.tex(tex);
                };
                BenchmarkBridge.ready();
              </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        bridge.awaitReady(config.timeoutMs)

        val allRuns = runRepeated("WebView + JS", config) {
            bridge.renderAndAwait(webView, tex, config.timeoutMs)
        }
        webView.destroy()
        return RendererBenchmarkResult("WebView + JS", allRuns.take(config.warmupRuns), allRuns.drop(config.warmupRuns))
    }

    @OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
    private suspend fun runNativeBenchmark(
        tex: String,
        config: BenchmarkConfig
    ): RendererBenchmarkResult {
        val alphaTabView = AlphaTabView(activity, null).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings = Settings().apply {
                core.useWorkers = false
                core.enableLazyLoading = false
                player.playerMode = PlayerMode.Disabled
            }
        }

        host.addView(alphaTabView)
        delay(250L)

        val allRuns = runRepeated("Native AlphaTabView", config) {
            suspendCancellableCoroutine { continuation ->
                val startedAt = SystemClock.elapsedRealtime()
                var unregister: (() -> Unit)? = null
                unregister = alphaTabView.api.postRenderFinished.on {
                    unregister?.invoke()
                    if (continuation.isActive) {
                        continuation.resume(SystemClock.elapsedRealtime() - startedAt)
                    }
                }
                continuation.invokeOnCancellation {
                    unregister?.invoke()
                }
                alphaTabView.api.tex(tex)
            }
        }
        host.removeView(alphaTabView)
        return RendererBenchmarkResult("Native AlphaTabView", allRuns.take(config.warmupRuns), allRuns.drop(config.warmupRuns))
    }

    private suspend fun runRepeated(
        label: String,
        config: BenchmarkConfig,
        runOnce: suspend () -> Long
    ): List<Long> {
        return List(config.warmupRuns + config.measuredRuns) { index ->
            try {
                withTimeout(config.timeoutMs) {
                    runOnce()
                }
            } catch (error: TimeoutCancellationException) {
                throw IllegalStateException("$label run ${index + 1} timed out after ${config.timeoutMs}ms", error)
            }
        }
    }

    private class WebBenchmarkBridge {
        private var readyContinuation: (kotlin.coroutines.Continuation<Unit>)? = null
        private var renderContinuation: (kotlin.coroutines.Continuation<Long>)? = null

        suspend fun awaitReady(timeoutMs: Long) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    readyContinuation = continuation
                }
            }
        }

        suspend fun renderAndAwait(webView: WebView, tex: String, timeoutMs: Long): Long {
            return withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    renderContinuation = continuation
                    webView.evaluateJavascript("window.renderAlphaTex(${JSONObject.quote(tex)});", null)
                }
            }
        }

        @JavascriptInterface
        fun ready() {
            val continuation = readyContinuation ?: return
            readyContinuation = null
            continuation.resume(Unit)
        }

        @JavascriptInterface
        fun done(milliseconds: Long) {
            val continuation = renderContinuation ?: return
            renderContinuation = null
            continuation.resume(milliseconds)
        }

        @JavascriptInterface
        fun error(message: String) {
            val continuation = renderContinuation ?: return
            renderContinuation = null
            continuation.resumeWithException(IllegalStateException(message))
        }
    }
}
