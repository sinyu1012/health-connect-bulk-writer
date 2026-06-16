package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MassRecordFactoryTest {

    private val anchor = Instant.ofEpochMilli(1_746_800_000_000)
    private val factory = MassRecordFactory()

    @Test
    fun createsHeartRateRecordsEndingAtAnchorAndWalkingBackwards() {
        val spec = MassRecordSpecs.default.first { it.recordType == HeartRateRecord::class }
        val records = factory.createRecords(spec = spec, startIndex = 0, count = 3, anchor = anchor)
            .map { it as HeartRateRecord }

        assertEquals(anchor.minusSeconds(10), records[0].startTime)
        assertEquals(anchor, records[0].endTime)
        assertEquals(anchor.minusSeconds(5), records[0].samples.single().time)
        assertEquals(60, records[0].samples.single().beatsPerMinute)

        assertEquals(anchor.minusSeconds(20), records[1].startTime)
        assertEquals(anchor.minusSeconds(10), records[1].endTime)
        assertEquals(61, records[1].samples.single().beatsPerMinute)

        assertEquals(anchor.minusSeconds(30), records[2].startTime)
        assertEquals(anchor.minusSeconds(20), records[2].endTime)
        assertEquals(62, records[2].samples.single().beatsPerMinute)
    }

    @Test
    fun createsStepRecordsWithMinuteIntervalsAndSyntheticCounts() {
        val spec = MassRecordSpecs.default.first { it.recordType == StepsRecord::class }
        val record = factory.createRecords(spec = spec, startIndex = 7, count = 1, anchor = anchor)
            .single() as StepsRecord

        assertEquals(anchor.minusSeconds(480), record.startTime)
        assertEquals(anchor.minusSeconds(420), record.endTime)
        assertEquals(27, record.count)
    }

    @Test
    fun allSyntheticSpecsCoverEveryHealthConnectRecordType() {
        val actual = MassRecordSpecs.allSynthetic.mapTo(linkedSetOf()) { it.recordType }

        assertEquals(HealthConnectCatalog.recordTypes, actual)
    }

    @Test
    fun createsOneRecordForEverySyntheticSpec() {
        for (spec in MassRecordSpecs.allSynthetic) {
            val record = factory.createRecords(spec = spec, startIndex = 0, count = 1, anchor = anchor)
                .single()

            assertTrue("${spec.label} should create ${spec.recordType.simpleName}", spec.recordType.isInstance(record))
        }
    }
}
