package com.example.alphatabtest

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var resultText: TextView
    private lateinit var renderHost: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val runButton = Button(this).apply {
            text = "Run alphaTab benchmark"
            setOnClickListener {
                runBenchmark()
            }
        }
        val runTransportButton = Button(this).apply {
            text = "Run WebView transport benchmark"
            setOnClickListener {
                runTransportBenchmark()
            }
        }

        resultText = TextView(this).apply {
            textSize = 14f
            text = "Run a benchmark to compare alphaTab renderers or WebView alphaTex transports."
        }

        renderHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val resultScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(resultText)
        }

        root.addView(runButton)
        root.addView(runTransportButton)
        root.addView(resultScroll)
        root.addView(renderHost)
        setContentView(root)
    }

    fun runBenchmarkForTest(
        config: BenchmarkConfig = BenchmarkConfig(bars = 32, measuredRuns = 5, warmupRuns = 1),
        callback: (Result<BenchmarkReport>) -> Unit
    ) {
        lifecycleScope.launch {
            val result = runCatching {
                AlphaTabBenchmarkRunner(this@MainActivity, renderHost).run(config)
            }
            callback(result)
            resultText.text = result.fold(
                onSuccess = { it.toDisplayText() },
                onFailure = { "Benchmark failed: ${it.message}" }
            )
        }
    }

    private fun runBenchmark(config: BenchmarkConfig = BenchmarkConfig()) {
        resultText.text = "Running benchmark..."
        lifecycleScope.launch {
            val text = try {
                AlphaTabBenchmarkRunner(this@MainActivity, renderHost).run(config).toDisplayText()
            } catch (error: Throwable) {
                "Benchmark failed: ${error.message}"
            }
            resultText.text = text
        }
    }

    fun runTransportBenchmarkForTest(
        config: WebViewTransportBenchmarkConfig = WebViewTransportBenchmarkConfig(
            bars = 64,
            measuredRuns = 7,
            warmupRuns = 1
        ),
        callback: (Result<WebViewTransportBenchmarkReport>) -> Unit
    ) {
        lifecycleScope.launch {
            val result = runCatching {
                WebViewTransportBenchmarkRunner(this@MainActivity, renderHost).run(config)
            }
            callback(result)
            resultText.text = result.fold(
                onSuccess = { it.toDisplayText() },
                onFailure = { "Transport benchmark failed: ${it.message}" }
            )
        }
    }

    private fun runTransportBenchmark(
        config: WebViewTransportBenchmarkConfig = WebViewTransportBenchmarkConfig()
    ) {
        resultText.text = "Running WebView transport benchmark..."
        lifecycleScope.launch {
            val text = try {
                WebViewTransportBenchmarkRunner(this@MainActivity, renderHost).run(config).toDisplayText()
            } catch (error: Throwable) {
                "Transport benchmark failed: ${error.message}"
            }
            resultText.text = text
        }
    }
}
