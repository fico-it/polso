package it.fico.polso

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

data class TimedValue(val time: Instant, val value: Double)

data class StageSeg(val start: Instant, val end: Instant, val type: Int)

data class SleepInfo(val start: Instant, val end: Instant, val stages: List<StageSeg>)

data class ExerciseInfo(val name: String, val start: Instant, val end: Instant)

data class HeartInfo(
    val bins: List<TimedValue>,   // medie a 5 minuti
    val min: Long,
    val max: Long,
    val avg: Long,
)

/** Ogni campo è null quando manca il permesso o non ci sono dati. */
data class DayData(
    val day: LocalDate,
    val dayStart: Instant,
    val hourlySteps: List<Long>?,
    val totalSteps: Long?,
    val distanceKm: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val week: List<Pair<LocalDate, Long>>?,
    val heart: HeartInfo?,
    val resting: List<TimedValue>?,
    val hrv: List<TimedValue>?,
    val sleep: SleepInfo?,
    val spo2: List<TimedValue>?,
    val respiration: List<TimedValue>?,
    val exercises: List<ExerciseInfo>?,
)

class HealthRepo(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    companion object {
        /** Permesso per leggere dati di oltre 30 giorni prima del primo consenso. */
        const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }

    /** Valori giornalieri della metrica tra [from] e [to] inclusi; null senza permesso. */
    suspend fun dailySeries(
        metric: Metric,
        from: LocalDate,
        to: LocalDate,
        granted: Set<String>,
    ): Map<LocalDate, Double>? {
        val startLdt = from.atStartOfDay()
        val endLdt = to.plusDays(1).atStartOfDay()
        val startI = from.atStartOfDay(zone).toInstant()
        val endI = to.plusDays(1).atStartOfDay(zone).toInstant()

        suspend fun <M : Any> perDay(
            type: KClass<out Record>,
            metricKey: androidx.health.connect.client.aggregate.AggregateMetric<M>,
            convert: (M) -> Double,
        ): Map<LocalDate, Double>? {
            if (!granted.allows(type)) return null
            val res = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(metricKey),
                    timeRangeFilter = TimeRangeFilter.between(startLdt, endLdt),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            return res.mapNotNull { r ->
                r.result[metricKey]?.let { r.startTime.toLocalDate() to convert(it) }
            }.toMap()
        }

        return safe {
            when (metric) {
                Metric.STEPS -> perDay(StepsRecord::class, StepsRecord.COUNT_TOTAL) { it.toDouble() }
                Metric.DISTANCE -> perDay(DistanceRecord::class, DistanceRecord.DISTANCE_TOTAL) { it.inKilometers }
                Metric.CALORIES -> perDay(
                    ActiveCaloriesBurnedRecord::class,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                ) { it.inKilocalories }

                Metric.RESTING -> if (!granted.allows(RestingHeartRateRecord::class)) null else
                    readAll(RestingHeartRateRecord::class, TimeRangeFilter.between(startI, endI))
                        .groupBy { it.time.atZone(zone).toLocalDate() }
                        .mapValues { (_, l) -> l.map { it.beatsPerMinute.toDouble() }.average() }

                Metric.HRV -> if (!granted.allows(HeartRateVariabilityRmssdRecord::class)) null else
                    readAll(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(startI, endI))
                        .groupBy { it.time.atZone(zone).toLocalDate() }
                        .mapValues { (_, l) -> l.map { it.heartRateVariabilityMillis }.average() }

                Metric.SLEEP -> if (!granted.allows(SleepSessionRecord::class)) null else {
                    val f = from.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                    val t = to.atTime(18, 0).atZone(zone).toInstant()
                    readAll(SleepSessionRecord::class, TimeRangeFilter.between(f, t))
                        .groupBy { it.endTime.atZone(zone).toLocalDate() }
                        .mapValues { (_, sessions) ->
                            sessions.sumOf { s ->
                                val total = Duration.between(s.startTime, s.endTime).seconds
                                val awake = s.stages
                                    .filter {
                                        it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                                            it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED ||
                                            it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                                    }
                                    .sumOf { Duration.between(it.startTime, it.endTime).seconds }
                                (total - awake).coerceAtLeast(0L) / 3600.0
                            }
                        }
                }
            }
        }
    }

    suspend fun granted(): Set<String> =
        client.permissionController.getGrantedPermissions()

    private fun Set<String>.allows(type: KClass<out Record>): Boolean =
        HealthPermission.getReadPermission(type) in this

    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = filter,
                    pageSize = 2000,
                    pageToken = token,
                )
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    /** Esegue un blocco ignorando errori (permesso revocato, dati assenti...). */
    private suspend fun <R> safe(block: suspend () -> R): R? =
        try {
            block()
        } catch (e: Exception) {
            null
        }

    suspend fun load(day: LocalDate, granted: Set<String>): DayData {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        val dayFilter = TimeRangeFilter.between(start, end)

        // --- Passi ora per ora ---
        val hourly: List<Long>? = if (granted.allows(StepsRecord::class)) safe {
            val buckets = LongArray(24)
            val res = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = dayFilter,
                    timeRangeSlicer = Duration.ofHours(1),
                )
            )
            for (r in res) {
                val h = r.startTime.atZone(zone).hour
                buckets[h] += r.result[StepsRecord.COUNT_TOTAL] ?: 0L
            }
            buckets.toList()
        } else null

        // --- Ultimi 7 giorni ---
        val week: List<Pair<LocalDate, Long>>? = if (granted.allows(StepsRecord::class)) safe {
            val first = day.minusDays(6)
            val res = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        first.atStartOfDay(),
                        day.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            val byDay = res.associate {
                it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
            (0L..6L).map { i -> first.plusDays(i).let { d -> d to (byDay[d] ?: 0L) } }
        } else null

        val distance = if (granted.allows(DistanceRecord::class)) safe {
            client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), dayFilter))[
                DistanceRecord.DISTANCE_TOTAL
            ]?.inKilometers
        } else null

        val active = if (granted.allows(ActiveCaloriesBurnedRecord::class)) safe {
            client.aggregate(
                AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), dayFilter)
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        } else null

        val total = if (granted.allows(TotalCaloriesBurnedRecord::class)) safe {
            client.aggregate(
                AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), dayFilter)
            )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        } else null

        // --- Battito ---
        val heart: HeartInfo? = if (granted.allows(HeartRateRecord::class)) safe {
            val samples = readAll(HeartRateRecord::class, dayFilter)
                .flatMap { it.samples }
                .filter { !it.time.isBefore(start) && it.time.isBefore(end) }
                .sortedBy { it.time }
            if (samples.isEmpty()) null else {
                val bins = samples
                    .groupBy { Duration.between(start, it.time).toMinutes() / 5 }
                    .toSortedMap()
                    .map { (bin, list) ->
                        TimedValue(
                            start.plus(Duration.ofMinutes(bin * 5 + 2)),
                            list.map { it.beatsPerMinute }.average(),
                        )
                    }
                val bpm = samples.map { it.beatsPerMinute }
                HeartInfo(bins, bpm.min(), bpm.max(), bpm.average().toLong())
            }
        } else null

        // Valori "lenti": ultimi 30 giorni fino a fine giornata
        val monthFilter = TimeRangeFilter.between(start.minus(Duration.ofDays(29)), end)

        val resting = if (granted.allows(RestingHeartRateRecord::class)) safe {
            readAll(RestingHeartRateRecord::class, monthFilter)
                .map { TimedValue(it.time, it.beatsPerMinute.toDouble()) }
                .sortedBy { it.time }
                .ifEmpty { null }
        } else null

        val hrv = if (granted.allows(HeartRateVariabilityRmssdRecord::class)) safe {
            readAll(HeartRateVariabilityRmssdRecord::class, monthFilter)
                .map { TimedValue(it.time, it.heartRateVariabilityMillis) }
                .sortedBy { it.time }
                .ifEmpty { null }
        } else null

        // --- Sonno: la notte che termina in questa giornata ---
        val sleep: SleepInfo? = if (granted.allows(SleepSessionRecord::class)) safe {
            val from = day.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val to = day.atTime(18, 0).atZone(zone).toInstant()
            val main = readAll(SleepSessionRecord::class, TimeRangeFilter.between(from, to))
                .maxByOrNull { Duration.between(it.startTime, it.endTime).toMinutes() }
            main?.let { s ->
                val stages = s.stages
                    .map { StageSeg(it.startTime, it.endTime, it.stage) }
                    .sortedBy { it.start }
                    .ifEmpty {
                        listOf(StageSeg(s.startTime, s.endTime, SleepSessionRecord.STAGE_TYPE_SLEEPING))
                    }
                SleepInfo(s.startTime, s.endTime, stages)
            }
        } else null

        val spo2 = if (granted.allows(OxygenSaturationRecord::class)) safe {
            readAll(OxygenSaturationRecord::class, dayFilter)
                .map { TimedValue(it.time, it.percentage.value) }
                .sortedBy { it.time }
                .ifEmpty { null }
        } else null

        val respiration = if (granted.allows(RespiratoryRateRecord::class)) safe {
            readAll(RespiratoryRateRecord::class, dayFilter)
                .map { TimedValue(it.time, it.rate) }
                .sortedBy { it.time }
                .ifEmpty { null }
        } else null

        val exercises = if (granted.allows(ExerciseSessionRecord::class)) safe {
            readAll(ExerciseSessionRecord::class, dayFilter)
                .map { ExerciseInfo(it.title ?: exerciseName(it.exerciseType), it.startTime, it.endTime) }
                .sortedBy { it.start }
                .ifEmpty { null }
        } else null

        return DayData(
            day = day,
            dayStart = start,
            hourlySteps = hourly,
            totalSteps = hourly?.sum(),
            distanceKm = distance,
            activeKcal = active,
            totalKcal = total,
            week = week,
            heart = heart,
            resting = resting,
            hrv = hrv,
            sleep = sleep,
            spo2 = spo2,
            respiration = respiration,
            exercises = exercises,
        )
    }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Camminata"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corsa"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Tapis roulant"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Bici"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Cyclette"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Escursione"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Nuoto in piscina"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Nuoto in acque libere"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Forza"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        else -> "Allenamento"
    }
}
