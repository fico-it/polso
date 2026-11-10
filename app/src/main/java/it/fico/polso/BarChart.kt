package it.fico.polso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Grafico a colonne con asse verticale etichettato sulla linea più alta e su
 * quella mediana, linea obiettivo opzionale e selezione al tocco.
 */
@Composable
fun BarChart(
    values: List<Double?>,
    color: Color,
    labels: List<String>,
    formatAxis: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    highlight: Int? = null,
    selected: Int? = null,
    goal: Double? = null,
    maxBarWidth: Dp = 12.dp,
    onSelect: ((Int?) -> Unit)? = null,
) {
    val maxV = values.filterNotNull().maxOrNull() ?: 0.0
    val top = niceCeil(max(maxV, goal ?: 0.0))
    val pad = 6.dp

    Row(modifier.fillMaxWidth()) {
        // asse verticale: massimo, mediana, zero
        Box(
            Modifier
                .width(40.dp)
                .height(height + pad * 2)
                .padding(end = 6.dp)
        ) {
            AxisText(formatAxis(top), Modifier.align(Alignment.TopEnd), strong = true)
            AxisText(formatAxis(top / 2), Modifier.align(Alignment.CenterEnd))
            AxisText("0", Modifier.align(Alignment.BottomEnd))
        }

        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height + pad * 2)
                    .padding(vertical = pad)
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    drawLine(Palette.GridHi, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1.dp.toPx())
                    drawLine(
                        Palette.GridHi.copy(alpha = 0.7f),
                        Offset(0f, h / 2),
                        Offset(w, h / 2),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                    drawLine(Palette.Grid, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
                    if (goal != null && goal > 0) {
                        val y = h - (goal / top).toFloat() * h
                        drawLine(
                            color.copy(alpha = 0.9f),
                            Offset(0f, y),
                            Offset(w, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                }
                Row(Modifier.matchParentSize(), verticalAlignment = Alignment.Bottom) {
                    values.forEachIndexed { i, v ->
                        val isSel = i == selected
                        val isHi = i == highlight
                        val slot = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .let { m ->
                                if (onSelect != null) m.clickable { onSelect(if (isSel) null else i) } else m
                            }
                        Box(slot, contentAlignment = Alignment.BottomCenter) {
                            if (v != null) {
                                val frac = (v / top).toFloat().coerceIn(0f, 1f)
                                val reached = goal != null && v >= goal
                                val brush = when {
                                    v <= 0.0 -> SolidColor(Palette.Track)
                                    isSel -> SolidColor(Palette.Text)
                                    isHi || reached -> Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f)))
                                    else -> Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.3f)))
                                }
                                Box(
                                    Modifier
                                        .widthIn(max = maxBarWidth)
                                        .fillMaxWidth(0.62f)
                                        .fillMaxHeight(frac.coerceAtLeast(0.012f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(brush)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (l.isNotEmpty()) {
                            val on = i == highlight || i == selected
                            Text(
                                text = l,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (on) color else Palette.Muted,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisText(text: String, modifier: Modifier, strong: Boolean = false) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = if (strong) Palette.Text else Palette.Muted,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Anello di avanzamento verso l'obiettivo. */
@Composable
fun GoalRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
    stroke: Dp = 11.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(Palette.Track, -90f, 360f, false, tl, sz, style = Stroke(s))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            }
            if (progress > 1f) {
                // secondo giro oltre l'obiettivo, più chiaro
                val extra = (progress - 1f).coerceAtMost(1f)
                drawArc(
                    Color.White.copy(alpha = 0.55f), -90f, 360f * extra, false, tl, sz,
                    style = Stroke(s * 0.45f, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Pulsante a pillola per scegliere metriche e periodi. */
@Composable
fun Pill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Palette.Track)
            .border(1.dp, if (selected) color else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) color else Palette.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Arrotonda in su a 1, 2, 2,5 o 5 × 10ⁿ, così le etichette dell'asse sono pulite. */
fun niceCeil(v: Double): Double {
    if (v <= 0.0) return 1.0
    val base = 10.0.pow(floor(log10(v)))
    val f = v / base
    val n = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return n * base
}

fun fmtAxis(v: Double, decimals: Int = 0): String = when {
    v >= 10_000 -> String.format(Locale.ITALY, "%.0fk", v / 1000)
    v >= 1_000 -> String.format(Locale.ITALY, "%.1fk", v / 1000).replace(",0k", "k")
    v > 0 && v < 10 && decimals > 0 -> String.format(Locale.ITALY, "%.1f", v).replace(",0", "")
    else -> String.format(Locale.ITALY, "%.0f", v)
}
