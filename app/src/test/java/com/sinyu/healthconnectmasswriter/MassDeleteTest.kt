package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

class MassDeleteTest {

    @Test
    fun deletesEverySupportedRecordTypeAcrossDefaultRange() = runBlocking {
        val store = FakeHealthConnectDataStore()
        val cleaner = MassHealthConnectCleaner(store)
        val anchor = Instant.parse("2026-06-16T03:30:00Z")
        val supportedRecordTypes = linkedSetOf(
            StepsRecord::class,
            ActiveCaloriesBurnedRecord::class,
        )

        val result = cleaner.deleteAppData(
            supportedRecordTypes = supportedRecordTypes,
            anchor = anchor,
        )

        assertEquals(2, result.deletedTypes)
        assertEquals(emptyList<String>(), result.failures)
        assertEquals(
            listOf(
                DeleteCall(
                    recordType = StepsRecord::class,
                    startTime = anchor.minus(Duration.ofDays(3650)),
                    endTime = anchor.plus(Duration.ofDays(1)),
                ),
                DeleteCall(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    startTime = anchor.minus(Duration.ofDays(3650)),
                    endTime = anchor.plus(Duration.ofDays(1)),
                ),
            ),
            store.deleteCalls,
        )
    }

    @Test
    fun reportsFailedRecordTypesAndKeepsDeletingTheRest() = runBlocking {
        val store = FakeHealthConnectDataStore(
            failingTypes = setOf(StepsRecord::class),
        )
        val cleaner = MassHealthConnectCleaner(store)

        val result = cleaner.deleteAppData(
            supportedRecordTypes = linkedSetOf(
                StepsRecord::class,
                ActiveCaloriesBurnedRecord::class,
            ),
            anchor = Instant.parse("2026-06-16T03:30:00Z"),
        )

        assertEquals(1, result.deletedTypes)
        assertEquals(listOf("StepsRecord: boom"), result.failures)
        assertEquals(2, store.deleteCalls.size)
    }
}

private data class DeleteCall(
    val recordType: KClass<out Record>,
    val startTime: Instant,
    val endTime: Instant,
)

private class FakeHealthConnectDataStore(
    private val failingTypes: Set<KClass<out Record>> = emptySet(),
) : HealthConnectDataStore {
    val deleteCalls = mutableListOf<DeleteCall>()

    override suspend fun insertRecords(records: List<Record>) = Unit

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        startTime: Instant,
        endTime: Instant,
    ) {
        deleteCalls += DeleteCall(recordType, startTime, endTime)
        if (recordType in failingTypes) error("boom")
    }
}
