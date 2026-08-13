package com.kego.simplifiedfit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SimplifiedFitApp(viewModel: AppViewModel = viewModel()) {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    SimplifiedFitTheme(darkTheme = darkTheme) {
        val state = viewModel.state
        val snapshot = state.snapshot
        var destination by remember { mutableStateOf(Destination.TODAY) }
        var detail by remember { mutableStateOf<Detail?>(null) }
        var detailParent by remember { mutableStateOf<Detail?>(null) }
        var settings by remember { mutableStateOf(false) }

        BackHandler(detail != null || settings) {
            if (settings) {
                settings = false
            } else {
                detail = detailParent
                detailParent = null
            }
        }

        Box(Modifier.fillMaxSize().background(FitColors.Black)) {
            MatteTexture()
            when {
                settings -> SettingsScreen(
                    viewModel = viewModel,
                    state = state,
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                    onBack = { settings = false },
                )
                detail != null -> DetailScreen(
                    detail = detail!!,
                    snapshot = snapshot,
                    onBack = {
                        detail = detailParent
                        detailParent = null
                    },
                    onDetail = {
                        detailParent = detail
                        detail = it
                    },
                )
                destination == Destination.TODAY -> TodayScreen(
                    snapshot = snapshot,
                    onDetail = {
                        detailParent = null
                        detail = it
                    },
                    onSettings = { settings = true },
                    onDestination = { destination = it },
                )
                else -> CoachScreen(state = state, onAsk = viewModel::askCoach, onDestination = { destination = it })
            }
        }
    }
}

@Composable
private fun MatteTexture() {
    val surfaceColor = FitColors.Surface
    val backgroundColor = FitColors.Black
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(surfaceColor, backgroundColor),
                center = Offset(size.width * .28f, size.height * .08f),
                radius = maxOf(size.width, size.height) * .95f,
            ),
        )
        val spacing = 31.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 7.dp.toPx() else 18.dp.toPx()
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = .035f), .65.dp.toPx(), Offset(x, y))
                x += spacing
            }
            y += spacing * .73f
            row++
        }
    }
}

@Composable
private fun AppHeader(
    title: String,
    subtitle: String? = null,
    onSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(37.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                OutlineIcon(FitIcon.BACK, FitColors.White, 20.dp)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 15.sp, letterSpacing = 2.4.sp))
                if (subtitle != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(subtitle, color = FitColors.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (onSettings != null) {
            Box(Modifier.size(40.dp).clickable(onClick = onSettings), contentAlignment = Alignment.CenterEnd) {
                OutlineIcon(FitIcon.SETTINGS, FitColors.Muted, 22.dp)
            }
        }
    }
}

@Composable
private fun TodayScreen(
    snapshot: HealthSnapshot,
    onDetail: (Detail) -> Unit,
    onSettings: () -> Unit,
    onDestination: (Destination) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("TODAY", color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 24.sp, letterSpacing = 2.2.sp))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(FitColors.Cyan, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("Synced ${snapshot.lastSync}", color = FitColors.Muted, fontSize = 16.sp)
                }
            }
            Box(Modifier.size(48.dp).clickable(onClick = onSettings), contentAlignment = Alignment.TopEnd) {
                OutlineIcon(FitIcon.SETTINGS, FitColors.White, 26.dp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 27.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScoreRing(
                    (snapshot.steps / 100f).toInt(),
                    "Steps",
                    FitColors.Cyan,
                    size = 144.dp,
                    provisional = false,
                    valueText = snapshot.steps.formatted(),
                ) { onDetail(Detail.STEPS) }
                ScoreRing(snapshot.sleepScore, "Sleep", FitColors.Violet, size = 144.dp) { onDetail(Detail.SLEEP) }
            }
            Rule(Modifier.padding(horizontal = 22.dp))
            MetricRow("Readiness", snapshot.readiness.toString(), "score", FitColors.Green, FitIcon.TODAY) { onDetail(Detail.READINESS) }
            MetricRow("Heart rate", snapshot.latestHeartRate.toString(), "bpm", FitColors.Coral, FitIcon.HEART) { onDetail(Detail.HEART) }
            MetricRow("Calories", snapshot.totalCalories.formatted(), "kcal", FitColors.Cyan, FitIcon.FIRE) { onDetail(Detail.CALORIES) }
        }
        BottomNav(Destination.TODAY, onDestination)
    }
}

