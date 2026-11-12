package it.fico.polso

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId = ZoneId.systemDefault()
private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val longDayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onRequestPermissions: () -> Unit,
    onRequestHistory: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onExport: () -> Unit,
) {
    BackHandler(enabled = vm.showSettings) { vm.showSettings = false }

    val bg by animateColorAsState(
        targetValue = vm.coach?.background ?: Palette.Bg,
        animationSpec = tween(1500),
        label = "sfondo",
    )
    Surface(Modifier.fillMaxSize(), color = bg, contentColor = Palette.Text) {
        Box(Modifier.safeDrawingPadding()) {
            when (vm.status) {
                Status.CHECKING -> Centered { Text("…", color = Palette.Muted) }

                Status.UNAVAILABLE -> Centered {
                    Text(
                        "Connessione Salute non è disponibile su questo telefono.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenHealthConnect) { Text("Apri Connessione Salute") }
                }

                Status.NEEDS_PERMISSION -> Centered {
                    Text("Polso", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Palette.Steps)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Per mostrare i dati della band serve il permesso di lettura " +
                            "su Connessione Salute. Polso legge soltanto: non scrive e non invia nulla.",
                        textAlign = TextAlign.Center,
                        color = Palette.Muted,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.Steps,
                            contentColor = Color.Black,
                        ),
                    ) { Text("Concedi accesso") }
                }

                Status.READY ->
                    if (vm.showSettings) {
                        SettingsScreen(vm, onRequestPermissions, onRequestHistory, onExport)
                    } else {
                        Dashboard(vm, onRequestPermissions, onOpenHealthConnect, onExport)
                    }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Dashboard(
    vm: DashboardViewModel,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onExport: () -> Unit,
) {
    val d = vm.data
    val s = vm.settings
    val isToday = vm.day == LocalDate.now()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(vm, isToday) }

        if (d == null) {
            item {
                Text(
                    if (vm.loading) "Caricamento…" else "Nessun dato per questo giorno.",
                    color = Palette.Muted,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        s.order.filter { s.isVisible(it) }.forEach { card ->
            if (card == CardId.HISTORY) {
                item(key = card.name) { HistoryCard(vm) }
            } else if (card == CardId.TIPS) {
                val c = vm.coach
                if (c != null && c.tips.isNotEmpty()) item(key = card.name) { TipsCard(c) }
            } else if (d != null) {
                when (card) {
                    CardId.STEPS -> {
                        val hours = d.hourlySteps
                        if (hours != null) item(key = card.name) {
                            HeroCard(d, hours, s.stepGoal, if (isToday) LocalTime.now().hour else null)
                        }
                    }
                    CardId.WEEK -> {
                        val week = d.week
                        if (week != null) item(key = card.name) { WeekCard(week, d.day, s.stepGoal) }
                    }
                    CardId.HEART -> {
                        val heart = d.heart
                        val resting = d.resting
                        if (heart != null) {
                            item(key = card.name) { HeartCard(d, heart) }
                        } else if (resting != null) {
                            item(key = card.name) { TrendCard("Battito a riposo", Palette.Resting, resting, "bpm", 0) }
                        }
                    }
                    CardId.SLEEP -> {
                        val sl = d.sleep
                        if (sl != null) item(key = card.name) { SleepCard(sl) }
                    }
                    CardId.HRV -> {
                        val list = d.hrv
                        if (list != null) item(key = card.name) { HrvCard(d, list) }
                    }
                    CardId.SPO2 -> {
                        val list = d.spo2
                        if (list != null) item(key = card.name) {
                            DayValuesCard("Ossigeno nel sangue (SpO₂)", Palette.Spo2, list, "%", d)
                        }
                    }
                    CardId.RESPIRATION -> {
                        val list = d.respiration
                        if (list != null) item(key = card.name) {
                            DayValuesCard("Frequenza respiratoria", Palette.Breath, list, "atti/min", d)
                        }
                    }
                    CardId.WORKOUTS -> {
                        val list = d.exercises
                        if (list != null) item(key = card.name) { WorkoutsCard(list) }
                    }
                    CardId.HISTORY, CardId.TIPS -> Unit
                }
            }
        }

        item { Footer(vm, onRequestPermissions, onOpenHealthConnect, onExport) }
    }
}

// ---------------------------------------------------------------- intestazione

@Composable
private fun Header(vm: DashboardViewModel, isToday: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Polso", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Palette.Steps)
                Text(
                    vm.day.format(longDayFmt).replaceFirstChar { it.uppercase() },
                    fontSize = 13.sp,
                    color = Palette.Muted,
                )
            }
            Pill("Personalizza", selected = false, color = Palette.Steps) { vm.showSettings = true }
        }
        vm.coach?.phrase?.takeIf { it.isNotBlank() }?.let { phrase ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .size(width = 3.dp, height = 30.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Steps.copy(alpha = 0.8f))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    phrase,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontStyle = FontStyle.Italic,
                    color = Palette.Text.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.shiftDay(-1) }) { Text("‹", fontSize = 24.sp, color = Palette.Text) }
            Text(
                text = when (vm.day) {
                    LocalDate.now() -> "Oggi"
                    LocalDate.now().minusDays(1) -> "Ieri"
                    else -> vm.day.format(dayFmt).replaceFirstChar { it.uppercase() }
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (!isToday) {
                TextButton(onClick = { vm.goToday() }) { Text("Oggi", color = Palette.Steps, fontSize = 13.sp) }
            }
            TextButton(onClick = { vm.shiftDay(1) }, enabled = !isToday) {
                Text("›", fontSize = 24.sp, color = if (isToday) Palette.Grid else Palette.Text)
            }
        }
        if (vm.loading) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = Palette.Steps,
                trackColor = Palette.Track,
            )
        }
    }
}

