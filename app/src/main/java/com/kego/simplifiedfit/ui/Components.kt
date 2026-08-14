package com.kego.simplifiedfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import kotlin.math.max

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(FitColors.Rule))
}

@Composable
fun ScoreRing(
    value: Int,
    label: String,
    color: Color,
    size: Dp = 154.dp,
    valueText: String = value.toString(),
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    val trackColor = FitColors.Track
    Box(clickModifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 9.dp.toPx()
            val pad = stroke / 2f + 2.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(this.size.width - pad * 2, this.size.height - pad * 2),
                style = Stroke(stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = value.coerceIn(0, 100) * 3.6f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(this.size.width - pad * 2, this.size.height - pad * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), color = color, style = FitType.Eyebrow.copy(fontSize = 10.sp))
            Spacer(Modifier.height(2.dp))
            Text(
                text = valueText,
                color = FitColors.White,
                style = FitType.Display.copy(fontSize = if (valueText.length > 3) 34.sp else if (size < 140.dp) 43.sp else 50.sp),
            )
        }
    }
}

@Composable
fun SleepScoreRing(value: Int, modifier: Modifier = Modifier) {
    val trackColor = FitColors.Track
    val violetColor = FitColors.Violet
    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 11.dp.toPx()
            val pad = stroke / 2f + 5.dp.toPx()
            val ringSize = Size(size.width - pad * 2, size.height - pad * 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = ringSize,
                style = Stroke(stroke),
            )
            drawArc(
                color = violetColor,
                startAngle = -90f,
                sweepAngle = value.coerceIn(0, 100) * 3.6f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SLEEP SCORE", color = FitColors.Violet, style = FitType.Eyebrow.copy(fontSize = 11.sp))
            Spacer(Modifier.height(3.dp))
            Text(value.toString(), color = FitColors.White, style = FitType.Display.copy(fontSize = 62.sp, letterSpacing = (-2).sp))
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    unit: String,
    color: Color,
    icon: FitIcon,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlineIcon(icon, color)
            Spacer(Modifier.width(15.dp))
            Text(label.uppercase(), color = color, style = FitType.Eyebrow, modifier = Modifier.weight(1f))
            Text(value, color = FitColors.White, style = FitType.Metric)
            Spacer(Modifier.width(5.dp))
            Text(unit.uppercase(), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp))
        }
        Rule(Modifier.padding(horizontal = 22.dp))
    }
}

enum class FitIcon { STEPS, HEART, FIRE, TODAY, COACH, SETTINGS, BACK, SEND, CLOCK, WAVES, LOTUS, CALENDAR }

