package br.com.vigiaterritorio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import br.com.vigiaterritorio.domain.usecase.SeriesPoint
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import kotlin.math.max

enum class ChartStyle { BARS, LINE }

/**
 * Série temporal com previsão e limiar. Eixo X em horas relativas a agora (−72 … +72).
 * Observado = cor primária sólida; previsto = mesma cor clara/tracejada; limiar = linha de atenção.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TrendChart(
    observed: List<SeriesPoint>,
    forecast: List<SeriesPoint>,
    threshold: Double?,
    thresholdLabel: String,
    unit: String,
    style: ChartStyle,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val limit = VigiaTheme.status.attention
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = labelColor)

    val all = observed + forecast
    val minX = (all.minOfOrNull { it.hoursFromNow } ?: -72.0).coerceAtMost(0.0)
    val maxX = (all.maxOfOrNull { it.hoursFromNow } ?: 6.0).coerceAtLeast(1.0)
    val maxY = max(all.maxOfOrNull { it.value } ?: 1.0, threshold ?: 0.0).coerceAtLeast(1.0) * 1.15

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            val left = 40.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            val top = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            fun x(h: Double) = left + ((h - minX) / (maxX - minX)).toFloat() * (right - left)
            fun y(v: Double) = bottom - (v / maxY).toFloat() * (bottom - top)

            // eixos
            drawLine(axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
            drawLine(axis, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
            listOf(0.0, maxY / 2, maxY / 1.15).forEach { v ->
                val text = measurer.measure(v.formatOne(), labelStyle)
                drawText(text, topLeft = Offset(left - text.size.width - 4.dp.toPx(), y(v) - text.size.height / 2))
            }
            listOf(minX, 0.0, maxX).distinct().forEach { h ->
                val label = when {
                    h == 0.0 -> "agora"
                    h < 0 -> "${h.toInt()} h"
                    else -> "+${h.toInt()} h"
                }
                val text = measurer.measure(label, labelStyle)
                val tx = (x(h) - text.size.width / 2).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(tx, bottom + 4.dp.toPx()))
            }

            // linha do "agora"
            drawLine(
                axis, Offset(x(0.0), top), Offset(x(0.0), bottom),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )

            when (style) {
                ChartStyle.BARS -> {
                    val barWidth = ((right - left) / ((maxX - minX).toFloat() + 1f) * 0.8f).coerceAtLeast(1f)
                    observed.forEach { p ->
                        drawRect(primary, Offset(x(p.hoursFromNow) - barWidth / 2, y(p.value)), Size(barWidth, bottom - y(p.value)))
                    }
                    forecast.forEach { p ->
                        drawRect(primary.copy(alpha = 0.35f), Offset(x(p.hoursFromNow) - barWidth / 2, y(p.value)), Size(barWidth, bottom - y(p.value)))
                    }
                }
                ChartStyle.LINE -> {
                    fun path(points: List<SeriesPoint>) = Path().apply {
                        points.forEachIndexed { i, p ->
                            if (i == 0) moveTo(x(p.hoursFromNow), y(p.value)) else lineTo(x(p.hoursFromNow), y(p.value))
                        }
                    }
                    drawPath(path(observed), primary, style = Stroke(width = 2.dp.toPx()))
                    drawPath(
                        path(forecast), primary.copy(alpha = 0.7f),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
            }

            threshold?.let { t ->
                drawLine(limit, Offset(left, y(t)), Offset(right, y(t)), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.lg)) {
            LegendItem(primary, "Observado ($unit)")
            LegendItem(primary.copy(alpha = 0.35f), if (style == ChartStyle.BARS) "Previsto" else "Projeção")
            if (threshold != null) LegendItem(limit, thresholdLabel)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
        Box(Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
