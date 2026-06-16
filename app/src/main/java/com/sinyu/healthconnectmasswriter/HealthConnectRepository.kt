package com.sinyu.healthconnectmasswriter

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.Record
import kotlin.reflect.KClass

class HealthConnectRepository(
    private val context: Context,
    private val clientProvider: (Context) -> HealthConnectClient = { HealthConnectClient.getOrCreate(it) },
) {
    private val providerPackageName = "com.google.android.apps.healthdata"
    val client: HealthConnectClient by lazy { clientProvider(context) }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun supportedRecordTypes(): Set<KClass<out Record>> =
        HealthConnectCatalog.recordTypes.filterTo(linkedSetOf()) { recordType ->
            HealthConnectCatalog.featureBasedPermissions[recordType]?.let(::isFeatureAvailable) ?: true
        }

    fun allRuntimePermissions(): Set<String> {
        val recordPermissions = HealthConnectCatalog.permissionsFor(supportedRecordTypes())
        val extraPermissions = HealthConnectCatalog.featureBasedExtraPermissions
            .filterValues(::isFeatureAvailable)
            .keys
        return (recordPermissions + extraPermissions).toSet()
    }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    suspend fun missingPermissions(): Set<String> {
        val granted = grantedPermissions()
        return allRuntimePermissions().filterNotTo(linkedSetOf()) { it in granted }
    }

    suspend fun hasAllPermissions(): Boolean =
        missingPermissions().isEmpty()

    fun openHealthConnect() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "android.health.connect.action.HEALTH_HOME_SETTINGS"
        } else {
            "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        }
        context.startActivity(Intent(action))
    }

    fun installHealthConnect() {
        val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.android.vending")
                data = uriString.toUri()
                putExtra("overlay", true)
            }
        )
    }

    private fun isFeatureAvailable(feature: Int): Boolean =
        client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
}
