package com.example.alphatabtest

import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebViewTransportBenchmarkInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun compareWebViewAlphaTexTransports() {
        val latch = CountDownLatch(1)
        val reportRef = AtomicReference<Result<WebViewTransportBenchmarkReport>>()

        activityRule.scenario.onActivity { activity ->
            activity.runTransportBenchmarkForTest(
                WebViewTransportBenchmarkConfig(bars = 64, measuredRuns = 7, warmupRuns = 1)
            ) { result ->
                reportRef.set(result)
                latch.countDown()
            }
        }

        assertTrue("Transport benchmark did not finish in time", latch.await(240, TimeUnit.SECONDS))
        val report = reportRef.get().getOrThrow()
        Log.i(
            "AlphaTabBenchmark",
            "WEBVIEW_TRANSPORT_BENCHMARK_RESULT ${report.toDisplayText().replace('\n', ' ')}"
        )
        assertEquals(3, report.results.size)
        assertTrue(report.results.all { result -> result.measuredMs.size == 7 })
        assertTrue(report.results.all { result -> result.measuredMs.all { it > 0L } })
    }
}