// ---------------------------------------------------------------- mattoni

@Composable
private fun Card(
    title: String,
    color: Color,
    tinted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = if (tinted) {
        Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Palette.Card, Palette.Card))
    } else {
        Brush.verticalGradient(listOf(Palette.CardHi, Palette.Card))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.18f), shape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color = Palette.Text, big: Boolean = false) {
    Column {
        Text(value, fontSize = if (big) 34.sp else 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = Palette.Muted)
    }
}

/** Piccola tessera colorata per i riepiloghi. */
@Composable
private fun Tile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Palette.Muted, maxLines = 1)
    }
}

@Composable
private fun Caption(text: String, color: Color = Palette.Muted) {
    Text(text, fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 8.dp))
}

// ---------------------------------------------------------------- schede

@Composable
private fun HeroCard(d: DayData, hours: List<Long>, goal: Int, currentHour: Int?) {
    val steps = d.totalSteps ?: 0L
    val progress = steps.toFloat() / goal.coerceAtLeast(1)
    var sel by remember(d.day) { mutableStateOf<Int?>(null) }

    Card("Passi", Palette.Steps, tinted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(progress, Palette.Steps) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Palette.Steps)
                    Text("obiettivo", fontSize = 10.sp, color = Palette.Muted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(fmtInt(steps), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Palette.Text)
                Text("passi su ${fmtInt(goal.toLong())}", fontSize = 13.sp, color = Palette.Muted)
                val left = goal - steps
                Text(
                    if (left > 0) "ne mancano ${fmtInt(left)}" else "obiettivo raggiunto",
                    fontSize = 13.sp,
                    color = if (left > 0) Palette.Muted else Palette.Steps,
                    fontWeight = if (left > 0) FontWeight.Normal else FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.distanceKm?.let { Tile(String.format(Locale.ITALY, "%.1f km", it), "distanza", Palette.Distance, Modifier.weight(1f)) }
            (d.activeKcal ?: d.totalKcal)?.let {
                Tile("${fmtInt(it.toLong())}", if (d.activeKcal != null) "kcal attive" else "kcal", Palette.Kcal, Modifier.weight(1f))
            }
            d.resting?.lastOrNull { it.time.atZone(zone).toLocalDate() == d.day }?.let {
                Tile("${it.value.toLong()} bpm", "a riposo", Palette.Resting, Modifier.weight(1f))
            }
            d.sleep?.let { sl ->
                Tile(fmtHours(asleepDuration(sl)), "sonno", Palette.Sleep, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Ora per ora", fontSize = 12.sp, color = Palette.Muted)
        Spacer(Modifier.height(6.dp))
        BarChart(
            values = hours.map { it.toDouble() },
            color = Palette.Steps,
            labels = hours.indices.map { if (it % 3 == 0) "%02d".format(it) else "" },
            formatAxis = { fmtAxis(it) },
            highlight = currentHour,
            selected = sel,
            onSelect = { sel = it },
        )
        val shown = sel
        if (shown != null) {
            Caption("%02d:00–%02d:00 · %s passi".format(shown, (shown + 1) % 24, fmtInt(hours[shown])), Palette.Text)
        } else {
            val best = hours.indices.maxByOrNull { hours[it] }
            if (best != null && hours[best] > 0) {
                Caption("Ora più attiva %02d:00 · tocca una barra per il dettaglio".format(best))
            }
        }
    }
}

@Composable
private fun WeekCard(week: List<Pair<LocalDate, Long>>, selected: LocalDate, goal: Int) {
    val avg = week.map { it.second }.average().toLong()
    val hit = week.count { it.second >= goal }
    var sel by remember(week) { mutableStateOf<Int?>(null) }
    Card("Ultimi 7 giorni", Palette.Distance) {
        Row {
            Stat(fmtInt(avg), "media giornaliera", Palette.Distance)
            Spacer(Modifier.weight(1f))
            Stat("$hit/7", "giorni a obiettivo")
        }
        Spacer(Modifier.height(14.dp))
        BarChart(
            values = week.map { it.second.toDouble() },
            color = Palette.Distance,
            labels = week.map { it.first.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALIAN).take(3) },
            formatAxis = { fmtAxis(it) },
            highlight = week.indexOfFirst { it.first == selected }.takeIf { it >= 0 },
            selected = sel,
            goal = goal.toDouble(),
            maxBarWidth = 30.dp,
            height = 120.dp,
            onSelect = { sel = it },
        )
        sel?.let { i ->
            Caption("${week[i].first.format(longDayFmt)} · ${fmtInt(week[i].second)} passi", Palette.Text)
        } ?: Caption("Linea tratteggiata: obiettivo di ${fmtInt(goal.toLong())} passi")
    }
}

@Composable
private fun HistoryCard(vm: DashboardViewModel) {
    val s = vm.settings
    val metric = s.historyMetric
    val series = vm.history
    var sel by remember(series) { mutableStateOf<Int?>(null) }

    Card("Storico", metric.color) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Metric.values().forEach { m ->
                Pill(m.label, m == metric, m.color) { vm.setHistory(metric = m) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HistoryPeriod.values().forEach { p ->
                Pill(p.label, p == s.historyPeriod, metric.color) { vm.setHistory(period = p) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.shiftHistory(-1) }) { Text("‹", fontSize = 22.sp, color = Palette.Text) }
            Text(
                series?.title ?: "",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.shiftHistory(1) }, enabled = vm.canShiftHistoryForward) {
                Text("›", fontSize = 22.sp, color = if (vm.canShiftHistoryForward) Palette.Text else Palette.Grid)
            }
        }

        when {
            vm.historyUnavailable -> Caption("Manca il permesso per leggere questa statistica.")
            series == null -> Caption(if (vm.historyLoading) "Caricamento…" else "Nessun dato.")
            series.values.all { it == null || it == 0.0 } -> Caption("Nessun dato in questo periodo.")
            else -> {
                val goal = if (metric == Metric.STEPS) s.stepGoal.toDouble() else null
                BarChart(
                    values = series.values,
                    color = metric.color,
                    labels = series.labels,
                    formatAxis = { fmtAxis(it, metric.decimals) },
                    highlight = series.highlight,
                    selected = sel,
                    goal = goal,
                    maxBarWidth = if (series.period == HistoryPeriod.MONTH) 12.dp else 30.dp,
                    height = 160.dp,
                    onSelect = { sel = it },
                )
                val unit = metric.unit
                val shown = sel
                if (shown != null && series.values[shown] != null) {
                    Caption(
                        "${series.longLabels[shown]} · ${fmtValue(series.values[shown]!!, metric)} $unit" +
                            if (series.perDayNote) " al giorno" else "",
                        Palette.Text,
                    )
                } else {
                    Caption(
                        "Tocca una barra per il valore" +
                            if (series.perDayNote) " · ogni barra è la media giornaliera del mese" else ""
                    )
                }

                val present = series.values.filterNotNull().filter { metric.aggregation == Aggregation.SUM || it > 0 }
                if (present.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (metric.aggregation == Aggregation.SUM && series.period != HistoryPeriod.YEAR) {
                            Tile(fmtValue(present.sum(), metric), "totale $unit", metric.color, Modifier.weight(1f))
                        }
                        Tile(fmtValue(present.average(), metric), "media $unit", metric.color, Modifier.weight(1f))
                        val maxI = series.values.indices.maxByOrNull { series.values[it] ?: Double.MIN_VALUE }
                        if (maxI != null) {
                            Tile(fmtValue(series.values[maxI] ?: 0.0, metric), "massimo", metric.color, Modifier.weight(1f))
                        }
                        if (goal != null && series.period != HistoryPeriod.YEAR) {
                            Tile("${present.count { it >= goal }}", "a obiettivo", metric.color, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (!vm.hasHistoryPermission) {
            Caption("Per i dati di oltre 30 giorni fa attiva l'accesso allo storico in Personalizza.")
        }
    }
}

@Composable
private fun TipsCard(c: CoachResult) {
    Card("Il parere di Polso", Palette.Hrv) {
        c.tips.forEachIndexed { i, t ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(t.color.copy(alpha = 0.10f))
                    .border(1.dp, t.color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.color)
                Spacer(Modifier.height(4.dp))
                Text(t.text, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.Text.copy(alpha = 0.9f))
            }
        }
        c.score?.let { sc ->
            Caption(
                "Indice di forma ${(sc * 100).toInt()}%" +
                    (c.recentScore?.let { " · ultimi 7 giorni ${(it * 100).toInt()}%" } ?: "") +
                    " · su ${c.daysOfData} giorni di dati",
            )
        }
    }
}

@Composable
private fun HeartCard(d: DayData, h: HeartInfo) {
    val restingToday = d.resting?.lastOrNull { it.time.atZone(zone).toLocalDate() == d.day }
    Card("Battito", Palette.Heart) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("${h.min}", "min bpm", Palette.Heart, Modifier.weight(1f))
            Tile("${h.avg}", "media bpm", Palette.Heart, Modifier.weight(1f))
            Tile("${h.max}", "max bpm", Palette.Heart, Modifier.weight(1f))
            restingToday?.let { Tile("${it.value.toLong()}", "a riposo", Palette.Resting, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(16.dp))
        ZoomableHeartChart(d, h)
        d.resting?.takeIf { it.size >= 2 }?.let { list ->
            Spacer(Modifier.height(16.dp))
            Text("Battito a riposo, 30 giorni", fontSize = 12.sp, color = Palette.Resting)
            Spacer(Modifier.height(6.dp))
            Sparkline(list, Palette.Resting)
        }
    }
}

/**
 * Battito delle 24 ore ingrandibile: due dita per lo zoom (centrato fra le
 * dita), un dito in orizzontale per scorrere quando è ingrandito, doppio
 * tocco per tornare alla giornata intera.
 */
@Composable
private fun ZoomableHeartChart(d: DayData, h: HeartInfo) {
    var scale by remember(d.day) { mutableFloatStateOf(1f) }
    var start by remember(d.day) { mutableFloatStateOf(0f) }
    val maxScale = 48f // finestra minima: 30 minuti

    fun clampStart(s: Float, sc: Float) = s.coerceIn(0f, (1f - 1f / sc).coerceAtLeast(0f))

    val source = if (scale >= 3f) h.fine else h.bins
    val pts = source.map { Duration.between(d.dayStart, it.time).seconds / 86_400f to it.value.toFloat() }
    val end = start + 1f / scale
    val visible = pts.filter { it.first in start..end }
    val lo: Float
    val hi: Float
    if (scale > 1f && visible.isNotEmpty()) {
        val vMin = visible.minOf { it.second }
        val vMax = visible.maxOf { it.second }
        val pad = ((vMax - vMin) * 0.15f).coerceAtLeast(3f)
        lo = vMin - pad
        hi = vMax + pad
    } else {
        lo = (h.min - 5).toFloat()
        hi = (h.max + 5).toFloat()
    }

    var lastTap by remember { mutableStateOf(0L) }
    // posizione del cursore come frazione della giornata (null = nascosto)
    var cursorX by remember(d.day) { mutableStateOf<Float?>(null) }

    fun toDataX(px: Float, width: Float) = (start + (px / width).coerceIn(0f, 1f) / scale).coerceIn(0f, 1f)

    val gesture = Modifier.pointerInput(d.day) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = size.width.toFloat().coerceAtLeast(1f)
            val slop = viewConfiguration.touchSlop

            // Fase 1 (400 ms): capire che gesto è.
            // null = dito fermo a lungo → cursore trascinabile
            val kind: String? = withTimeoutOrNull(400L) {
                var result: String? = null
                while (result == null) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.count { it.pressed }
                    val drift = ev.changes.first().position - down.position
                    result = when {
                        pressed >= 2 -> "multi"
                        pressed == 0 -> "tap"
                        abs(drift.x) > slop && abs(drift.x) > abs(drift.y) -> "hdrag"
                        abs(drift.y) > slop -> "vdrag"
                        else -> null
                    }
                }
                result
            }

            when (kind) {
                "tap" -> {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastTap < 350) {
                        // doppio tocco: giornata intera, cursore via
                        scale = 1f
                        start = 0f
                        cursorX = null
                        lastTap = 0L
                    } else {
                        cursorX = toDataX(down.position.x, width)
                        lastTap = now
                    }
                }

                "vdrag" -> Unit // lascia scorrere la pagina

                null, "hdrag" -> {
                    if (kind == "hdrag" && scale > 1f) {
                        // ingrandito: un dito in orizzontale scorre la finestra
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } >= 2) break
                            val pan = ev.calculatePan()
                            start = clampStart(start - pan.x / width / scale, scale)
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                    } else {
                        // cursore che segue il dito
                        var pos = down.position.x
                        cursorX = toDataX(pos, width)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.pressed } ?: break
                            pos = ch.position.x
                            cursorX = toDataX(pos, width)
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                    }
                }
            }

            if (kind == "multi" || currentEvent.changes.count { it.pressed } >= 2) {
                // zoom con due dita (centrato fra le dita) + spostamento
                do {
                    val ev = awaitPointerEvent()
                    if (ev.changes.count { it.pressed } >= 2) {
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        val c = (ev.calculateCentroid(useCurrent = true).x / width).coerceIn(0f, 1f)
                        val focus = start + c / scale
                        val newScale = (scale * zoom).coerceIn(1f, maxScale)
                        val newStart = focus - c / newScale - pan.x / width / newScale
                        scale = newScale
                        start = clampStart(newStart, newScale)
                    }
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
            }
        }
    }

    // punto più vicino al cursore
    val cursorPoint = cursorX?.let { cx ->
        pts.minByOrNull { abs(it.first - cx) }?.takeIf { it.first in start..end }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        LineChart(
            pts,
            Palette.Heart,
            lo,
            hi,
            height = 170.dp,
            gap = if (source === h.fine) 10f / 1440f else 30f / 1440f,
            dots = scale >= 12f,
            modifier = gesture,
            viewStart = start,
            viewScale = scale,
            timeGrid = true,
            cursor = cursorPoint,
        )
        if (cursorPoint != null) {
            val minutes = (cursorPoint.first * 1440).toInt().coerceIn(0, 1439)
            val tipW = 92.dp
            val rel = (cursorPoint.first - start) * scale
            val x = (maxWidth * rel - tipW / 2).coerceIn(0.dp, maxWidth - tipW)
            Column(
                Modifier
                    .offset(x = x, y = 4.dp)
                    .width(tipW)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Bg.copy(alpha = 0.92f))
                    .border(1.dp, Palette.Heart.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "%02d:%02d".format(minutes / 60, minutes % 60),
                    fontSize = 11.sp,
                    color = Palette.Muted,
                )
                Text(
                    "${cursorPoint.second.toInt()} bpm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Palette.Heart,
                )
            }
        }
    }
    HourAxis(start, scale)

    val fromMin = (start * 1440).toInt()
    val toMin = (end * 1440).toInt().coerceAtMost(1440)
    if (scale > 1.01f) {
        val vMin = visible.minOfOrNull { it.second }?.toInt()
        val vMax = visible.maxOfOrNull { it.second }?.toInt()
        Caption(
            "%02d:%02d–%02d:%02d".format(fromMin / 60, fromMin % 60, toMin / 60, toMin % 60) +
                (if (vMin != null) " · min $vMin · max $vMax bpm" else " · nessuna misura") +
                " · doppio tocco per le 24 ore",
            Palette.Text,
        )
    } else {
        Caption("Tocca o tieni premuto per leggere ora e bpm · allarga con due dita per il dettaglio")
    }
}

