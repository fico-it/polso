package it.fico.polso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Linea su asse x normalizzato 0..1. Interrompe il tratto quando tra due
 * punti c'è un salto più grande di [gap].
 */
@Composable
fun LineChart(
    points: List<Pair<Float, Float>>,
    color: Color,
    minY: Float,
    maxY: Float,
    height: Dp = 140.dp,
    gap: Float = 1f,
    gridColumns: Int = 4,
    dots: Boolean = false,
    modifier: Modifier = Modifier,
    viewStart: Float = 0f,
    viewScale: Float = 1f,
    timeGrid: Boolean = false,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = (x - viewStart) * viewScale * w

        if (timeGrid) {
            val (labelStep, _) = timeTicks(viewScale)
            var m = 0
            while (m <= 1440) {
                val x = px(m / 1440f)
                if (x in -1f..w + 1f) {
                    drawLine(Palette.Grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                m += labelStep
            }
        } else {
            for (i in 0..gridColumns) {
                val x = w * i / gridColumns
                drawLine(Palette.Grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
        }
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(Palette.Grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (points.isEmpty()) return@Canvas
        val range = (maxY - minY).takeIf { it > 0f } ?: 1f
        fun py(v: Float) = h - (v - minY) / range * h
        val path = Path()
        var prevX = -10f
        points.forEachIndexed { i, (x, y) ->
            val p = px(x)
            if (i == 0 || x - prevX > gap) path.moveTo(p, py(y)) else path.lineTo(p, py(y))
            prevX = x
        }
        clipRect(0f, 0f, w, h) {
            drawPath(
                path,
                color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (dots || points.size == 1) {
                points.forEach { (x, y) ->
                    val p = px(x)
                    if (p in -4f..w + 4f) drawCircle(color, 3.dp.toPx(), Offset(p, py(y)))
                }
            }
        }
    }
}

/** Passo delle etichette e delle tacche (in minuti) in base allo zoom. */
fun timeTicks(scale: Float): Pair<Int, Int> {
    val visible = 1440f / scale
    val options = listOf(5 to 1, 10 to 5, 15 to 5, 30 to 10, 60 to 15, 120 to 30, 180 to 60)
    return options.firstOrNull { it.first >= visible / 8f } ?: options.last()
}

/**
 * Asse orario fine sotto i grafici delle 24 ore. Segue lo zoom: con la
 * giornata intera tacca ogni ora ed etichetta ogni 3 ore, ingrandendo
 * scende fino a tacche di un minuto ed etichette ogni 5 minuti.
 */
@Composable
fun HourAxis(viewStart: Float = 0f, viewScale: Float = 1f) {
    val (labelStep, minorStep) = timeTicks(viewScale)
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val w = size.width
            var m = 0
            while (m <= 1440) {
                val x = (m / 1440f - viewStart) * viewScale * w
                if (x in -1f..w + 1f) {
                    val major = m % labelStep == 0
                    val len = when {
                        major && m % (labelStep * 2) == 0 -> 8.dp.toPx()
                        major -> 6.dp.toPx()
                        else -> 3.dp.toPx()
                    }
                    drawLine(
                        if (major) Palette.GridHi else Palette.Grid,
                        Offset(x.coerceIn(0.5f, w - 0.5f), 0f),
                        Offset(x.coerceIn(0.5f, w - 0.5f), len),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                m += minorStep
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val withMinutes = labelStep < 60
            val labelW = if (withMinutes) 34.dp else 18.dp
            var m = 0
            while (m <= 1440) {
                val f = (m / 1440f - viewStart) * viewScale
                if (f in -0.001f..1.001f) {
                    val x = (maxWidth * f - labelW / 2).coerceIn(0.dp, maxWidth - labelW)
                    val text = if (withMinutes) "%02d:%02d".format(m / 60, m % 60) else "%02d".format(m / 60)
                    Text(
                        text = text,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (m % (labelStep * 2) == 0) Palette.Text.copy(alpha = 0.75f) else Palette.Muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .width(labelW)
                            .offset(x = x),
                    )
                }
                m += labelStep
            }
        }
    }
}

enum class SleepLevel(val label: String, val color: Color) {
    AWAKE("Sveglio", Palette.Awake),
    REM("REM", Palette.Rem),
    LIGHT("Leggero", Palette.Light),
    DEEP("Profondo", Palette.Deep),
}

fun sleepLevel(type: Int): SleepLevel = when (type) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepLevel.AWAKE
    SleepSessionRecord.STAGE_TYPE_REM -> SleepLevel.REM
    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepLevel.DEEP
    else -> SleepLevel.LIGHT
}

/** Ipnogramma: una fascia per ciascuna fase. */
@Composable
fun Hypnogram(sleep: SleepInfo) {
    val totalSec = Duration.between(sleep.start, sleep.end).seconds.coerceAtLeast(1L).toFloat()
    val rowHeight = 30.dp
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(64.dp)) {
            SleepLevel.values().forEach {
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(it.label, fontSize = 11.sp, color = it.color)
                }
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(rowHeight * SleepLevel.values().size)
        ) {
            val w = size.width
            val rh = size.height / SleepLevel.values().size
            for (i in 0..SleepLevel.values().size) {
                drawLine(Palette.Grid, Offset(0f, rh * i), Offset(w, rh * i), strokeWidth = 1f)
            }
            sleep.stages.forEach { seg ->
                val lvl = sleepLevel(seg.type)
                val x1 = Duration.between(sleep.start, seg.start).seconds / totalSec * w
                val x2 = Duration.between(sleep.start, seg.end).seconds / totalSec * w
                drawRoundRect(
                    color = lvl.color,
                    topLeft = Offset(x1, rh * lvl.ordinal + rh * 0.18f),
                    size = Size((x2 - x1).coerceAtLeast(1.5f), rh * 0.64f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

// ---------- formattazione ----------

fun fmtInt(v: Long): String = String.format(Locale.ITALY, "%,d", v)

fun compact(v: Long): String = when {
    v >= 10_000 -> String.format(Locale.ITALY, "%.0fk", v / 1000.0)
    v >= 1_000 -> String.format(Locale.ITALY, "%.1fk", v / 1000.0)
    else -> v.toString()
}

fun fmtDuration(d: Duration): String {
    val m = d.toMinutes()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
