package com.example.alphatabtest

object AlphaTexFactory {
    fun createBars(barCount: Int): String {
        val motif = listOf(
            ":4 0.6 1.6 3.6 0.5",
            "2.5 3.5 0.4 2.4",
            "3.4 0.3 2.3 0.2",
            "1.2 3.2 0.1 1.1"
        )
        return buildString {
            appendLine("\\title \"alphaTab benchmark\"")
            appendLine("\\subtitle \"Synthetic tablature\"")
            appendLine(".")
            repeat(barCount) { index ->
                append(motif[index % motif.size])
                append(" | ")
                if ((index + 1) % 4 == 0) {
                    appendLine()
                }
            }
        }
    }
}