@Composable
private fun Sparkline(list: List<TimedValue>, color: Color) {
    val lo = list.minOf { it.value }.toFloat()
    val hi = list.maxOf { it.value }.toFloat()
    val pad = ((hi - lo) * 0.15f).coerceAtLeast(1f)
    val first = list.first().time
    val span = Duration.between(first, list.last().time).seconds.coerceAtLeast(1L).toFloat()
    val pts = list.map { Duration.between(first, it.time).seconds / span to it.value.toFloat() }
    val dm = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    LineChart(pts, color, lo - pad, hi + pad, height = 70.dp, gridColumns = 6, dots = list.size <= 31)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(list.first().time.atZone(zone).format(dm), fontSize = 10.sp, color = Palette.Muted)
        Text("min ${lo.toInt()} · max ${hi.toInt()}", fontSize = 10.sp, color = Palette.Muted)
        Text(list.last().time.atZone(zone).format(dm), fontSize = 10.sp, color = Palette.Muted)
    }
}

private fun asleepDuration(s: SleepInfo): Duration {
    val awake = s.stages
        .filter { sleepLevel(it.type) == SleepLevel.AWAKE }
        .fold(Duration.ZERO) { acc, seg -> acc.plus(Duration.between(seg.start, seg.end)) }
    return Duration.between(s.start, s.end).minus(awake)
}