@Composable
private fun BottomNav(selected: Destination, onDestination: (Destination) -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        Rule()
        Row(Modifier.fillMaxWidth().height(66.dp)) {
            NavItem("Today", FitIcon.CALENDAR, selected == Destination.TODAY, Modifier.weight(1f)) { onDestination(Destination.TODAY) }
            NavItem("Coach", FitIcon.COACH, selected == Destination.COACH, Modifier.weight(1f)) { onDestination(Destination.COACH) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: FitIcon, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OutlineIcon(icon, if (selected) FitColors.White else FitColors.Muted, 22.dp)
        Spacer(Modifier.height(5.dp))
        Text(label.uppercase(), color = if (selected) FitColors.White else FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 8.sp, letterSpacing = 1.2.sp))
    }
}

@Composable
private fun DetailScreen(
    detail: Detail,
    snapshot: HealthSnapshot,
    onBack: () -> Unit,
    onDetail: (Detail) -> Unit,
) {
    val title = when (detail) {
        Detail.READINESS -> "Readiness"
        Detail.SLEEP -> "Sleep"
        Detail.STEPS -> "Steps"
        Detail.HEART -> "Heart"
        Detail.CALORIES -> "Calories"
        Detail.HRV -> "Heart rate variability"
        Detail.RESTING_HEART_RATE -> "Resting heart rate"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(title, "Thu, 13 Aug", onBack = onBack)
        Rule(Modifier.padding(horizontal = 22.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            when (detail) {
                Detail.READINESS -> ReadinessDetail(snapshot, onDetail)
                Detail.SLEEP -> SleepDetail(snapshot)
                Detail.STEPS -> StepsDetail(snapshot)
                Detail.HEART -> HeartDetail(snapshot)
                Detail.CALORIES -> CaloriesDetail(snapshot)
                Detail.HRV -> HrvDetail(snapshot)
                Detail.RESTING_HEART_RATE -> RestingHeartRateDetail(snapshot)
            }
            Spacer(Modifier.height(4.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun ReadinessDetail(snapshot: HealthSnapshot, onDetail: (Detail) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 27.dp), horizontalArrangement = Arrangement.Center) {
        ScoreRing(snapshot.readiness, "Readiness", FitColors.Green, size = 176.dp, provisional = false)
    }
    Text("Readiness combines HRV, recent sleep, and resting heart rate.", color = FitColors.White, style = FitType.Body)
    SectionLabel("Signals")
    Rule()
    DataRow(
        "Heart-rate variability",
        metricValue(snapshot.hrv),
        "ms",
        FitColors.Green,
        onClick = { onDetail(Detail.HRV) },
    )
    DataRow("Past week of sleep", snapshot.sleepTrend.size.toString(), "nights")
    DataRow(
        "Resting heart rate",
        metricValue(snapshot.restingHeartRate.toDouble()),
        "bpm",
        FitColors.Coral,
        onClick = { onDetail(Detail.RESTING_HEART_RATE) },
    )
}

@Composable
private fun HrvDetail(snapshot: HealthSnapshot) {
    HealthMetricDetail(
        label = "Heart rate variability",
        value = snapshot.hrv,
        unit = "ms",
        points = snapshot.hrvTrend,
        color = FitColors.Green,
    )
}

@Composable
private fun RestingHeartRateDetail(snapshot: HealthSnapshot) {
    HealthMetricDetail(
        label = "Resting heart rate",
        value = snapshot.restingHeartRate.toDouble(),
        unit = "bpm",
        points = snapshot.restingHeartRateTrend,
        color = FitColors.Coral,
    )
}

@Composable
private fun HealthMetricDetail(
    label: String,
    value: Double,
    unit: String,
    points: List<DayPoint>,
    color: Color,
) {
    val readings = points.filter { it.value > 0f }
    val values = readings.map { it.value.toDouble() }.ifEmpty {
        listOfNotNull(value.takeIf { it > 0.0 })
    }
    val average = values.average()

    Row(Modifier.fillMaxWidth().padding(top = 23.dp), verticalAlignment = Alignment.Bottom) {
        Text(metricValue(average), color = FitColors.White, style = FitType.Display.copy(fontSize = 56.sp))
        Spacer(Modifier.width(9.dp))
        Text("$unit (avg)", color = FitColors.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 9.dp))
    }
    if (readings.isNotEmpty()) {
        val low = readings.minOf { it.value }.toDouble()
        val high = readings.maxOf { it.value }.toDouble()
        Text(
            "Range: ${metricValue(low)}-${metricValue(high)} $unit over the last 7 days",
            color = FitColors.Muted,
            style = FitType.Body,
        )
    } else {
        Text("No readings synced yet.", color = FitColors.Muted, style = FitType.Body)
    }
    SectionLabel("7 days", color = color)
    MetricTrendChart(readings, color)
    if (readings.isNotEmpty()) {
        SectionLabel("Daily", "$label (avg)", color = color)
        readings.asReversed().forEachIndexed { index, point ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    when (index) {
                        0 -> "Today"
                        1 -> "Yesterday"
                        else -> point.label
                    },
                    color = FitColors.Muted,
                    style = FitType.Body,
                    modifier = Modifier.weight(1f),
                )
                Text(metricValue(point.value.toDouble()), color = FitColors.White, style = FitType.Metric.copy(fontSize = 24.sp))
                Spacer(Modifier.width(5.dp))
                Text(unit.uppercase(), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 8.sp), modifier = Modifier.padding(bottom = 4.dp))
            }
            Rule()
        }
    }
}

