package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.StepsRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class MassDataPlanTest {

    @Test
    fun derivesRecordCountsFromHeartRateScale() {
        val plan = MassDataPlan.fromHeartRateCount(
            specs = MassRecordSpecs.default,
            heartRateCount = 360,
            batchSize = 25,
        )

        assertEquals(360, plan.requestedHeartRateCount)
        assertEquals(25, plan.batchSize)
        assertEquals(440, plan.totalRecords)
        assertEquals(360, plan.countFor(HeartRateRecord::class))
        assertEquals(60, plan.countFor(StepsRecord::class))
        assertEquals(12, plan.countFor(DistanceRecord::class))
        assertEquals(6, plan.countFor(ActiveCaloriesBurnedRecord::class))
        assertEquals(2, plan.countFor(HeartRateVariabilityRmssdRecord::class))
    }

    @Test
    fun exposesReadableScaleLabels() {
        val label = MassDataPlan.formatScale(heartRateCount = 2_000_000)

        assertEquals("200万 · 7.7月", label)
    }

    @Test
    fun keepsHeartRateAsTheTenSecondBaseline() {
        val spec = MassRecordSpecs.default.first { it.recordType == HeartRateRecord::class }

        assertEquals(Duration.ofSeconds(10), spec.interval)
        assertEquals(360, spec.countOf(360))
    }
}