private fun fmtHours(d: Duration): String {
    val m = d.toMinutes()
    return "${m / 60}h ${"%02d".format(m % 60)}"
}

@Composable
private fun SleepCard(s: SleepInfo) {
    val total = Duration.between(s.start, s.end)
    val byLevel = s.stages
        .groupBy { sleepLevel(it.type) }
        .mapValues { (_, segs) -> segs.fold(Duration.ZERO) { acc, seg -> acc.plus(Duration.between(seg.start, seg.end)) } }
    Card("Sonno", Palette.Sleep) {
        Row(verticalAlignment = Alignment.Bottom) {
            Stat(fmtDuration(asleepDuration(s)), "dormito", Palette.Sleep, big = true)
            Spacer(Modifier.weight(1f))
            Stat("${s.start.atZone(zone).format(hm)}–${s.end.atZone(zone).format(hm)}", "a letto")
        }
        Spacer(Modifier.height(16.dp))
        Hypnogram(s)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(s.start.atZone(zone).format(hm), fontSize = 10.sp, color = Palette.Muted)
            Text(s.end.atZone(zone).format(hm), fontSize = 10.sp, color = Palette.Muted)
        }
        if (byLevel.size > 1) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SleepLevel.values().forEach { lvl ->
                    val dur = byLevel[lvl] ?: Duration.ZERO
                    val pct = if (total.seconds > 0) dur.seconds * 100 / total.seconds else 0
                    Tile(fmtHours(dur), "${lvl.label} $pct%", lvl.color, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HrvCard(d: DayData, list: List<TimedValue>) {
    val today = list.filter { it.time.atZone(zone).toLocalDate() == d.day }
    val avg30 = list.map { it.value }.average()
    Card("Variabilità cardiaca (HRV)", Palette.Hrv) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (today.isNotEmpty()) {
                Tile(fmtDec(today.map { it.value }.average(), 0) + " ms", "media del giorno", Palette.Hrv, Modifier.weight(1f))
                Tile("${today.size}", if (today.size == 1) "misura" else "misure", Palette.Hrv, Modifier.weight(1f))
            } else {
                Tile(fmtDec(list.last().value, 0) + " ms", "ultima misura", Palette.Hrv, Modifier.weight(1f))
            }
            Tile(fmtDec(avg30, 0) + " ms", "media 30 giorni", Palette.Hrv, Modifier.weight(1f))
        }
        if (today.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Nel corso della giornata", fontSize = 12.sp, color = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            val lo = today.minOf { it.value }
            val hi = today.maxOf { it.value }
            val pad = ((hi - lo) * 0.2).coerceAtLeast(5.0)
            val pts = today.map { Duration.between(d.dayStart, it.time).seconds / 86_400f to it.value.toFloat() }
            LineChart(
                pts, Palette.Hrv, (lo - pad).toFloat(), (hi + pad).toFloat(),
                height = 110.dp, gap = 90f / 1440f, gridColumns = 8, dots = true,
            )
            HourAxis()
            Caption("ms · scala ${(lo - pad).toInt()}–${(hi + pad).toInt()}")
        } else {
            Caption("Nessuna misura in questa giornata: Huawei di solito la rileva durante il sonno.")
        }
        if (list.size >= 2) {
            Spacer(Modifier.height(16.dp))
            Text("Andamento 30 giorni", fontSize = 12.sp, color = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            Sparkline(list, Palette.Hrv)
        }
    }
}

@Composable
private fun TrendCard(title: String, color: Color, list: List<TimedValue>, unit: String, decimals: Int) {
    val last = list.last()
    val avg = list.map { it.value }.average()
    Card(title, color) {
        Row(verticalAlignment = Alignment.Bottom) {
            Stat(
                fmtDec(last.value, decimals) + " $unit",
                "ultima · " + last.time.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ITALIAN)),
                color,
                big = true,
            )
            Spacer(Modifier.weight(1f))
            Stat(fmtDec(avg, decimals) + " $unit", "media 30 giorni")
        }
        if (list.size >= 2) {
            Spacer(Modifier.height(14.dp))
            Sparkline(list, color)
        }
    }
}

