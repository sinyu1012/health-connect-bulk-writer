package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.StepsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    private val plan = MassDataPlan.fromHeartRateCount(
        specs = MassRecordSpecs.default,
        heartRateCount = 360,
        batchSize = 25,
    )

    private val supportedRecordTypes = setOf(
        HeartRateRecord::class,
        StepsRecord::class,
        DistanceRecord::class,
        ActiveCaloriesBurnedRecord::class,
        HeartRateVariabilityRmssdRecord::class,
    )

    @Test
    fun allowsWritingWhenOnlyNonWritePermissionIsMissing() {
        val granted = HealthConnectCatalog.permissionsFor(supportedRecordTypes) -
            HealthPermission.getReadPermission(HeartRateRecord::class)

        assertTrue(
            PermissionPolicy.canStartWriting(
                plan = plan,
                supportedRecordTypes = supportedRecordTypes,
                grantedPermissions = granted,
            )
        )
    }

    @Test
    fun blocksWritingWhenCurrentPlanWritePermissionIsMissing() {
        val missingWrite = HealthPermission.getWritePermission(StepsRecord::class)
        val granted = HealthConnectCatalog.permissionsFor(supportedRecordTypes) - missingWrite

        assertFalse(
            PermissionPolicy.canStartWriting(
                plan = plan,
                supportedRecordTypes = supportedRecordTypes,
                grantedPermissions = granted,
            )
        )
        assertEquals(
            setOf(missingWrite),
            PermissionPolicy.missingWritePermissions(
                plan = plan,
                supportedRecordTypes = supportedRecordTypes,
                grantedPermissions = granted,
            )
        )
    }
}