private fun metricValue(value: Double): String =
    if (value > 0.0) value.roundToInt().toString() else "n/a"

@Composable
private fun MetricTrendChart(points: List<DayPoint>, color: Color) {
    if (points.isEmpty()) return
    val values = points.map { it.value }
    val minValue = values.minOrNull() ?: return
    val maxValue = values.maxOrNull() ?: return
    val padding = ((maxValue - minValue) * .18f).coerceAtLeast(1f)
    val low = minValue - padding
    val high = maxValue + padding
    val range = (high - low).coerceAtLeast(1f)
    val ruleColor = FitColors.Rule
    val backgroundColor = FitColors.Black

    Row(Modifier.fillMaxWidth().height(165.dp)) {
        Column(
            Modifier.width(34.dp).fillMaxHeight().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(metricValue(high.toDouble()), color = FitColors.Muted, fontSize = 11.sp)
            Text(metricValue(((high + low) / 2f).toDouble()), color = FitColors.Muted, fontSize = 11.sp)
            Text(metricValue(low.toDouble()), color = FitColors.Muted, fontSize = 11.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
            for (index in 0..2) {
                val y = index * size.height / 2f
                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / range) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / range) * size.height
                drawCircle(backgroundColor, 4.dp.toPx(), Offset(x, y))
                drawCircle(color, 4.dp.toPx(), Offset(x, y), style = Stroke(1.7.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        points.forEach { Text(it.label, color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp)) }
    }
}

@Composable
private fun SleepDetail(snapshot: HealthSnapshot) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
        SleepScoreRing(snapshot.sleepScore)
    }
    SleepDuration(snapshot)
    Rule(Modifier.padding(top = 12.dp))
    SleepStages(snapshot)
    Rule(Modifier.padding(top = 8.dp))
    SectionLabel("7 days", color = FitColors.Violet, topPadding = 16.dp, bottomPadding = 8.dp)
    SleepTrend(snapshot.sleepTrend)
    Spacer(Modifier.height(42.dp))
    Rule()
    SectionLabel("Sleep score breakdown", color = FitColors.Violet, topPadding = 16.dp, bottomPadding = 8.dp)
    SleepBreakdown(snapshot.sleepBreakdown)
}

