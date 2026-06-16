package com.sinyu.healthconnectmasswriter

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ActivityIntensityRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseCompletionGoal
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExercisePerformanceTarget
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.PlannedExerciseBlock
import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import androidx.health.connect.client.records.PlannedExerciseStep
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.reflect.KClass

private val heartRateBaseline = Duration.ofSeconds(10)

data class MassRecordSpec(
    val recordType: KClass<out Record>,
    val label: String,
    val interval: Duration,
) {
    fun countOf(heartRateCount: Int): Int {
        val spanMillis = heartRateCount.toLong() * heartRateBaseline.toMillis()
        return (spanMillis / interval.toMillis()).toInt()
    }
}

object MassRecordSpecs {
    private fun spec(
        recordType: KClass<out Record>,
        label: String,
        interval: Duration,
    ) = MassRecordSpec(recordType = recordType, label = label, interval = interval)

    val default: List<MassRecordSpec> = listOf(
        spec(HeartRateRecord::class, "心率", heartRateBaseline),
        spec(StepsRecord::class, "步数", Duration.ofMinutes(1)),
        spec(DistanceRecord::class, "距离", Duration.ofMinutes(5)),
        spec(ActiveCaloriesBurnedRecord::class, "活动能量", Duration.ofMinutes(10)),
        spec(HeartRateVariabilityRmssdRecord::class, "HRV", Duration.ofMinutes(30)),
    )

    val allSynthetic: List<MassRecordSpec> = default + listOf(
        spec(TotalCaloriesBurnedRecord::class, "总热量", Duration.ofMinutes(10)),
        spec(StepsCadenceRecord::class, "步频", Duration.ofMinutes(1)),
        spec(CyclingPedalingCadenceRecord::class, "骑行踏频", Duration.ofMinutes(1)),
        spec(PowerRecord::class, "功率", Duration.ofMinutes(1)),
        spec(SpeedRecord::class, "速度", Duration.ofMinutes(1)),
        spec(ActivityIntensityRecord::class, "活动强度", Duration.ofMinutes(30)),
        spec(ElevationGainedRecord::class, "海拔爬升", Duration.ofMinutes(30)),
        spec(FloorsClimbedRecord::class, "爬楼", Duration.ofHours(1)),
        spec(RespiratoryRateRecord::class, "呼吸频率", Duration.ofHours(1)),
        spec(OxygenSaturationRecord::class, "血氧", Duration.ofHours(1)),
        spec(SkinTemperatureRecord::class, "皮肤温度", Duration.ofHours(1)),
        spec(BloodGlucoseRecord::class, "血糖", Duration.ofHours(6)),
        spec(HydrationRecord::class, "饮水", Duration.ofHours(4)),
        spec(NutritionRecord::class, "营养", Duration.ofHours(8)),
        spec(SleepSessionRecord::class, "睡眠", Duration.ofDays(1)),
        spec(ExerciseSessionRecord::class, "运动", Duration.ofDays(1)),
        spec(MindfulnessSessionRecord::class, "正念", Duration.ofDays(1)),
        spec(RestingHeartRateRecord::class, "静息心率", Duration.ofDays(1)),
        spec(WeightRecord::class, "体重", Duration.ofDays(1)),
        spec(BodyFatRecord::class, "体脂", Duration.ofDays(7)),
        spec(BodyWaterMassRecord::class, "身体水分", Duration.ofDays(7)),
        spec(BoneMassRecord::class, "骨量", Duration.ofDays(7)),
        spec(LeanBodyMassRecord::class, "瘦体重", Duration.ofDays(7)),
        spec(Vo2MaxRecord::class, "VO2Max", Duration.ofDays(7)),
        spec(HeightRecord::class, "身高", Duration.ofDays(30)),
        spec(BasalBodyTemperatureRecord::class, "基础体温", Duration.ofDays(1)),
        spec(BodyTemperatureRecord::class, "体温", Duration.ofDays(1)),
        spec(BasalMetabolicRateRecord::class, "基础代谢", Duration.ofDays(1)),
        spec(BloodPressureRecord::class, "血压", Duration.ofDays(1)),
        spec(CervicalMucusRecord::class, "宫颈黏液", Duration.ofDays(30)),
        spec(IntermenstrualBleedingRecord::class, "经间出血", Duration.ofDays(30)),
        spec(MenstruationFlowRecord::class, "经量", Duration.ofDays(30)),
        spec(MenstruationPeriodRecord::class, "经期", Duration.ofDays(30)),
        spec(OvulationTestRecord::class, "排卵测试", Duration.ofDays(30)),
        spec(SexualActivityRecord::class, "性活动", Duration.ofDays(30)),
        spec(WheelchairPushesRecord::class, "轮椅推动", Duration.ofHours(1)),
        spec(PlannedExerciseSessionRecord::class, "计划运动", Duration.ofDays(7)),
    )
}

