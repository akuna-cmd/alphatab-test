package com.example.alphatabtest

data class WebViewTransportBenchmarkConfig(
    val bars: Int = 64,
    val measuredRuns: Int = 7,
    val warmupRuns: Int = 1,
    val timeoutMs: Long = 60_000L
)

data class WebViewTransportResult(
    val transport: String,
    val warmupMs: List<Long>,
    val measuredMs: List<Long>
) {
    val medianMs: Long = measuredMs.sorted().let { values ->
        if (values.isEmpty()) 0L else values[values.size / 2]
    }
    val minMs: Long = measuredMs.minOrNull() ?: 0L
    val maxMs: Long = measuredMs.maxOrNull() ?: 0L
}

data class WebViewTransportBenchmarkReport(
    val config: WebViewTransportBenchmarkConfig,
    val results: List<WebViewTransportResult>
) {
    val fastest: WebViewTransportResult? = results.minByOrNull { it.medianMs }

    fun toDisplayText(): String = buildString {
        appendLine("WebView alphaTex transport benchmark")
        appendLine("Bars: ${config.bars}, measured runs: ${config.measuredRuns}, warmup: ${config.warmupRuns}")
        appendLine()
        results.forEach { result ->
            appendLine("${result.transport}: median=${result.medianMs}ms, min=${result.minMs}ms, max=${result.maxMs}ms, runs=${result.measuredMs}")
        }
        appendLine("Fastest by median: ${fastest?.transport ?: "inconclusive"}")
    }
}
