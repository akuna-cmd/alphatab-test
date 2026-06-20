package com.example.alphatabtest

import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AlphaTabBenchmarkInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun compareWebViewAndNativeAlphaTabRendering() {
        val latch = CountDownLatch(1)
        val reportRef = AtomicReference<Result<BenchmarkReport>>()

        activityRule.scenario.onActivity { activity ->
            activity.runBenchmarkForTest(
                BenchmarkConfig(bars = 16, measuredRuns = 5, warmupRuns = 1, timeoutMs = 60_000L)
            ) { result ->
                reportRef.set(result)
                latch.countDown()
            }
        }

        assertTrue("Benchmark did not finish in time", latch.await(180, TimeUnit.SECONDS))
        val report = reportRef.get().getOrThrow()
        Log.i("AlphaTabBenchmark", "ALPHATAB_BENCHMARK_RESULT ${report.toDisplayText().replace('\n', ' ')}")
        assertTrue(report.webView.measuredMs.all { it > 0L })
        assertTrue(report.native.measuredMs.all { it > 0L })
    }
}
