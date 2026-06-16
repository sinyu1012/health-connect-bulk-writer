package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import kotlin.reflect.KClass

object PermissionPolicy {
    fun requiredWritePermissions(
        plan: MassDataPlan,
        supportedRecordTypes: Set<KClass<out Record>>,
    ): Set<String> {
        val activeRecordTypes = plan.counts
            .filter { (spec, count) -> count > 0 && spec.recordType in supportedRecordTypes }
            .keys
            .mapTo(linkedSetOf()) { it.recordType }
        return HealthConnectCatalog.writePermissionsFor(activeRecordTypes)
    }

    fun missingWritePermissions(
        plan: MassDataPlan,
        supportedRecordTypes: Set<KClass<out Record>>,
        grantedPermissions: Set<String>,
    ): Set<String> =
        requiredWritePermissions(plan, supportedRecordTypes)
            .filterNotTo(linkedSetOf()) { it in grantedPermissions }

    fun canStartWriting(
        plan: MassDataPlan,
        supportedRecordTypes: Set<KClass<out Record>>,
        grantedPermissions: Set<String>,
    ): Boolean =
        missingWritePermissions(plan, supportedRecordTypes, grantedPermissions).isEmpty()

    fun displayName(permission: String): String =
        when (permission) {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND -> "后台读取健康数据"
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY -> "读取历史健康数据"
            HealthPermission.PERMISSION_READ_EXERCISE_ROUTES -> "读取运动路线"
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE -> "写入运动路线"
            else -> permission
                .removePrefix("android.permission.health.")
                .replace('_', ' ')
        }
}