data class MassDataPlan(
    val specs: List<MassRecordSpec>,
    val requestedHeartRateCount: Int,
    val batchSize: Int,
    val counts: Map<MassRecordSpec, Int>,
) {
    val totalRecords: Int = counts.values.sum()

    fun countFor(recordType: KClass<out Record>): Int =
        counts.entries.firstOrNull { it.key.recordType == recordType }?.value ?: 0

    companion object {
        fun fromHeartRateCount(
            specs: List<MassRecordSpec>,
            heartRateCount: Int,
            batchSize: Int,
        ): MassDataPlan {
            val sanitizedHeartRateCount = heartRateCount.coerceAtLeast(1)
            val sanitizedBatchSize = batchSize.coerceAtLeast(1)
            val counts = specs.associateWith { it.countOf(sanitizedHeartRateCount) }
            return MassDataPlan(
                specs = specs,
                requestedHeartRateCount = sanitizedHeartRateCount,
                batchSize = sanitizedBatchSize,
                counts = counts,
            )
        }

        fun formatScale(heartRateCount: Int): String {
            val wan = heartRateCount / 10_000
            val days = heartRateCount * heartRateBaseline.seconds.toDouble() / Duration.ofDays(1).seconds
            val span = if (days >= 365.0) {
                String.format(Locale.US, "%.1f年", days / 365.0)
            } else {
                String.format(Locale.US, "%.1f月", days / 30.0)
            }
            return "${wan}万 · $span"
        }
    }
}

data class MassWriteProgress(
    val label: String,
    val specIndex: Int,
    val specCount: Int,
    val doneForSpec: Int,
    val totalForSpec: Int,
    val totalInserted: Int,
    val totalPlanned: Int,
)

data class MassWriteResult(
    val requestedHeartRateCount: Int,
    val totalInserted: Int,
    val recordCounts: Map<String, Int>,
    val failures: List<String>,
)

class MassHealthConnectWriter(
    private val client: HealthConnectClient,
    private val factory: MassRecordFactory = MassRecordFactory(),
) {
    suspend fun write(
        plan: MassDataPlan,
        supportedRecordTypes: Set<KClass<out Record>>,
        anchor: Instant = Instant.now(),
        onProgress: (MassWriteProgress) -> Unit = {},
    ): MassWriteResult {
        val activeCounts = plan.counts
            .filter { (spec, count) -> count > 0 && spec.recordType in supportedRecordTypes }
        val totalPlanned = activeCounts.values.sum()
        val recordCounts = linkedMapOf<String, Int>()
        val failures = mutableListOf<String>()
        var totalInserted = 0

        activeCounts.entries.forEachIndexed { specIndex, (spec, totalForSpec) ->
            var doneForSpec = 0
            while (doneForSpec < totalForSpec) {
                currentCoroutineContext().ensureActive()
                val currentBatchSize = minOf(plan.batchSize, totalForSpec - doneForSpec)
                val records = factory.createRecords(
                    spec = spec,
                    startIndex = doneForSpec,
                    count = currentBatchSize,
                    anchor = anchor,
                )

                val recordName = spec.label
                try {
                    client.insertRecords(records)
                    doneForSpec += records.size
                    totalInserted += records.size
                    recordCounts[recordName] = (recordCounts[recordName] ?: 0) + records.size
                } catch (t: Throwable) {
                    failures += "$recordName: ${t.message ?: t::class.java.simpleName}"
                    break
                }

                onProgress(
                    MassWriteProgress(
                        label = spec.label,
                        specIndex = specIndex,
                        specCount = activeCounts.size,
                        doneForSpec = doneForSpec,
                        totalForSpec = totalForSpec,
                        totalInserted = totalInserted,
                        totalPlanned = totalPlanned,
                    )
                )
            }
        }

        return MassWriteResult(
            requestedHeartRateCount = plan.requestedHeartRateCount,
            totalInserted = totalInserted,
            recordCounts = recordCounts,
            failures = failures,
        )
    }
}