@Composable
private fun DayValuesCard(title: String, color: Color, list: List<TimedValue>, unit: String, d: DayData) {
    val lo = list.minOf { it.value }
    val hi = list.maxOf { it.value }
    Card(title, color) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(fmtDec(list.map { it.value }.average(), 0) + " $unit", "media", color, Modifier.weight(1f))
            Tile(fmtDec(lo, 0), "min", color, Modifier.weight(1f))
            Tile(fmtDec(hi, 0), "max", color, Modifier.weight(1f))
        }
        if (list.size >= 2) {
            Spacer(Modifier.height(14.dp))
            val pts = list.map { Duration.between(d.dayStart, it.time).seconds / 86_400f to it.value.toFloat() }
            val pad = ((hi - lo) * 0.2).coerceAtLeast(1.0)
            LineChart(pts, color, (lo - pad).toFloat(), (hi + pad).toFloat(), height = 80.dp, gap = 90f / 1440f, gridColumns = 8, dots = true)
            HourAxis()
        }
    }
}

@Composable
private fun WorkoutsCard(list: List<ExerciseInfo>) {
    Card("Allenamenti", Palette.Workout) {
        list.forEachIndexed { i, e ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Workout.copy(alpha = 0.10f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${e.start.atZone(zone).format(hm)}–${e.end.atZone(zone).format(hm)}",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
                Text(fmtDuration(Duration.between(e.start, e.end)), color = Palette.Workout, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Footer(
    vm: DashboardViewModel,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Button(
            onClick = onExport,
            enabled = !vm.exporting,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Hrv, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.exporting) "Preparo i dati…" else "Esporta i dati per un'analisi") }
        Spacer(Modifier.height(12.dp))
        Text(
            "I dati arrivano da Connessione Salute (Huawei Health → Health Sync). " +
                "Lo stress non è disponibile: Connessione Salute non lo prevede. " +
                "Le sezioni senza dati non vengono mostrate.",
            fontSize = 11.sp,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                Text("Aggiorna", color = Palette.Text)
            }
            OutlinedButton(onClick = onOpenHealthConnect, modifier = Modifier.weight(1f)) {
                Text("Conn. Salute", color = Palette.Text)
            }
        }
        if (vm.missingPermissions) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Concedi i permessi mancanti", color = Palette.Text)
            }
        }
    }
}

private fun fmtDec(v: Double, decimals: Int): String =
    String.format(Locale.ITALY, "%.${decimals}f", v)

fun fmtValue(v: Double, metric: Metric): String =
    if (metric.decimals == 0) fmtInt(Math.round(v)) else fmtDec(v, metric.decimals)
