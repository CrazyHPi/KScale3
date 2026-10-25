package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 体重趋势折线图（自绘 Canvas；dataviz 规范落地）：
 * - 单序列 → 免图例，标题即系列名；单一 y 轴（体重 kg）。
 * - 线 2px 圆角端点；端点/极值点标记 ≥8px + 2px 表面描边环；面积为系列色 ~10% 洗染。
 * - 网格 1px 实线（灰一档离面），轴/标签用文本色（不穿系列色）。
 * - 选择性直标：仅端值（最新/最低/最高），不逐点标数。
 * - 点按选中最近点 → 高亮 + tooltip（值 + 日期）。
 */
@Composable
fun WeightTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val valueStyle =
        TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val axisStyle = TextStyle(color = mutedColor, fontSize = 10.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    // 命中最近数据点（命中半径 ≥ 标记）
                    val leftPad = 40.dp.toPx()
                    val rightPad = 16.dp.toPx()
                    val topPad = 20.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val plotW = size.width - leftPad - rightPad
                    val xs = points.map { p -> leftPad + fractionX(points, p) * plotW }
                    val nearest =
                        xs.indices.minByOrNull { i -> sqrt((xs[i] - tap.x) * (xs[i] - tap.x)) }
                    if (nearest != null && kotlin.math.abs(xs[nearest] - tap.x) <= 24.dp.toPx()) {
                        selectedIndex = if (selectedIndex == nearest) null else nearest
                    }
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val leftPad = 40.dp.toPx()
        val rightPad = 16.dp.toPx()
        val topPad = 20.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad

        val weights = points.map { it.weightKg }
        val (lo, hi) = niceBounds(weights.min(), weights.max())
        fun yOf(weight: Double): Float =
            (topPad + plotH * (1.0 - (weight - lo) / (hi - lo))).toFloat()

        // 网格 + y 轴刻度（干净数字，3 档）
        val ticks = listOf(lo, (lo + hi) / 2, hi)
        for (t in ticks) {
            val y = yOf(t)
            drawLine(
                gridColor,
                Offset(leftPad, y),
                Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            val label = t.rounded(1)
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                measured,
                topLeft = Offset(
                    leftPad - measured.size.width - 6.dp.toPx(),
                    y - measured.size.height / 2f
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
        for (p in listOf(points.first(), points.last()).distinct()) {
            val label = timeFmt(p.at)
            val measured = textMeasurer.measure(label, axisStyle)
            val x = leftPad + fractionX(points, p) * plotW
            drawText(
                measured,
                topLeft = Offset(
                    (x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                    size.height - bottomPad + 6.dp.toPx(),
                ),
            )
        }

        // 面积洗染（系列色 ~10%）
        val area = Path().apply {
            moveTo(
                leftPad + fractionX(points, points.first()) * plotW,
                yOf(points.first().weightKg)
            )
            for (p in points) lineTo(leftPad + fractionX(points, p) * plotW, yOf(p.weightKg))
            lineTo(leftPad + fractionX(points, points.last()) * plotW, topPad + plotH)
            lineTo(leftPad + fractionX(points, points.first()) * plotW, topPad + plotH)
            close()
        }
        drawPath(area, color = lineColor.copy(alpha = 0.1f))

        // 折线 2px 圆角
        val line = Path().apply {
            moveTo(
                leftPad + fractionX(points, points.first()) * plotW,
                yOf(points.first().weightKg)
            )
            for (p in points) lineTo(leftPad + fractionX(points, p) * plotW, yOf(p.weightKg))
        }
        drawPath(
            line,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 选择性直标：最低/最高/最新（端值），文字用文本色
        val minIdx = weights.indexOf(weights.min())
        val maxIdx = weights.indexOf(weights.max())
        val lastIdx = points.lastIndex
        for (idx in setOf(minIdx, maxIdx, lastIdx)) {
            val p = points[idx]
            val x = leftPad + fractionX(points, p) * plotW
            val y = yOf(p.weightKg)
            markerDot(this, Offset(x, y), lineColor, surfaceColor)
            val label = p.weightKg.rounded(1)
            val measured = textMeasurer.measure(label, valueStyle)
            val labelY =
                if (idx == maxIdx) y - measured.size.height - 8.dp.toPx() else y + 8.dp.toPx()
            drawText(
                measured,
                topLeft = Offset(
                    (x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                    labelY.coerceIn(0f, size.height - measured.size.height),
                ),
            )
        }

        // 点按选中：高亮 + tooltip
        selectedIndex?.let { idx ->
            val p = points[idx]
            val x = leftPad + fractionX(points, p) * plotW
            val y = yOf(p.weightKg)
            markerDot(this, Offset(x, y), lineColor, surfaceColor, radiusDp = 6f)
            val tip = "${p.weightKg.rounded(2)} kg · ${timeFmt(p.at)}"
            val measured = textMeasurer.measure(tip, valueStyle)
            val tx = (x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
            val ty = (y - measured.size.height - 14.dp.toPx()).coerceAtLeast(0f)
            drawText(measured, topLeft = Offset(tx, ty))
        }
    }
}

/** 趋势点（时间 + 体重）。 */
data class TrendPoint(val at: Instant, val weightKg: Double)

/** 列表 → 趋势点（时间升序）。 */
fun List<xyz.crazyh.kscale3.domain.Measurement>.toTrendPoints(): List<TrendPoint> =
    sortedBy { it.measuredAt }.map { TrendPoint(it.measuredAt, it.weightKg) }

/** 数据点标记：填充 + 2px 表面描边环（dataviz「surface ring」）；直径 ≥8px。 */
private fun markerDot(
    scope: DrawScope,
    center: Offset,
    color: androidx.compose.ui.graphics.Color,
    ringColor: androidx.compose.ui.graphics.Color,
    radiusDp: Float = 4.5f,
) {
    with(scope) {
        val radius = radiusDp.dp.toPx()
        drawCircle(ringColor, radius = radius + 2.dp.toPx(), center = center)
        drawCircle(color, radius = radius, center = center)
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

/** y 轴干净边界：0.5 kg 步进取整，序列近似恒定时给 ±1 kg 视窗。 */
private fun niceBounds(min: Double, max: Double): Pair<Double, Double> {
    if (max - min < 1.0) {
        val mid = (min + max) / 2
        return floor(mid - 1.0) to ceil(mid + 1.0)
    }
    val pad = (max - min) * 0.15
    return (floor((min - pad) * 2) / 2) to (ceil((max + pad) * 2) / 2)
}