@Composable
fun OutlineIcon(icon: FitIcon, color: Color, size: Dp = 25.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        when (icon) {
            FitIcon.STEPS -> {
                val p = Path().apply {
                    moveTo(w * .12f, h * .56f)
                    lineTo(w * .34f, h * .28f)
                    lineTo(w * .54f, h * .43f)
                    lineTo(w * .72f, h * .27f)
                    lineTo(w * .92f, h * .44f)
                    lineTo(w * .73f, h * .63f)
                    lineTo(w * .92f, h * .79f)
                    lineTo(w * .92f, h * .94f)
                    lineTo(w * .53f, h * .94f)
                    lineTo(w * .34f, h * .75f)
                    lineTo(w * .12f, h * .75f)
                    close()
                }
                drawPath(p, color, style = stroke)
            }
            FitIcon.HEART -> {
                val p = Path().apply {
                    moveTo(w * .5f, h * .86f)
                    cubicTo(w * .08f, h * .60f, w * .08f, h * .22f, w * .30f, h * .18f)
                    cubicTo(w * .42f, h * .15f, w * .49f, h * .25f, w * .5f, h * .31f)
                    cubicTo(w * .53f, h * .22f, w * .61f, h * .15f, w * .72f, h * .18f)
                    cubicTo(w * .95f, h * .24f, w * .90f, h * .61f, w * .5f, h * .86f)
                }
                drawPath(p, color, style = stroke)
            }
            FitIcon.FIRE -> {
                val p = Path().apply {
                    moveTo(w * .52f, h * .05f)
                    cubicTo(w * .63f, h * .30f, w * .92f, h * .39f, w * .82f, h * .70f)
                    cubicTo(w * .73f, h * .98f, w * .26f, h * .98f, w * .18f, h * .68f)
                    cubicTo(w * .12f, h * .45f, w * .32f, h * .32f, w * .39f, h * .18f)
                    cubicTo(w * .42f, h * .37f, w * .57f, h * .43f, w * .52f, h * .05f)
                }
                drawPath(p, color, style = stroke)
            }
            FitIcon.TODAY -> {
                drawCircle(color, w * .37f, Offset(w / 2, h / 2), style = stroke)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 265f,
                    useCenter = false,
                    topLeft = Offset(w * .22f, h * .22f),
                    size = Size(w * .56f, h * .56f),
                    style = stroke,
                )
            }
            FitIcon.COACH -> {
                drawRoundRect(color, Offset(w * .08f, h * .15f), Size(w * .84f, h * .64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .16f), style = stroke)
                drawLine(color, Offset(w * .27f, h * .79f), Offset(w * .18f, h * .94f), stroke.width, StrokeCap.Round)
                drawCircle(color, w * .035f, Offset(w * .36f, h * .47f))
                drawCircle(color, w * .035f, Offset(w * .50f, h * .47f))
                drawCircle(color, w * .035f, Offset(w * .64f, h * .47f))
            }
            FitIcon.SETTINGS -> {
                val gear = Path().apply {
                    moveTo(w * .422f, h * .125f)
                    lineTo(w * .578f, h * .125f)
                    lineTo(w * .603f, h * .239f)
                    cubicTo(w * .678f, h * .276f, w * .753f, h * .319f, w * .775f, h * .344f)
                    lineTo(w * .881f, h * .294f)
                    lineTo(w * .959f, h * .430f)
                    lineTo(w * .875f, h * .502f)
                    cubicTo(w * .880f, h * .527f, w * .880f, h * .553f, w * .875f, h * .577f)
                    lineTo(w * .959f, h * .648f)
                    lineTo(w * .881f, h * .784f)
                    lineTo(w * .775f, h * .734f)
                    cubicTo(w * .709f, h * .794f, w * .653f, h * .828f, w * .603f, h * .864f)
                    lineTo(w * .578f, h * .875f)
                    lineTo(w * .422f, h * .875f)
                    lineTo(w * .397f, h * .761f)
                    cubicTo(w * .322f, h * .724f, w * .247f, h * .681f, w * .225f, h * .656f)
                    lineTo(w * .119f, h * .706f)
                    lineTo(w * .041f, h * .570f)
                    lineTo(w * .125f, h * .498f)
                    cubicTo(w * .120f, h * .473f, w * .120f, h * .447f, w * .125f, h * .423f)
                    lineTo(w * .041f, h * .352f)
                    lineTo(w * .119f, h * .216f)
                    lineTo(w * .225f, h * .266f)
                    cubicTo(w * .291f, h * .206f, w * .347f, h * .172f, w * .397f, h * .136f)
                    close()
                }
                val gearStroke = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(gear, color, style = gearStroke)
                drawCircle(color, w * .128f, Offset(w / 2f, h / 2f), style = gearStroke)
            }
            FitIcon.BACK -> {
                drawLine(color, Offset(w * .72f, h * .14f), Offset(w * .25f, h * .5f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .25f, h * .5f), Offset(w * .72f, h * .86f), stroke.width, StrokeCap.Round)
            }
            FitIcon.SEND -> {
                val sendStrokeWidth = 2.dp.toPx()
                drawLine(color, Offset(w * .18f, h * .82f), Offset(w * .78f, h * .22f), sendStrokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * .54f, h * .22f), Offset(w * .78f, h * .22f), sendStrokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, h * .22f), Offset(w * .78f, h * .46f), sendStrokeWidth, StrokeCap.Round)
            }
            FitIcon.CLOCK -> {
                drawCircle(color, w * .43f, Offset(w * .44f, h * .52f), style = stroke)
                drawLine(color, Offset(w * .44f, h * .52f), Offset(w * .44f, h * .29f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .44f, h * .52f), Offset(w * .60f, h * .62f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .72f, h * .15f), Offset(w * .72f, h * .37f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .62f, h * .26f), Offset(w * .84f, h * .26f), stroke.width, StrokeCap.Round)
            }
            FitIcon.WAVES -> {
                repeat(3) { index ->
                    val y = h * (.28f + index * .23f)
                    val path = Path().apply {
                        moveTo(w * .08f, y)
                        cubicTo(w * .18f, y - h * .10f, w * .27f, y - h * .10f, w * .37f, y)
                        cubicTo(w * .47f, y + h * .10f, w * .57f, y + h * .10f, w * .67f, y)
                        cubicTo(w * .77f, y - h * .10f, w * .86f, y - h * .10f, w * .94f, y)
                    }
                    drawPath(path, color, style = stroke)
                }
            }
            FitIcon.LOTUS -> {
                val left = Path().apply {
                    moveTo(w * .50f, h * .82f)
                    cubicTo(w * .32f, h * .78f, w * .13f, h * .58f, w * .10f, h * .35f)
                    cubicTo(w * .32f, h * .42f, w * .47f, h * .61f, w * .50f, h * .82f)
                }
                val center = Path().apply {
                    moveTo(w * .50f, h * .82f)
                    cubicTo(w * .34f, h * .58f, w * .39f, h * .25f, w * .50f, h * .08f)
                    cubicTo(w * .61f, h * .25f, w * .66f, h * .58f, w * .50f, h * .82f)
                }
                val right = Path().apply {
                    moveTo(w * .50f, h * .82f)
                    cubicTo(w * .68f, h * .78f, w * .87f, h * .58f, w * .90f, h * .35f)
                    cubicTo(w * .68f, h * .42f, w * .53f, h * .61f, w * .50f, h * .82f)
                }
                drawPath(left, color, style = stroke)
                drawPath(center, color, style = stroke)
                drawPath(right, color, style = stroke)
                drawLine(color, Offset(w * .10f, h * .83f), Offset(w * .90f, h * .83f), stroke.width, StrokeCap.Round)
            }
            FitIcon.CALENDAR -> {
                drawRoundRect(color, Offset(w * .12f, h * .18f), Size(w * .76f, h * .70f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f), style = stroke)
                drawLine(color, Offset(w * .12f, h * .38f), Offset(w * .88f, h * .38f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .30f, h * .08f), Offset(w * .30f, h * .26f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * .70f, h * .08f), Offset(w * .70f, h * .26f), stroke.width, StrokeCap.Round)
                repeat(2) { row ->
                    repeat(3) { column ->
                        drawCircle(color, w * .025f, Offset(w * (.30f + column * .20f), h * (.54f + row * .16f)))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    trailing: String? = null,
    color: Color = FitColors.White,
    topPadding: Dp = 25.dp,
    bottomPadding: Dp = 12.dp,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), color = color, style = FitType.Eyebrow, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.uppercase(), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp))
    }
}

