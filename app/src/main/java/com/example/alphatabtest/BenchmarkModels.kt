package com.example.alphatabtest

data class BenchmarkConfig(
    val bars: Int = 64,
    val measuredRuns: Int = 7,
    val warmupRuns: Int = 1,
    val timeoutMs: Long = 20_000L
)

data class RendererBenchmarkResult(
    val renderer: String,
    val warmupMs: List<Long>,
    val measuredMs: List<Long>
) {
    val medianMs: Long = measuredMs.sorted().let { values ->
        if (values.isEmpty()) 0L else values[values.size / 2]
    }
    val minMs: Long = measuredMs.minOrNull() ?: 0L
    val maxMs: Long = measuredMs.maxOrNull() ?: 0L
}

data class BenchmarkReport(
    val config: BenchmarkConfig,
    val webView: RendererBenchmarkResult,
    val native: RendererBenchmarkResult
) {
    val winner: String = when {
        webView.medianMs == 0L || native.medianMs == 0L -> "inconclusive"
        webView.medianMs < native.medianMs -> webView.renderer
        native.medianMs < webView.medianMs -> native.renderer
        else -> "tie"
    }

    fun toDisplayText(): String = buildString {
        appendLine("alphaTab render benchmark")
        appendLine("Bars: ${config.bars}, measured runs: ${config.measuredRuns}, warmup: ${config.warmupRuns}")
        appendLine()
        appendLine(format(webView))
        appendLine(format(native))
        appendLine("Winner by median: $winner")
    }

    private fun format(result: RendererBenchmarkResult): String {
        return "${result.renderer}: median=${result.medianMs}ms, min=${result.minMs}ms, max=${result.maxMs}ms, runs=${result.measuredMs}"
    }
}
