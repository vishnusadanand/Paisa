package com.visa.paisa.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class Slice(val label: String, val value: Double, val color: Color)

/**
 * A donut rather than a full pie: the hole carries the total, which is the number you
 * actually want, and thin arcs are easier to compare than wedges meeting at a point.
 * Slices under 1.5% are merged so the ring does not turn into a comb of hairlines.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 58f,
) {
    val total = slices.sumOf { it.value }
    val animated by animateFloatAsState(
        targetValue = if (total > 0) 1f else 0f,
        animationSpec = tween(700),
        label = "donut",
    )

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val diameter = max(0f, minOf(size.width, size.height) - strokeWidth)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            if (total <= 0.0) {
                drawArc(
                    color = Color(0x1A000000),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                return@Canvas
            }

            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total).toFloat() * 360f * animated
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = sweep - GAP.coerceAtMost(sweep / 3f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private const val GAP = 2.5f

@Composable
fun ChartLegend(
    slices: List<Slice>,
    onSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.forEach { slice ->
            val share = slice.value / total
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(slice.label) }
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(share * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = com.visa.paisa.util.Money.rupees(slice.value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Daily spend across the month. Bars, not a line: spending is a set of discrete events,
 * and a line implies you were spending continuously between them.
 */
@Composable
fun DailyBarChart(
    values: List<Pair<Int, Double>>,
    daysInMonth: Int,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val byDay = values.toMap()
    val maxValue = values.maxOfOrNull { it.second } ?: 0.0
    val animated by animateFloatAsState(
        targetValue = if (maxValue > 0) 1f else 0f,
        animationSpec = tween(600),
        label = "bars",
    )
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        if (daysInMonth <= 0) return@Canvas
        val slot = size.width / daysInMonth
        val barWidth = (slot * 0.62f).coerceAtLeast(2f)
        val baseline = size.height

        for (day in 1..daysInMonth) {
            val value = byDay[day] ?: 0.0
            val fraction = if (maxValue > 0) (value / maxValue).toFloat() else 0f
            val height = (fraction * baseline * 0.94f * animated).coerceAtLeast(if (value > 0) 3f else 1.5f)
            val left = slot * (day - 1) + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (value > 0) barColor else emptyColor,
                topLeft = Offset(left, baseline - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2.5f),
            )
        }
    }
}

/** Six-month trend strip shown under the donut. */
@Composable
fun TrendBars(
    values: List<Pair<String, Double>>,
    highlightIndex: Int,
    modifier: Modifier = Modifier,
) {
    val maxValue = values.maxOfOrNull { it.second } ?: 0.0
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, (label, value) ->
            val fraction = if (maxValue > 0) (value / maxValue).toFloat() else 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = com.visa.paisa.util.Money.compact(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((10 + 58 * fraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == highlightIndex) primary else muted),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Budget progress: a single bar that turns red once the cap is crossed. */
@Composable
fun BudgetBar(
    spent: Double,
    cap: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = if (cap > 0) (spent / cap).toFloat().coerceIn(0f, 1f) else 0f
    val over = cap > 0 && spent > cap
    val animated by animateFloatAsState(fraction, tween(500), label = "budget")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(4.dp))
                .background(if (over) MaterialTheme.colorScheme.error else color),
        )
    }
}