@Composable
fun DataRow(
    label: String,
    value: String,
    unit: String = "",
    color: Color = FitColors.White,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Text(unit.uppercase(), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 8.sp), modifier = Modifier.padding(bottom = 3.dp))
            }
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Text("›", color = color, fontSize = 24.sp, modifier = Modifier.padding(bottom = 1.dp))
            }
        }
        Rule()
    }
}

@Composable
fun SevenDayLine(points: List<DayPoint>, color: Color, target: Float? = null) {
    val values = points.map { it.value }
    val low = (values.minOrNull() ?: 0f) * .9f
    val high = max(values.maxOrNull() ?: 1f, target ?: 0f) * 1.06f
    val ruleColor = FitColors.Rule
    val backgroundColor = FitColors.Black
    Column {
        Canvas(Modifier.fillMaxWidth().height(128.dp)) {
            if (target != null) {
                val y = size.height - ((target - low) / (high - low)) * size.height
                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / (high - low)) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / (high - low)) * size.height
                drawCircle(if (index == points.lastIndex) color else backgroundColor, 4.dp.toPx(), Offset(x, y))
                drawCircle(color, 4.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { Text(it.label.take(1), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun SevenDayBars(points: List<DayPoint>, color: Color) {
    val high = (points.maxOfOrNull { it.value } ?: 1f) * 1.05f
    Column {
        Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            points.forEachIndexed { index, point ->
                Box(
                    Modifier.width(22.dp)
                        .height((112 * point.value / high).dp)
                        .background(if (index == points.lastIndex) color else color.copy(alpha = .35f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { Text(it.label.take(1), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp)) }
        }
    }
}

fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)
