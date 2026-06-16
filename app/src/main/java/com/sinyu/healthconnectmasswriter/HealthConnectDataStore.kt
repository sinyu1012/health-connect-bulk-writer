package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

interface HealthConnectDataStore {
    suspend fun insertRecords(records: List<Record>)

    suspend fun deleteRecords(
        recordType: KClass<out Record>,
        startTime: Instant,
        endTime: Instant,
    )
}

class AndroidXHealthConnectDataStore(
    private val client: HealthConnectClient,
) : HealthConnectDataStore {
    override suspend fun insertRecords(records: List<Record>) {
        client.insertRecords(records)
    }

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        startTime: Instant,
        endTime: Instant,
    ) {
        client.deleteRecords(
            recordType = recordType,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
        )
    }
}