class MassRecordFactory {
    fun createRecords(
        spec: MassRecordSpec,
        startIndex: Int,
        count: Int,
        anchor: Instant,
    ): List<Record> =
        (startIndex until startIndex + count).map { createRecord(spec, it, anchor) }

    private fun createRecord(spec: MassRecordSpec, index: Int, anchor: Instant): Record {
        val window = windowFor(spec, index, anchor)
        val time = window.endTime
        val metadata = Metadata.manualEntry()

        return when (spec.recordType) {
            HeartRateRecord::class -> HeartRateRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = window.midpoint,
                        beatsPerMinute = (60 + index % 40).toLong(),
                    )
                ),
                metadata = metadata,
            )

            StepsRecord::class -> StepsRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                count = (20 + index % 180).toLong(),
                metadata = metadata,
            )

            DistanceRecord::class -> DistanceRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                distance = Length.meters(80.0 + index % 300),
                metadata = metadata,
            )

            ActiveCaloriesBurnedRecord::class -> ActiveCaloriesBurnedRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                energy = Energy.calories(5.0 + index % 40),
                metadata = metadata,
            )

            HeartRateVariabilityRmssdRecord::class -> HeartRateVariabilityRmssdRecord(
                time = time,
                zoneOffset = null,
                heartRateVariabilityMillis = 20.0 + index % 100,
                metadata = metadata,
            )

            TotalCaloriesBurnedRecord::class -> TotalCaloriesBurnedRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                energy = Energy.calories(30.0 + index % 200),
                metadata = metadata,
            )

            StepsCadenceRecord::class -> StepsCadenceRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                samples = listOf(StepsCadenceRecord.Sample(time = window.midpoint, rate = 90.0 + index % 40)),
                metadata = metadata,
            )

            CyclingPedalingCadenceRecord::class -> CyclingPedalingCadenceRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                samples = listOf(
                    CyclingPedalingCadenceRecord.Sample(
                        time = window.midpoint,
                        revolutionsPerMinute = 60.0 + index % 50,
                    )
                ),
                metadata = metadata,
            )

            PowerRecord::class -> PowerRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                samples = listOf(PowerRecord.Sample(time = window.midpoint, power = Power.watts(80.0 + index % 220))),
                metadata = metadata,
            )

            SpeedRecord::class -> SpeedRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                samples = listOf(
                    SpeedRecord.Sample(
                        time = window.midpoint,
                        speed = Velocity.kilometersPerHour(4.0 + index % 18),
                    )
                ),
                metadata = metadata,
            )

            ActivityIntensityRecord::class -> ActivityIntensityRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                activityIntensityType = ActivityIntensityRecord.ACTIVITY_INTENSITY_TYPE_MODERATE,
                metadata = metadata,
            )

            ElevationGainedRecord::class -> ElevationGainedRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                elevation = Length.meters((index % 20).toDouble()),
                metadata = metadata,
            )

            FloorsClimbedRecord::class -> FloorsClimbedRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                floors = (index % 8).toDouble(),
                metadata = metadata,
            )

            RespiratoryRateRecord::class -> RespiratoryRateRecord(
                time = time,
                zoneOffset = null,
                rate = 12.0 + index % 10,
                metadata = metadata,
            )

            OxygenSaturationRecord::class -> OxygenSaturationRecord(
                time = time,
                zoneOffset = null,
                percentage = Percentage(94.0 + index % 6),
                metadata = metadata,
            )

            SkinTemperatureRecord::class -> SkinTemperatureRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                deltas = listOf(
                    SkinTemperatureRecord.Delta(
                        time = window.midpoint,
                        delta = TemperatureDelta.celsius((index % 8) / 10.0),
                    )
                ),
                baseline = Temperature.celsius(36.4),
                measurementLocation = 1,
                metadata = metadata,
            )

            BloodGlucoseRecord::class -> BloodGlucoseRecord(
                time = time,
                zoneOffset = null,
                level = BloodGlucose.milligramsPerDeciliter(80.0 + index % 50),
                specimenSource = 0,
                mealType = 0,
                relationToMeal = 0,
                metadata = metadata,
            )

            HydrationRecord::class -> HydrationRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                volume = Volume.liters(0.2 + (index % 8) * 0.05),
                metadata = metadata,
            )

            NutritionRecord::class -> NutritionRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                energy = Energy.calories(200.0 + index % 600),
                protein = Mass.grams(8.0 + index % 40),
                totalCarbohydrate = Mass.grams(20.0 + index % 80),
                totalFat = Mass.grams(5.0 + index % 30),
                name = "mass meal $index",
                mealType = 1,
                metadata = metadata,
            )

            SleepSessionRecord::class -> SleepSessionRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                title = "mass sleep $index",
                notes = null,
                stages = listOf(
                    SleepSessionRecord.Stage(
                        startTime = window.startTime,
                        endTime = window.endTime,
                        stage = 1 + index % 5,
                    )
                ),
                metadata = metadata,
            )

            ExerciseSessionRecord::class -> ExerciseSessionRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                exerciseType = 0,
                segments = listOf(
                    ExerciseSegment(
                        startTime = window.startTime,
                        endTime = window.endTime,
                        segmentType = 0,
                        repetitions = 1 + index % 20,
                    )
                ),
                laps = listOf(
                    ExerciseLap(
                        startTime = window.startTime,
                        endTime = window.endTime,
                        length = Length.meters(1000.0 + index % 5000),
                    )
                ),
                title = "mass exercise $index",
                notes = null,
                metadata = metadata,
            )

            MindfulnessSessionRecord::class -> MindfulnessSessionRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                title = "mass mindfulness $index",
                notes = null,
                metadata = metadata,
            )

            RestingHeartRateRecord::class -> RestingHeartRateRecord(
                time = time,
                zoneOffset = null,
                beatsPerMinute = (50 + index % 30).toLong(),
                metadata = metadata,
            )

            WeightRecord::class -> WeightRecord(
                time = time,
                zoneOffset = null,
                weight = Mass.kilograms(60.0 + (index % 100) / 10.0),
                metadata = metadata,
            )

            BodyFatRecord::class -> BodyFatRecord(
                time = time,
                zoneOffset = null,
                percentage = Percentage(15.0 + index % 20),
                metadata = metadata,
            )

            BodyWaterMassRecord::class -> BodyWaterMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(30.0 + index % 20),
                metadata = metadata,
            )

            BoneMassRecord::class -> BoneMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(2.0 + (index % 20) / 10.0),
                metadata = metadata,
            )

            LeanBodyMassRecord::class -> LeanBodyMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(45.0 + index % 20),
                metadata = metadata,
            )

            Vo2MaxRecord::class -> Vo2MaxRecord(
                time = time,
                zoneOffset = null,
                vo2MillilitersPerMinuteKilogram = 30.0 + index % 25,
                measurementMethod = 1,
                metadata = metadata,
            )

            HeightRecord::class -> HeightRecord(
                time = time,
                zoneOffset = null,
                height = Length.meters(1.55 + (index % 35) / 100.0),
                metadata = metadata,
            )

            BasalBodyTemperatureRecord::class -> BasalBodyTemperatureRecord(
                time = time,
                zoneOffset = null,
                temperature = Temperature.celsius(36.0 + (index % 15) / 10.0),
                measurementLocation = 0,
                metadata = metadata,
            )

            BodyTemperatureRecord::class -> BodyTemperatureRecord(
                time = time,
                zoneOffset = null,
                temperature = Temperature.celsius(36.0 + (index % 15) / 10.0),
                measurementLocation = 0,
                metadata = metadata,
            )

            BasalMetabolicRateRecord::class -> BasalMetabolicRateRecord(
                time = time,
                zoneOffset = null,
                basalMetabolicRate = Power.watts(60.0 + index % 30),
                metadata = metadata,
            )

            BloodPressureRecord::class -> BloodPressureRecord(
                time = time,
                zoneOffset = null,
                systolic = Pressure.millimetersOfMercury(110.0 + index % 20),
                diastolic = Pressure.millimetersOfMercury(70.0 + index % 15),
                bodyPosition = 0,
                measurementLocation = 0,
                metadata = metadata,
            )

            CervicalMucusRecord::class -> CervicalMucusRecord(
                time = time,
                zoneOffset = null,
                appearance = 0,
                sensation = 0,
                metadata = metadata,
            )

            IntermenstrualBleedingRecord::class -> IntermenstrualBleedingRecord(
                time = time,
                zoneOffset = null,
                metadata = metadata,
            )

            MenstruationFlowRecord::class -> MenstruationFlowRecord(
                time = time,
                zoneOffset = null,
                flow = 1,
                metadata = metadata,
            )

            MenstruationPeriodRecord::class -> MenstruationPeriodRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                metadata = metadata,
            )

            OvulationTestRecord::class -> OvulationTestRecord(
                time = time,
                zoneOffset = null,
                result = 1,
                metadata = metadata,
            )

            SexualActivityRecord::class -> SexualActivityRecord(
                time = time,
                zoneOffset = null,
                protectionUsed = 1,
                metadata = metadata,
            )

            WheelchairPushesRecord::class -> WheelchairPushesRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                count = (10 + index % 100).toLong(),
                metadata = metadata,
            )

            PlannedExerciseSessionRecord::class -> PlannedExerciseSessionRecord(
                startTime = window.startTime,
                startZoneOffset = null,
                endTime = window.endTime,
                endZoneOffset = null,
                exerciseType = 0,
                title = "mass planned exercise $index",
                notes = null,
                blocks = listOf(
                    PlannedExerciseBlock(
                        repetitions = 1,
                        description = "block",
                        steps = listOf(
                            PlannedExerciseStep(
                                exerciseType = 0,
                                exercisePhase = 0,
                                completionGoal = ExerciseCompletionGoal.DistanceAndDurationGoal(
                                    distance = Length.meters(1000.0 + index % 3000),
                                    duration = Duration.ofMinutes((20 + index % 30).toLong()),
                                ),
                                performanceTargets = listOf(
                                    ExercisePerformanceTarget.RateOfPerceivedExertionTarget(3)
                                ),
                                description = "step",
                            )
                        ),
                    )
                ),
                metadata = metadata,
            )

            else -> error("Unsupported record type: ${spec.recordType.simpleName}")
        }
    }

    private fun windowFor(spec: MassRecordSpec, index: Int, anchor: Instant): TimeWindow {
        val end = anchor.minus(spec.interval.multipliedBy(index.toLong()))
        val start = end.minus(spec.interval)
        return TimeWindow(startTime = start, endTime = end)
    }
}

private data class TimeWindow(
    val startTime: Instant,
    val endTime: Instant,
) {
    val midpoint: Instant =
        startTime.plusMillis(Duration.between(startTime, endTime).toMillis() / 2)
}
