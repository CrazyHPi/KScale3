package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.ui.theme.LocalKScaleExtraColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** 趋势图数据点（升序）。 */
data class TrendPoint(val at: Instant, val value: Double)

/** 列表 → 趋势点（按时间升序）；[valueOf] 选择体重或体脂。 */
fun List<Measurement>.toTrendPoints(valueOf: (Measurement) -> Double): List<TrendPoint> =
    sortedBy { it.measuredAt }.map { TrendPoint(it.measuredAt, valueOf(it)) }

/**
 * 单序列趋势折线（设计稿 §2.5）：
 * 三条浅色参考线、末点直标、其余小圆点、点击查看数值。
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    unit: String,
    decimals: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 150.dp,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val valueStyle =
        TextStyle(color = lineColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val axisStyle = TextStyle(color = mutedColor, fontSize = 9.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    val leftPad = 34.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val plotW = size.width - leftPad - rightPad
                    val xs = points.map { p -> leftPad + fractionX(points, p) * plotW }
                    val nearest =
                        xs.indices.minByOrNull { i -> abs(xs[i] - tap.x) }
                    if (nearest != null && abs(xs[nearest] - tap.x) <= 24.dp.toPx()) {
                        selectedIndex = if (selectedIndex == nearest) null else nearest
                    }
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val leftPad = 34.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 18.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad

        val values = points.map { it.value }
        val (lo, hi) = niceBounds(values.min(), values.max())
        fun yOf(value: Double): Float =
            (topPad + plotH * (1.0 - (value - lo) / (hi - lo))).toFloat()

        fun xOf(p: TrendPoint): Float = leftPad + fractionX(points, p) * plotW

        // 三条水平参考线 + y 轴刻度
        val ticks = listOf(lo, (lo + hi) / 2, hi)
        for (t in ticks) {
            val y = yOf(t)
            drawLine(
                gridColor.copy(alpha = 0.45f),
                Offset(leftPad, y),
                Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = t.rounded(1)
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                measured,
                topLeft = Offset(
                    leftPad - measured.size.width - 6.dp.toPx(),
                    y - measured.size.height / 2f,
                ),
            )
        }

        // x 轴：首/末日期
        val timeFmt: (Instant) -> String = { instant ->
            val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.monthNumber.toString().padStart(2, '0')}-${
                dt.dayOfMonth.toString().padStart(2, '0')
            }"
        }
        val xLabels =
            if (points.size == 1) listOf(points.first()) else listOf(points.first(), points.last())
        xLabels.forEachIndexed { index, p ->
            val measured = textMeasurer.measure(timeFmt(p.at), axisStyle)
            val rawX = xOf(p) - measured.size.width / 2f
            val x = if (xLabels.size == 1) (size.width - measured.size.width) / 2f
            else if (index == 0) rawX.coerceAtLeast(0f)
            else rawX.coerceAtMost(size.width - measured.size.width)
            drawText(
                measured,
                topLeft = Offset(x, size.height - bottomPad + 6.dp.toPx()),
            )
        }

        // 面积渐变
        val area = Path().apply {
            moveTo(xOf(points.first()), yOf(points.first().value))
            for (p in points) lineTo(xOf(p), yOf(p.value))
            lineTo(xOf(points.last()), topPad + plotH)
            lineTo(xOf(points.first()), topPad + plotH)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
                startY = topPad,
                endY = topPad + plotH,
            ),
        )

        // 折线
        val line = Path().apply {
            moveTo(xOf(points.first()), yOf(points.first().value))
            for (p in points) lineTo(xOf(p), yOf(p.value))
        }
        drawPath(
            line,
            color = lineColor,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 其余点小圆点
        for (p in points.dropLast(1)) {
            drawCircle(lineColor, radius = 2.5.dp.toPx(), center = Offset(xOf(p), yOf(p.value)))
        }

        // 末点：白底主色描边 + 数值直标
        val last = points.last()
        val lastX = xOf(last)
        val lastY = yOf(last.value)
        drawCircle(surfaceColor, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(
            lineColor,
            radius = 5.dp.toPx(),
            center = Offset(lastX, lastY),
            style = Stroke(width = 2.4.dp.toPx()),
        )
        val lastLabel = last.value.rounded(decimals)
        val measuredLast = textMeasurer.measure(lastLabel, valueStyle)
        val labelX = (lastX - measuredLast.size.width / 2f)
            .coerceIn(0f, size.width - measuredLast.size.width)
        val labelY = (lastY - measuredLast.size.height - 9.dp.toPx())
            .coerceAtLeast(0f)
        drawText(measuredLast, topLeft = Offset(labelX, labelY))

        // 点按选中：高亮 + tooltip
        selectedIndex?.let { idx ->
            val p = points.getOrNull(idx) ?: return@let
            val x = xOf(p)
            val y = yOf(p.value)
            drawCircle(surfaceColor, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(
                lineColor,
                radius = 5.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 2.4.dp.toPx()),
            )
            val tip = "${p.value.rounded(decimals)} $unit · ${timeFmt(p.at)}"
            val measured = textMeasurer.measure(
                tip,
                TextStyle(color = onSurfaceColor, fontSize = 10.sp),
            )
            val tx = (x - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)
            val ty = (y - measured.size.height - 16.dp.toPx()).coerceAtLeast(0f)
            drawText(measured, topLeft = Offset(tx, ty))
        }
    }
}

/** 体脂环（记录详情）：270° 弧 + 中心数值。 */
@Composable
fun FatRing(
    fatPct: Double,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.08f
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = (fatPct / 45.0).coerceIn(0.0, 1.0).toFloat() * 270f
            drawArc(
                color = ringColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "${fatPct.rounded(1)}%",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 心率 ECG 折线（中性墨色，不给正常生命体征上报警色）。 */
@Composable
fun HeartRateSparkline(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val h = size.height
        val w = size.width
        val ys = listOf(0.50f, 0.50f, 0.36f, 0.72f, 0.42f, 0.52f, 0.60f, 0.50f, 0.44f, 0.66f, 0.50f)
        val path = Path()
        ys.forEachIndexed { i, f ->
            val x = w * i / (ys.size - 1).toFloat()
            val y = h * f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** 体脂健康带：底色轨道 + 健康区间 + 当前值标记。 */
@Composable
fun FatHealthBand(
    fatPct: Double,
    healthyMin: Int,
    healthyMax: Int,
    markerColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val healthyColor = LocalKScaleExtraColors.current.success.copy(alpha = 0.35f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val domainMin = 5.0
        val domainMax = 40.0
        fun frac(v: Double): Float =
            ((v - domainMin) / (domainMax - domainMin)).coerceIn(0.0, 1.0).toFloat()

        val trackH = 6.dp.toPx()
        val top = (size.height - trackH) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val left = size.width * frac(healthyMin.toDouble())
        val right = size.width * frac(healthyMax.toDouble())
        drawRoundRect(
            color = healthyColor,
            topLeft = Offset(left, top),
            size = Size(right - left, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val markerX = size.width * frac(fatPct)
        drawCircle(surfaceColor, radius = 6.dp.toPx(), center = Offset(markerX, size.height / 2f))
        drawCircle(
            markerColor,
            radius = 6.dp.toPx(),
            center = Offset(markerX, size.height / 2f),
            style = Stroke(width = 2.2.dp.toPx()),
        )
    }
}

/** x 归一化：单点居中，多点按时间跨度。 */
private fun fractionX(points: List<TrendPoint>, p: TrendPoint): Float {
    if (points.size == 1) return 0.5f
    val t0 = points.first().at.toEpochMilliseconds().toDouble()
    val t1 = points.last().at.toEpochMilliseconds().toDouble()
    if (t1 <= t0) return 0.5f
    return ((p.at.toEpochMilliseconds() - t0) / (t1 - t0)).toFloat()
}

/** y 轴干净边界：0.5 步进取整，序列近似恒定时给 ±1 视窗。 */
private fun niceBounds(min: Double, max: Double): Pair<Double, Double> {
    if (max - min < 1.0) {
        val mid = (min + max) / 2
        return floor(mid - 1.0) to ceil(mid + 1.0)
    }
    val pad = (max - min) * 0.15
    return (floor((min - pad) * 2) / 2) to (ceil((max + pad) * 2) / 2)
}
