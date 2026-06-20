package com.example.alphatabtest

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebViewTransportBenchmarkRunner(
    private val activity: Activity,
    private val host: FrameLayout
) {
    private val appAssetsOrigin = "https://appassets.androidplatform.net"

    suspend fun run(
        config: WebViewTransportBenchmarkConfig = WebViewTransportBenchmarkConfig()
    ): WebViewTransportBenchmarkReport = withContext(Dispatchers.Main) {
        val baseTex = AlphaTexFactory.createBars(config.bars)
        val publicDir = File(activity.filesDir, "public").apply { mkdirs() }
        val assetTexFile = File(publicDir, "benchmark.alphatex")

        host.removeAllViews()
        val webView = createWebView(publicDir)
        val bridge = TransportBridge()
        webView.addJavascriptInterface(bridge, "BenchmarkBridge")
        host.addView(webView)
        webView.loadDataWithBaseURL(
            "$appAssetsOrigin/bench/index.html",
            benchmarkHtml(),
            "text/html",
            "UTF-8",
            null
        )
        bridge.awaitReady(config.timeoutMs)

        val base64 = runRepeated("Base64", config) { index ->
            val tex = texForRun(baseTex, index)
            val encoded = Base64.encodeToString(tex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            bridge.measure(config.timeoutMs) {
                webView.evaluateJavascript("window.renderFromBase64(${JSONObject.quote(encoded)});", null)
            }
        }

        val assetLoader = runRepeated("WebViewAssetLoader", config) { index ->
            assetTexFile.writeText(texForRun(baseTex, index))
            val url = "$appAssetsOrigin/public/${assetTexFile.name}?run=$index&ts=${SystemClock.elapsedRealtimeNanos()}"
            bridge.measure(config.timeoutMs) {
                webView.evaluateJavascript("window.renderFromAsset(${JSONObject.quote(url)});", null)
            }
        }

        val webMessage = runRepeated("WebMessage API", config) { index ->
            val tex = texForRun(baseTex, index)
            bridge.measure(config.timeoutMs) {
                webView.postWebMessage(WebMessage(tex), Uri.parse(appAssetsOrigin))
            }
        }

        webView.destroy()
        WebViewTransportBenchmarkReport(
            config = config,
            results = listOf(
                WebViewTransportResult("Base64", base64.take(config.warmupRuns), base64.drop(config.warmupRuns)),
                WebViewTransportResult("WebViewAssetLoader", assetLoader.take(config.warmupRuns), assetLoader.drop(config.warmupRuns)),
                WebViewTransportResult("WebMessage API", webMessage.take(config.warmupRuns), webMessage.drop(config.warmupRuns))
            )
        )
    }

    private fun texForRun(baseTex: String, index: Int): String {
        return baseTex.replace(
            "\\subtitle \"Synthetic tablature\"",
            "\\subtitle \"Synthetic tablature run $index\""
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(publicDir: File): WebView {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
            .addPathHandler("/public/", WebViewAssetLoader.InternalStoragePathHandler(activity, publicDir))
            .build()

        return WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    android.util.Log.d(
                        "AlphaTabBenchmark",
                        "transport console ${consoleMessage.messageLevel()}: ${consoleMessage.message()}"
                    )
                    return true
                }
            }
        }
    }

    private fun benchmarkHtml(): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <script src="$appAssetsOrigin/assets/alphaTab.js"></script>
          <style>
            html, body { margin: 0; padding: 0; background: white; }
            #alphaTab { width: 980px; min-height: 1200px; }
          </style>
        </head>
        <body>
          <div id="alphaTab"></div>
          <script>
            const element = document.getElementById('alphaTab');
            const decoder = new TextDecoder('utf-8');
            const api = new alphaTab.AlphaTabApi(element, {
              tex: true,
              core: {
                useWorkers: false,
                enableLazyLoading: false,
                fontDirectory: '$appAssetsOrigin/assets/font/'
              },
              player: {
                enablePlayer: false
              }
            });

            function finishError(error) {
              BenchmarkBridge.error(String(error && error.message ? error.message : error));
            }

            function decodeBase64(value) {
              const binary = atob(value);
              const bytes = new Uint8Array(binary.length);
              for (let i = 0; i < binary.length; i++) {
                bytes[i] = binary.charCodeAt(i);
              }
              return decoder.decode(bytes);
            }

            api.error.on(finishError);
            api.renderFinished.on(() => {
              requestAnimationFrame(() => BenchmarkBridge.done());
            });

            window.renderFromBase64 = function(value) {
              try {
                api.tex(decodeBase64(value));
              } catch (error) {
                finishError(error);
              }
            };

            window.renderFromAsset = async function(url) {
              try {
                const response = await fetch(url, { cache: 'no-store' });
                if (!response.ok) {
                  throw new Error('Asset fetch failed: ' + response.status);
                }
                api.tex(await response.text());
              } catch (error) {
                finishError(error);
              }
            };

            window.addEventListener('message', event => {
              try {
                api.tex(String(event.data));
              } catch (error) {
                finishError(error);
              }
            });

            BenchmarkBridge.ready();
          </script>
        </body>
        </html>
    """.trimIndent()

    private suspend fun runRepeated(
        label: String,
        config: WebViewTransportBenchmarkConfig,
        runOnce: suspend (Int) -> Long
    ): List<Long> {
        delay(150L)
        return List(config.warmupRuns + config.measuredRuns) { index ->
            try {
                runOnce(index)
            } catch (error: TimeoutCancellationException) {
                throw IllegalStateException("$label run ${index + 1} timed out after ${config.timeoutMs}ms", error)
            }
        }
    }

    private class TransportBridge {
        private var readyContinuation: kotlin.coroutines.Continuation<Unit>? = null
        private var renderContinuation: kotlin.coroutines.Continuation<Long>? = null
        private var startedAtMs: Long = 0L

        suspend fun awaitReady(timeoutMs: Long) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    readyContinuation = continuation
                }
            }
        }

        suspend fun measure(timeoutMs: Long, action: () -> Unit): Long {
            return withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    renderContinuation = continuation
                    startedAtMs = SystemClock.elapsedRealtime()
                    action()
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
        fun done() {
            val continuation = renderContinuation ?: return
            renderContinuation = null
            continuation.resume(SystemClock.elapsedRealtime() - startedAtMs)
        }

        @JavascriptInterface
        fun error(message: String) {
            val continuation = renderContinuation ?: return
            renderContinuation = null
            continuation.resumeWithException(IllegalStateException(message))
        }
    }
}