@Composable
private fun SleepStages(snapshot: HealthSnapshot) {
    val stages = listOf(
        SleepStage("Awake", snapshot.awakeMinutes, FitColors.StageAwake),
        SleepStage("Light", snapshot.lightMinutes, FitColors.Violet),
        SleepStage("Deep", snapshot.deepMinutes, FitColors.StageDeep),
        SleepStage("REM", snapshot.remMinutes, FitColors.StageRem),
    )
    val total = stages.sumOf { it.minutes }.coerceAtLeast(1).toFloat()

    SectionLabel("Sleep stages", color = FitColors.Violet, topPadding = 16.dp, bottomPadding = 8.dp)
    Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        stages.forEach { stage ->
            Box(Modifier.weight((stage.minutes / total).coerceAtLeast(.01f)).fillMaxSize().background(stage.color))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        stages.forEach { stage ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(stage.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(stage.label, color = FitColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(formatSleepMinutes(stage.minutes), color = FitColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 18.dp, top = 7.dp))
            }
        }
    }
}

private data class SleepStage(val label: String, val minutes: Int, val color: Color)

private fun formatSleepMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours == 0) "${remainder}m" else "${hours}h ${remainder.toString().padStart(2, '0')}m"
}

@Composable
private fun SleepDuration(snapshot: HealthSnapshot) {
    val hours = snapshot.sleepMinutes / 60
    val minutes = (snapshot.sleepMinutes % 60).toString().padStart(2, '0')
    val targetHours = snapshot.sleepTargetMinutes / 60
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${hours}h ${minutes}m", color = FitColors.White, style = FitType.Metric.copy(fontSize = 31.sp))
            Spacer(Modifier.width(10.dp))
            Text("asleep", color = FitColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text("${targetHours}h target", color = FitColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        Text(
            "${snapshot.awakeMinutes}m awake  ·  ${snapshot.restlessnessMinutes}m restless",
            color = FitColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SleepTrend(points: List<DayPoint>) {
    if (points.isEmpty()) return
    val low = (points.minOfOrNull { it.value } ?: 0f) - 4f
    val high = (points.maxOfOrNull { it.value } ?: 1f) + 4f
    val ruleColor = FitColors.Rule
    val violetColor = FitColors.Violet
    val backgroundColor = FitColors.Black
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEachIndexed { index, point ->
                Text(
                    point.value.toInt().toString(),
                    color = if (index == points.lastIndex || point.value == points.maxOf { it.value }) FitColors.Violet else FitColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp)) {
            val range = (high - low).coerceAtLeast(1f)
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / range) * (size.height - 10.dp.toPx())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawLine(
                ruleColor,
                Offset(0f, size.height - 1.dp.toPx()),
                Offset(size.width, size.height - 1.dp.toPx()),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
            drawPath(path, violetColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val x = index * size.width / (points.size - 1).coerceAtLeast(1)
                val y = size.height - ((point.value - low) / range) * (size.height - 10.dp.toPx())
                drawCircle(if (index == points.lastIndex) violetColor else backgroundColor, 4.dp.toPx(), Offset(x, y))
                drawCircle(violetColor, 4.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEachIndexed { index, point ->
                Text(
                    point.label,
                    color = if (index == points.lastIndex) FitColors.Violet else FitColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
    }
}

private data class SleepBreakdownItem(val label: String, val value: Int, val icon: FitIcon)

@Composable
private fun SleepBreakdown(breakdown: SleepScoreBreakdown) {
    val items = listOfNotNull(
        SleepBreakdownItem("Duration", breakdown.duration, FitIcon.CLOCK),
        SleepBreakdownItem("Restfulness", breakdown.continuity, FitIcon.WAVES),
        breakdown.restlessness?.let { SleepBreakdownItem("Restlessness", it, FitIcon.WAVES) },
        breakdown.rem?.let { SleepBreakdownItem("REM", it, FitIcon.LOTUS) },
        breakdown.deep?.let { SleepBreakdownItem("Deep", it, FitIcon.LOTUS) },
    )
    if (items.isEmpty()) {
        Text("Not enough sleep data to calculate a breakdown.", color = FitColors.Muted, style = FitType.Body)
        return
    }
    items.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineIcon(item.icon, FitColors.Violet, 25.dp)
            Spacer(Modifier.width(11.dp))
            Text(item.label, color = FitColors.White, fontSize = 16.sp, modifier = Modifier.width(103.dp))
            Box(
                Modifier.weight(1f).height(6.dp).background(FitColors.Track, RoundedCornerShape(4.dp)),
            ) {
                Box(Modifier.fillMaxWidth(item.value / 100f).fillMaxSize().background(FitColors.Violet, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.width(13.dp))
            Text(
                item.value.toString(),
                color = FitColors.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp),
            )
        }
        Rule()
    }
}

@Composable
private fun StepsDetail(snapshot: HealthSnapshot) {
    val averageSteps = snapshot.stepTrend.map { it.value }.average().toInt()
    val bestDay = snapshot.stepTrend.maxByOrNull { it.value }
    val progress = (snapshot.steps / 10_000f * 100f).coerceIn(0f, 100f)
    val remaining = (10_000 - snapshot.steps).coerceAtLeast(0)

    Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center) {
        ScoreRing(
            (snapshot.steps / 100f).toInt(),
            "Steps",
            FitColors.Cyan,
            size = 176.dp,
            provisional = false,
            valueText = snapshot.steps.formatted(),
        )
    }
    Text(
        String.format(Locale.US, "%.1f%% OF 10K", progress),
        color = FitColors.White,
        style = FitType.Metric.copy(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        "${remaining.formatted()} STEPS REMAINING",
        color = FitColors.Muted,
        style = FitType.Eyebrow.copy(fontSize = 9.sp),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    SectionLabel("7 days", "10k target")
    SevenDayLine(snapshot.stepTrend, FitColors.Cyan, target = 10_000f)
    SectionLabel("Summary")
    Rule()
    DataRow("7-day average", averageSteps.formatted(), "steps")
    bestDay?.let { DataRow("Best day · ${it.label}", it.value.toInt().formatted(), "steps", FitColors.Cyan) }
}

@Composable
private fun HeartDetail(snapshot: HealthSnapshot) {
    Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center) {
        ScoreRing(72, "Latest bpm", FitColors.Coral, size = 176.dp, provisional = false)
    }
    Text("Fitbit sync, not live.", color = FitColors.Muted, style = FitType.Body.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    SectionLabel("Today", "synced 8:42")
    HeartChart()
    SectionLabel("Range")
    Rule()
    DataRow("Resting", snapshot.restingHeartRate.toString(), "bpm")
    DataRow("Low", snapshot.lowHeartRate.toString(), "bpm")
    DataRow("High", snapshot.highHeartRate.toString(), "bpm", FitColors.Coral)
}

@Composable
private fun HeartChart() {
    val values = listOf(61f, 58f, 57f, 63f, 82f, 112f, 86f, 70f, 68f, 94f, 132f, 89f, 74f, 72f)
    val ruleColor = FitColors.Rule
    val coralColor = FitColors.Coral
    Column {
        Canvas(Modifier.fillMaxWidth().height(136.dp)) {
            for (i in 0..3) {
                val y = i * size.height / 3f
                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, value ->
                val x = i * size.width / (values.size - 1)
                val y = size.height - ((value - 45f) / 95f) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, coralColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12A", "6A", "12P", "6P", "NOW").forEach { Text(it, color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 8.sp)) }
        }
    }
}

@Composable
private fun CaloriesDetail(snapshot: HealthSnapshot) {
    Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center) {
        ScoreRing(73, "Daily total", FitColors.Cyan, size = 176.dp, provisional = false)
    }
    Text("${snapshot.totalCalories.formatted()} KCAL", color = FitColors.White, style = FitType.Metric, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    SectionLabel("7 days")
    SevenDayBars(snapshot.calorieTrend, FitColors.Cyan)
    SectionLabel("Today")
    Rule()
    DataRow("Active", snapshot.activeCalories.formatted(), "kcal", FitColors.Cyan)
    DataRow("Resting", snapshot.restingCalories.formatted(), "kcal")
    DataRow("7-day average", "2,241", "kcal")
}

@Composable
private fun CoachScreen(state: AppUiState, onAsk: (String) -> Unit, onDestination: (Destination) -> Unit) {
    var input by remember { mutableStateOf("") }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sendMessage = {
        val message = input.trim()
        if (message.isNotEmpty()) {
            onAsk(message)
            input = ""
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("COACH", color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 24.sp, letterSpacing = 2.2.sp))
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(FitColors.Green, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.coachConnected) "Mac connected" else "Mac offline", color = if (state.coachConnected) FitColors.Green else FitColors.Muted, fontSize = 16.sp)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(24.dp))
            if (state.coachMessage != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        state.coachMessage,
                        color = FitColors.White,
                        style = FitType.Body,
                        modifier = Modifier.background(FitColors.Surface, RoundedCornerShape(22.dp)).padding(horizontal = 19.dp, vertical = 15.dp),
                    )
                }
            }
            if (state.coachMessage != null) Spacer(Modifier.height(48.dp))
            when {
                state.coachBusy -> Text("Thinking...", color = FitColors.Muted, style = FitType.Body.copy(fontSize = 16.sp))
                state.coachReply != null -> Text(state.coachReply, color = FitColors.White, style = FitType.Body.copy(fontSize = 16.sp, lineHeight = 25.sp), modifier = Modifier.fillMaxWidth())
                state.coachMessage == null -> Text(
                    if (state.coachConnected) "Ask a question about your health." else "Pair the Mac companion to start a conversation.",
                    color = FitColors.Muted,
                    style = FitType.Body.copy(fontSize = 16.sp, lineHeight = 25.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(36.dp)); Rule(); Spacer(Modifier.height(16.dp))
            listOf("Why is readiness lower?" to FitColors.Green, "How can I sleep better?" to FitColors.Violet, "Compare this week" to FitColors.Cyan).forEach { (suggestion, color) ->
                Row(Modifier.fillMaxWidth().clickable { input = suggestion }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SuggestionMark(color); Spacer(Modifier.width(18.dp))
                    Text(suggestion, color = FitColors.White, style = FitType.Body, modifier = Modifier.weight(1f))
                    Text("›", color = FitColors.White, fontSize = 28.sp)
                }
                Rule()
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = FitType.Body.copy(color = FitColors.White),
                cursorBrush = SolidColor(FitColors.Green),
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                modifier = Modifier.weight(1f).border(1.dp, FitColors.Rule, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    Box {
                        if (input.isBlank()) Text("Ask about your health", color = FitColors.Muted, style = FitType.Body)
                        inner()
                    }
                },
            )
            Box(Modifier.size(48.dp).clickable {
                sendMessage()
            }.background(FitColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
                OutlineIcon(FitIcon.SEND, if (input.isBlank()) FitColors.Muted else FitColors.Green, 23.dp)
            }
        }
        if (!imeVisible) BottomNav(Destination.COACH, onDestination)
    }
}

@Composable private fun SuggestionMark(color: Color) { Canvas(Modifier.size(31.dp)) { drawCircle(color, size.minDimension * .43f, center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))) } }

@Composable
private fun SettingsScreen(
    viewModel: AppViewModel,
    state: AppUiState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val savedSetup = remember { viewModel.googleSetupCredentials() }
    var clientId by remember { mutableStateOf(savedSetup?.clientId.orEmpty()) }
    var clientSecret by remember { mutableStateOf(savedSetup?.clientSecret.orEmpty()) }
    var authorizationCode by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        AppHeader("Settings", onBack = onBack)
        Rule(Modifier.padding(horizontal = 22.dp))
        Column(Modifier.padding(horizontal = 22.dp).imePadding().verticalScroll(rememberScrollState())) {
            SectionLabel("Google Health")
            DataRow("Status", if (state.googleConnected) "Connected" else "Not connected", color = if (state.googleConnected) FitColors.Green else FitColors.White)
            DataRow("Sync", "Every 6 hours")
            DataRow("Storage", "30 days")
            if (!state.googleConnected) {
                SetupField("WEB CLIENT ID", clientId) { clientId = it }
                SetupField("CLIENT SECRET", clientSecret) { clientSecret = it }
                SettingsAction("SAVE & OPEN GOOGLE CONSENT", FitColors.Cyan) {
                    viewModel.prepareGoogleAuthorization(clientId, clientSecret)?.let { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                SetupField("PASTE REDIRECT URL OR CODE", authorizationCode) { authorizationCode = it }
                SettingsAction("CONNECT GOOGLE HEALTH", FitColors.Green) {
                    viewModel.connectGoogle(clientId, clientSecret, authorizationCode)
                }
            } else {
                SettingsAction(if (state.syncing) "SYNCING…" else "SYNC NOW", FitColors.Cyan, viewModel::sync)
                SettingsAction("DISCONNECT", FitColors.Coral, viewModel::disconnectGoogle)
            }
            SectionLabel("Appearance")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("THEME", color = FitColors.White, style = FitType.Eyebrow)
                    Spacer(Modifier.height(6.dp))
                    Text(if (darkTheme) "Dark" else "Light", color = FitColors.Muted, fontSize = 14.sp)
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FitColors.Black,
                        checkedTrackColor = FitColors.Green,
                        uncheckedThumbColor = FitColors.White,
                        uncheckedTrackColor = FitColors.Track,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            Rule()
            SectionLabel("Coach")
            DataRow("Mac companion", if (state.coachConnected) "Connected" else "Not connected", color = if (state.coachConnected) FitColors.Green else FitColors.White)
            DataRow("Network", "Tailscale")
            SetupField("PASTE PAIRING DETAILS", pairing) { pairing = it }
            SettingsAction("PAIR MAC COACH", FitColors.Green) { viewModel.pairCoach(pairing) }
            SectionLabel("Scores")
            Text("Readiness and sleep scores are calculated locally. They are not Fitbit or medical scores.", color = FitColors.Muted, style = FitType.Body)
            state.setupMessage?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, color = if (it.contains("failed", true) || it.contains("could not", true)) FitColors.Coral else FitColors.Muted, style = FitType.Body)
            }
            Spacer(Modifier.height(80.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun SetupField(label: String, value: String, onValueChange: (String) -> Unit) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) bringIntoViewRequester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().padding(top = 17.dp)) {
        Text(label, color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = FitType.Body.copy(color = FitColors.White),
            cursorBrush = SolidColor(FitColors.Green),
            singleLine = true,
            visualTransformation = if (label == "CLIENT SECRET") PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focused = it.isFocused }
                .padding(vertical = 12.dp),
        )
        Rule()
    }
}

@Composable
private fun SettingsAction(label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, style = FitType.Eyebrow, modifier = Modifier.weight(1f))
        Text("›", color = color, fontSize = 24.sp)
    }
    Rule()
}
