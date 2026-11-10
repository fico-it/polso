package it.fico.polso

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class Aggregation { SUM, AVERAGE }

/** Statistiche disponibili nello storico, ciascuna con il suo colore. */
enum class Metric(
    val label: String,
    val unit: String,
    val color: Color,
    val aggregation: Aggregation,
    val decimals: Int,
) {
    STEPS("Passi", "passi", Palette.Steps, Aggregation.SUM, 0),
    DISTANCE("Distanza", "km", Palette.Distance, Aggregation.SUM, 1),
    CALORIES("Calorie attive", "kcal", Palette.Kcal, Aggregation.SUM, 0),
    SLEEP("Sonno", "h", Palette.Sleep, Aggregation.AVERAGE, 1),
    RESTING("Battito a riposo", "bpm", Palette.Resting, Aggregation.AVERAGE, 0),
    HRV("HRV", "ms", Palette.Hrv, Aggregation.AVERAGE, 0),
}

enum class HistoryPeriod(val label: String) {
    WEEK("Settimana"),
    MONTH("Mese"),
    YEAR("Anno"),
}

/** Serie pronta da disegnare: un valore (o null se assente) per ciascuna barra. */
data class HistorySeries(
    val metric: Metric,
    val period: HistoryPeriod,
    val title: String,
    val values: List<Double?>,
    val labels: List<String>,        // etichetta breve sotto la barra ("" = nessuna)
    val longLabels: List<String>,    // etichetta per il valore selezionato
    val highlight: Int?,             // oggi / mese corrente
    val perDayNote: Boolean,         // anno: media giornaliera del mese
)

private val itShortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

/** Intervallo di date coperto da un periodo che contiene [anchor]. */
fun periodRange(period: HistoryPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    HistoryPeriod.WEEK -> anchor.minusDays(6) to anchor
    HistoryPeriod.MONTH -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
    HistoryPeriod.YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
}

fun shiftAnchor(period: HistoryPeriod, anchor: LocalDate, delta: Long): LocalDate = when (period) {
    HistoryPeriod.WEEK -> anchor.plusWeeks(delta)
    HistoryPeriod.MONTH -> anchor.plusMonths(delta)
    HistoryPeriod.YEAR -> anchor.plusYears(delta)
}

/** Costruisce la serie a partire dai valori giornalieri. */
fun buildSeries(
    metric: Metric,
    period: HistoryPeriod,
    anchor: LocalDate,
    daily: Map<LocalDate, Double>,
    today: LocalDate = LocalDate.now(),
): HistorySeries {
    val (from, to) = periodRange(period, anchor)

    fun dayValue(d: LocalDate): Double? = when {
        d.isAfter(today) -> null
        metric.aggregation == Aggregation.SUM -> daily[d] ?: 0.0
        else -> daily[d]
    }

    return when (period) {
        HistoryPeriod.WEEK, HistoryPeriod.MONTH -> {
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
            val title = if (period == HistoryPeriod.MONTH) {
                YearMonth.from(anchor).let {
                    it.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
                        .replaceFirstChar { c -> c.uppercase() } + " ${it.year}"
                }
            } else {
                "${from.dayOfMonth} ${from.format(itShortMonth)} – ${to.dayOfMonth} ${to.format(itShortMonth)}"
            }
            HistorySeries(
                metric = metric,
                period = period,
                title = title,
                values = days.map { dayValue(it) },
                labels = days.mapIndexed { i, d ->
                    if (period == HistoryPeriod.WEEK) {
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(3)
                    } else if (d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0) {
                        d.dayOfMonth.toString()
                    } else ""
                },
                longLabels = days.map {
                    it.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                },
                highlight = days.indexOf(today).takeIf { it >= 0 },
                perDayNote = false,
            )
        }

        HistoryPeriod.YEAR -> {
            val months = (1..12).map { YearMonth.of(anchor.year, it) }
            val values = months.map { ym ->
                val days = generateSequence(ym.atDay(1)) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(ym.atEndOfMonth()) && !it.isAfter(today) }
                    .toList()
                if (days.isEmpty()) null else {
                    val vals = days.mapNotNull { daily[it] }
                    when {
                        vals.isEmpty() -> null
                        metric.aggregation == Aggregation.SUM -> vals.sum() / days.size
                        else -> vals.average()
                    }
                }
            }
            HistorySeries(
                metric = metric,
                period = period,
                title = anchor.year.toString(),
                values = values,
                labels = months.map { it.month.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.ITALIAN) },
                longLabels = months.map {
                    it.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN) + " ${it.year}"
                },
                highlight = months.indexOf(YearMonth.from(today)).takeIf { it >= 0 },
                perDayNote = metric.aggregation == Aggregation.SUM,
            )
        }
    }
}
