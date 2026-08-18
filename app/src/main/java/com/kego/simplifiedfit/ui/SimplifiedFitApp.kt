package com.kego.simplifiedfit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kego.simplifiedfit.data.CoachProvider
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

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
            MatteTexture(settings)
            when {
                settings -> SettingsScreen(
                    viewModel = viewModel,
                    state = state,
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                    onBack = { settings = false },
                    onDestination = {
                        settings = false
                        destination = it
                    },
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
                else -> CoachScreen(
                    state = state,
                    onAsk = viewModel::askCoach,
                    onRetry = viewModel::retryCoach,
                    onNewChat = viewModel::newCoachChat,
                    onDestination = { destination = it },
                    onSettings = { settings = true },
                )
            }
        }
    }
}

@Composable
private fun MatteTexture(settings: Boolean) {
    val backgroundColor = FitColors.Black
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                colors = if (settings) {
                    listOf(backgroundColor, Color(0xFF142024), Color(0xFF45555D))
                } else {
                    listOf(Color(0xFF28363C), Color(0xFF141C1F), backgroundColor)
                },
                startY = 0f,
                endY = if (settings) size.height else size.height * .78f,
            ),
        )
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
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(42.dp).clickable(onClick = onSettings), contentAlignment = Alignment.CenterEnd) {
                OutlineIcon(FitIcon.SETTINGS, FitColors.White, 21.dp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val openReadiness = { onDetail(Detail.READINESS) }
            WhoopOverview(snapshot)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("DATA SYNCED AT ${snapshot.lastSync}", color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 7.sp, letterSpacing = 1.2.sp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).background(FitColors.Green, CircleShape))
            }
            Column(Modifier.padding(horizontal = 15.dp)) {
                WhoopInfoCard(
                    title = "YOUR DAILY BASELINE",
                    body = "Readiness combines sleep, HRV, and resting heart rate to show how prepared you are today.",
                    action = "VIEW READINESS",
                    onClick = openReadiness,
                )
                SectionLabel("Today's signals", topPadding = 22.dp, bottomPadding = 9.dp)
                WhoopMetricCard("SLEEP", formatSleepMinutes(snapshot.sleepMinutes), "${snapshot.sleepScore}% performance", FitIcon.MOON) { onDetail(Detail.SLEEP) }
                Spacer(Modifier.height(8.dp))
                WhoopMetricCard("STEPS", snapshot.steps.formatted(), "${snapshot.totalCalories.formatted()} kcal today", FitIcon.STEPS) { onDetail(Detail.STEPS) }
                Spacer(Modifier.height(8.dp))
                WhoopMetricCard("HEART RATE", "${snapshot.latestHeartRate} bpm", "${snapshot.restingHeartRate} bpm resting", FitIcon.HEART) { onDetail(Detail.HEART) }
                Spacer(Modifier.height(18.dp))
            }
        }
        BottomNav(Destination.TODAY, onDestination)
    }
}

@Composable
private fun WhoopOverview(snapshot: HealthSnapshot) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.width(74.dp), horizontalAlignment = Alignment.End) {
            Text(snapshot.readiness.toString(), color = FitColors.Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("READINESS", color = FitColors.Green, style = FitType.Eyebrow.copy(fontSize = 7.sp))
            Spacer(Modifier.height(14.dp))
            Text(metricValue(snapshot.hrv), color = FitColors.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("HRV", color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 7.sp))
        }
        Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
            val track = FitColors.Track
            val readiness = FitColors.Green
            val steps = FitColors.Cyan
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2f + 4.dp.toPx()
                val ringSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, -90f, 360f, false, Offset(inset, inset), ringSize, style = Stroke(stroke))
                drawArc(readiness, -90f, snapshot.readiness.coerceIn(0, 100) * 3.6f, false, Offset(inset, inset), ringSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                val innerInset = inset + 13.dp.toPx()
                val innerSize = androidx.compose.ui.geometry.Size(size.width - innerInset * 2, size.height - innerInset * 2)
                drawArc(track, -90f, 360f, false, Offset(innerInset, innerInset), innerSize, style = Stroke(5.dp.toPx()))
                val stepProgress = (snapshot.steps / 10_000f).coerceIn(0f, 1f)
                drawArc(steps, -90f, stepProgress * 360f, false, Offset(innerInset, innerInset), innerSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Butt))
            }
            Text("S/F", color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 13.sp, letterSpacing = 0.sp))
        }
        Column(Modifier.width(74.dp)) {
            Text(snapshot.steps.formatted(), color = FitColors.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("STEPS", color = FitColors.Cyan, style = FitType.Eyebrow.copy(fontSize = 7.sp))
            Spacer(Modifier.height(14.dp))
            Text("${snapshot.sleepScore}%", color = FitColors.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("SLEEP", color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 7.sp))
        }
    }
}

@Composable
private fun WhoopInfoCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(15.dp),
    ) {
        Text(title, color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 10.sp))
        Spacer(Modifier.height(8.dp))
        Text(body, color = FitColors.White, style = FitType.Body.copy(fontSize = 12.sp, lineHeight = 17.sp))
        Spacer(Modifier.height(11.dp))
        Text("$action  ›", color = FitColors.Green, style = FitType.Eyebrow.copy(fontSize = 9.sp))
    }
}

@Composable
private fun WhoopMetricCard(label: String, value: String, supporting: String, icon: FitIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlineIcon(icon, FitColors.Muted, 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 9.sp))
            Spacer(Modifier.height(3.dp))
            Text(supporting, color = FitColors.Muted, fontSize = 10.sp)
        }
        Text(value, color = FitColors.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Text("›", color = FitColors.White, fontSize = 22.sp)
    }
}

@Composable
private fun BottomNav(selected: Destination?, onDestination: (Destination) -> Unit) {
    Column(Modifier.background(Color(0xFF080D0F)).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(67.dp)) {
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
        Detail.HRV -> "HRV"
        Detail.RESTING_HEART_RATE -> "Resting HR"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader(title, "Thu, 13 Aug", onBack = onBack)
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
        ScoreRing(snapshot.readiness, "Readiness", FitColors.Green, size = 176.dp)
    }
    Text("Readiness combines HRV, recent sleep, and resting heart rate.", color = FitColors.White, style = FitType.Body)
    SectionLabel("Signals")
    DataRow(
        "Heart-rate variability",
        metricValue(snapshot.hrv),
        "ms · today",
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
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metricValue(value), color = FitColors.White, style = FitType.Display.copy(fontSize = 56.sp))
                Spacer(Modifier.width(9.dp))
                Text(unit, color = FitColors.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 9.dp))
            }
            Text(
                "TODAY",
                color = color,
                style = FitType.Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (readings.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 5.dp)) {
                Text("7-DAY AVG", color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metricValue(average), color = FitColors.Muted, style = FitType.Metric.copy(fontSize = 23.sp))
                    Spacer(Modifier.width(4.dp))
                    Text(unit, color = FitColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
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
        points.forEach { Text(it.label.take(1), color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp)) }
    }
}

@Composable
private fun SleepDetail(snapshot: HealthSnapshot) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
        SleepScoreRing(snapshot.sleepScore)
    }
    SleepDuration(snapshot)
    Spacer(Modifier.height(12.dp))
    SleepStages(snapshot)
    Spacer(Modifier.height(8.dp))
    SectionLabel("7 days", color = FitColors.Violet, topPadding = 16.dp, bottomPadding = 8.dp)
    SleepTrend(snapshot.sleepTrend)
    Spacer(Modifier.height(16.dp))
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
                    point.label.take(1),
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
        breakdown.rem?.let { SleepBreakdownItem("REM", it, FitIcon.MOON) },
        breakdown.deep?.let { SleepBreakdownItem("Deep", it, FitIcon.MOON) },
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
    DataRow("7-day average", averageSteps.formatted(), "steps")
    bestDay?.let { DataRow("Best day · ${it.label}", it.value.toInt().formatted(), "steps", FitColors.Cyan) }
}

@Composable
private fun HeartDetail(snapshot: HealthSnapshot) {
    Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center) {
        ScoreRing(72, "Latest bpm", FitColors.Coral, size = 176.dp)
    }
    Text("Fitbit sync, not live.", color = FitColors.Muted, style = FitType.Body.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    SectionLabel("Today", "synced 8:42")
    HeartChart()
    SectionLabel("Range")
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
        ScoreRing(73, "Daily total", FitColors.Cyan, size = 176.dp)
    }
    Text("${snapshot.totalCalories.formatted()} KCAL", color = FitColors.White, style = FitType.Metric, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    SectionLabel("7 days")
    SevenDayBars(snapshot.calorieTrend, FitColors.Cyan)
    SectionLabel("Today")
    DataRow("Active", snapshot.activeCalories.formatted(), "kcal", FitColors.Cyan)
    DataRow("Resting", snapshot.restingCalories.formatted(), "kcal")
    DataRow("7-day average", "2,241", "kcal")
}

@Composable
private fun CoachScreen(
    state: AppUiState,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
    onDestination: (Destination) -> Unit,
    onSettings: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var followOutput by remember(state.coachMessage) { mutableStateOf(true) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val starterSuggestions = listOf(
        "What should I focus on today?",
        "How am I doing compared to usual?",
        "Is there anything I should pay attention to?",
    )
    val followUpQuestions = state.coachSuggestions.takeIf { it.size == 3 } ?: run {
        listOf(
            "What stands out most for me?",
            "Is this normal for me?",
            "What should I keep an eye on?",
        )
    }
    val suggestions = when {
        state.coachBusy -> emptyList()
        state.coachPhase == CoachPhase.COMPLETE -> followUpQuestions
        state.coachMessage == null -> starterSuggestions
        else -> emptyList()
    }
    val sendMessage = {
        val message = input.trim()
        if (message.isNotEmpty()) {
            onAsk(message)
            input = ""
        }
    }
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) followOutput = !scrollState.canScrollForward
    }
    LaunchedEffect(state.coachReply, state.coachPhase) {
        if (followOutput) scrollState.scrollTo(scrollState.maxValue)
    }
    val providerReady = when (state.coachProvider) {
        CoachProvider.CODEX -> state.coachConnected
        CoachProvider.OPENROUTER -> state.openRouterConfigured
    }
    val connectionStatus = when {
        providerReady -> "Ready"
        state.coachProvider == CoachProvider.CODEX -> "Offline"
        else -> "Setup needed"
    }
    val coachModel = when (state.coachProvider) {
        CoachProvider.CODEX -> "Local Codex"
        CoachProvider.OPENROUTER -> "DeepSeek V4 Flash"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp)
                    .semantics { contentDescription = "New chat" }
                    .clickable(enabled = !state.coachBusy) {
                        input = ""
                        onNewChat()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                OutlineIcon(FitIcon.ADD, if (state.coachBusy) FitColors.Muted else FitColors.Green, 20.dp)
            }
            Text(
                "COACH",
                color = FitColors.White,
                style = FitType.Eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.8.sp),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Box(Modifier.size(42.dp).clickable(onClick = onSettings), contentAlignment = Alignment.CenterEnd) {
                OutlineIcon(FitIcon.SETTINGS, FitColors.White, 21.dp)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(horizontal = 22.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(if (providerReady) FitColors.Green else FitColors.Muted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                connectionStatus,
                color = if (providerReady) FitColors.White else FitColors.Muted,
                style = FitType.Body.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
            Text(" · ", color = FitColors.Muted, style = FitType.Body.copy(fontSize = 11.sp))
            Text(coachModel, color = FitColors.Muted, style = FitType.Body.copy(fontSize = 11.sp))
        }
        Column(Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(12.dp))
            state.coachTurns.forEach { turn ->
                CoachQuestion(turn.question)
                Spacer(Modifier.height(32.dp))
                CoachThinkingBlock(
                    phase = CoachPhase.COMPLETE,
                    busy = false,
                    reasoning = turn.reasoning,
                    durationMs = turn.durationMs,
                    expansionKey = turn,
                )
                Spacer(Modifier.height(20.dp))
                CoachResponseText(turn.answer)
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.height(40.dp))
            }
            if (state.coachMessage != null) {
                CoachQuestion(state.coachMessage)
            }
            if (state.coachMessage != null) Spacer(Modifier.height(32.dp))
            if (state.coachMessage != null && state.coachPhase != CoachPhase.IDLE) {
                CoachThinkingBlock(
                    phase = state.coachPhase,
                    busy = state.coachBusy,
                    reasoning = state.coachReasoning,
                    durationMs = state.coachDurationMs,
                    expansionKey = state.coachMessage,
                )
                state.coachReply?.takeIf(String::isNotBlank)?.let { reply ->
                    Spacer(Modifier.height(20.dp))
                    CoachResponseText(reply)
                }
                state.coachError?.let { error ->
                    Spacer(Modifier.height(18.dp))
                    Text(error, color = FitColors.Coral, style = FitType.Body)
                    if (state.coachRetryable) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "RETRY",
                            color = FitColors.Green,
                            style = FitType.Eyebrow,
                            modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 8.dp),
                        )
                    }
                }
            } else if (state.coachMessage == null && !providerReady) {
                Text(
                    when (state.coachProvider) {
                        CoachProvider.OPENROUTER -> "Add your OpenRouter API key in Settings to start."
                        CoachProvider.CODEX -> "Pair the Mac companion to start a conversation."
                    },
                    color = FitColors.Muted,
                    style = FitType.Body.copy(fontSize = 16.sp, lineHeight = 25.sp),
                    modifier = Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).padding(16.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
            }
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    Modifier.fillMaxWidth().clickable { input = suggestion }.padding(horizontal = 3.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString().padStart(2, '0'),
                        color = FitColors.Muted,
                        style = FitType.Eyebrow.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        suggestion,
                        color = FitColors.White,
                        style = FitType.Body.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("›", color = FitColors.Muted, fontSize = 22.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp, vertical = 11.dp)
                .background(Color(0xFF090E10), RoundedCornerShape(12.dp))
                .border(1.dp, FitColors.Rule, RoundedCornerShape(12.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (!state.coachBusy) input = it },
                enabled = !state.coachBusy,
                textStyle = FitType.Body.copy(color = FitColors.White),
                cursorBrush = SolidColor(FitColors.Green),
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    Box {
                        if (input.isBlank()) Text("Ask about your health", color = FitColors.Muted, style = FitType.Body)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).background(if (input.isBlank()) FitColors.Surface else FitColors.Green, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.coachBusy && input.isNotBlank()) { sendMessage() },
                contentAlignment = Alignment.Center,
            ) {
                OutlineIcon(FitIcon.SEND, if (input.isBlank()) FitColors.Muted else FitColors.Black, 21.dp)
            }
        }
        if (!imeVisible) BottomNav(Destination.COACH, onDestination)
    }
}

@Composable
private fun CoachQuestion(question: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            question,
            color = FitColors.White,
            style = FitType.Body,
            modifier = Modifier.background(FitColors.Surface, RoundedCornerShape(22.dp)).padding(horizontal = 19.dp, vertical = 15.dp),
        )
    }
}

@Composable
private fun CoachResponseText(response: String) {
    Text(
        coachMarkdown(response),
        color = FitColors.White,
        style = FitType.Body.copy(fontSize = 16.sp, lineHeight = 25.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun coachMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    var bold = false
    while (index < text.length) {
        val marker = text.indexOf("**", index).takeIf { it >= 0 } ?: text.length
        val segment = text.substring(index, marker)
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment) } else append(segment)
        if (marker == text.length) break
        bold = !bold
        index = marker + 2
    }
}

@Composable
private fun CoachThinkingBlock(
    phase: CoachPhase,
    busy: Boolean,
    reasoning: List<String>,
    durationMs: Long?,
    expansionKey: Any,
) {
    var expanded by remember(expansionKey) { mutableStateOf(busy) }
    LaunchedEffect(phase) {
        expanded = when (phase) {
            CoachPhase.COMPLETE, CoachPhase.ERROR -> false
            else -> true
        }
    }
    val canExpand = busy || reasoning.isNotEmpty()
    val durationSeconds = ((durationMs ?: 0L) / 1_000L).coerceAtLeast(1L)
    val label = when (phase) {
        CoachPhase.COMPLETE -> if (reasoning.isNotEmpty()) "Why this answer · ${durationSeconds}s" else "Generated in ${durationSeconds}s"
        CoachPhase.ERROR -> "Coach request interrupted"
        else -> "Coach progress"
    }
    Row(
        Modifier.clickable(enabled = canExpand) { expanded = !expanded }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (phase == CoachPhase.ERROR) {
                Box(Modifier.size(5.dp).background(FitColors.Coral, CircleShape))
            } else {
                CoachDisclosureArrow(expanded)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = FitColors.Muted, style = FitType.Body.copy(fontSize = 14.sp))
        if (busy && phase != CoachPhase.COMPLETE && phase != CoachPhase.ERROR) {
            Spacer(Modifier.width(8.dp))
            CoachProgressDots()
        }
    }
    if (!expanded) return

    if (phase == CoachPhase.COMPLETE) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(start = 9.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(FitColors.Rule))
            Column(Modifier.padding(start = 18.dp, top = 2.dp, bottom = 2.dp)) {
                reasoning.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp).size(5.dp).background(FitColors.Green, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(step, color = FitColors.White, style = FitType.Body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                    }
                    if (index != reasoning.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }
        }
    } else if (phase != CoachPhase.ERROR) {
        Spacer(Modifier.height(16.dp))
        CoachProgressTimeline(phase)
    }
}

@Composable
private fun CoachProgressDots() {
    val transition = rememberInfiniteTransition(label = "coach progress")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_150, easing = LinearEasing)),
        label = "progress dot cycle",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                Modifier.size(4.dp).graphicsLayer {
                    val phase = (cycle.value - index * .13f + 1f) % 1f
                    val bounce = if (phase < .52f) sin(phase / .52f * Math.PI).toFloat() else 0f
                    translationY = -4.dp.toPx() * bounce
                    alpha = .35f + .65f * bounce
                }.background(FitColors.Green, CircleShape),
            )
        }
    }
}

@Composable
private fun CoachDisclosureArrow(expanded: Boolean) {
    val color = FitColors.Muted
    Canvas(Modifier.size(14.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        if (expanded) {
            drawLine(color, Offset(size.width * .18f, size.height * .34f), Offset(size.width * .5f, size.height * .66f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * .5f, size.height * .66f), Offset(size.width * .82f, size.height * .34f), strokeWidth, StrokeCap.Round)
        } else {
            drawLine(color, Offset(size.width * .34f, size.height * .18f), Offset(size.width * .66f, size.height * .5f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * .66f, size.height * .5f), Offset(size.width * .34f, size.height * .82f), strokeWidth, StrokeCap.Round)
        }
    }
}

@Composable
private fun CoachProgressTimeline(phase: CoachPhase) {
    val activeIndex = when (phase) {
        CoachPhase.CONTEXT_READY -> 0
        CoachPhase.ANALYZING -> 1
        CoachPhase.WRITING -> 2
        CoachPhase.COMPLETE -> 3
        else -> 0
    }
    val labels = listOf(
        "Health data attached",
        when {
            activeIndex < 1 -> "Wait for Coach"
            activeIndex == 1 -> "Waiting for Coach"
            else -> "Coach responded"
        },
        when {
            activeIndex < 2 -> "Receive response"
            activeIndex == 2 -> "Receiving response"
            else -> "Response received"
        },
    )
    Column(Modifier.padding(start = 8.dp)) {
        labels.forEachIndexed { index, label ->
            CoachProgressRow(
                label = label,
                done = index < activeIndex,
                active = index == activeIndex,
                last = index == labels.lastIndex,
            )
        }
    }
}

@Composable
private fun CoachProgressRow(label: String, done: Boolean, active: Boolean, last: Boolean) {
    val markerColor = FitColors.Green
    val waitingColor = FitColors.Rule
    Row(Modifier.fillMaxWidth().height(if (last) 34.dp else 54.dp), verticalAlignment = Alignment.Top) {
        Canvas(Modifier.width(18.dp).fillMaxHeight()) {
            val center = Offset(size.width / 2f, 7.dp.toPx())
            if (!last) drawLine(waitingColor, center, Offset(center.x, size.height), 1.dp.toPx())
            when {
                done -> drawCircle(markerColor, 5.dp.toPx(), center)
                active -> {
                    drawCircle(markerColor, 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                    drawCircle(markerColor, 2.dp.toPx(), center)
                }
                else -> drawCircle(waitingColor, 4.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = when {
                active -> FitColors.White
                done -> FitColors.Muted
                else -> FitColors.Rule
            },
            style = FitType.Body.copy(fontSize = 14.sp),
        )
    }
}

private enum class SettingsPage { INDEX, GOOGLE_HEALTH, APPEARANCE, COACH, PRIVACY }

@Composable
private fun SettingsScreen(
    viewModel: AppViewModel,
    state: AppUiState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onDestination: (Destination) -> Unit,
) {
    val context = LocalContext.current
    val savedSetup = remember { viewModel.googleSetupCredentials() }
    var page by rememberSaveable { mutableStateOf(SettingsPage.INDEX) }
    var clientId by remember { mutableStateOf(savedSetup?.clientId.orEmpty()) }
    var clientSecret by remember { mutableStateOf(savedSetup?.clientSecret.orEmpty()) }
    var authorizationCode by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf("") }
    var openRouterKey by remember { mutableStateOf("") }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val title = when (page) {
        SettingsPage.INDEX -> "APP SETTINGS"
        SettingsPage.GOOGLE_HEALTH -> "GOOGLE HEALTH"
        SettingsPage.APPEARANCE -> "APPEARANCE"
        SettingsPage.COACH -> "COACH"
        SettingsPage.PRIVACY -> "PRIVACY & DATA"
    }
    val closePage = {
        if (page == SettingsPage.INDEX) onBack() else page = SettingsPage.INDEX
    }
    BackHandler(page != SettingsPage.INDEX) { page = SettingsPage.INDEX }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        SettingsHeader(title, closePage)
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                SettingsPage.INDEX -> {
                    Spacer(Modifier.height(27.dp))
                    SettingsMenuRow(FitIcon.TODAY, "GOOGLE HEALTH", if (state.googleConnected) "Connected · syncs every 6 hours" else "Not connected") { page = SettingsPage.GOOGLE_HEALTH }
                    Spacer(Modifier.height(9.dp))
                    SettingsMenuRow(FitIcon.COACH, "COACH", if (state.coachProvider == CoachProvider.OPENROUTER) "OpenRouter · DeepSeek V4 Flash" else "Local Codex") { page = SettingsPage.COACH }
                    Spacer(Modifier.height(9.dp))
                    SettingsMenuRow(FitIcon.SUN, "APPEARANCE", if (darkTheme) "Dark theme" else "Light theme") { page = SettingsPage.APPEARANCE }
                    Spacer(Modifier.height(9.dp))
                    SettingsMenuRow(FitIcon.WAVES, "PRIVACY & DATA", "30 days stored on device") { page = SettingsPage.PRIVACY }
                    SectionLabel("About", topPadding = 30.dp, bottomPadding = 11.dp)
                    SettingsMenuRow(FitIcon.MOON, "SCORES & METHODOLOGY", "How readiness and sleep are calculated") { page = SettingsPage.PRIVACY }
                    Text(
                        "SIMPLIFIED FIT  ·  VERSION 0.1.0",
                        color = FitColors.Muted,
                        style = FitType.Eyebrow.copy(fontSize = 8.sp),
                        modifier = Modifier.fillMaxWidth().padding(top = 31.dp, bottom = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                SettingsPage.GOOGLE_HEALTH -> {
                    SettingsStateLine("CONNECTION", if (state.googleConnected) "CONNECTED" else "NOT CONNECTED", state.googleConnected)
                    SectionLabel("Data source", topPadding = 25.dp, bottomPadding = 10.dp)
                    SettingsValueRow("STATUS", if (state.googleConnected) "Connected" else "Not connected")
                    Spacer(Modifier.height(8.dp))
                    SettingsValueRow("SYNC SCHEDULE", "Every 6 hours")
                    Spacer(Modifier.height(8.dp))
                    SettingsValueRow("HISTORY", "30 days")
                    if (!state.googleConnected) {
                        SectionLabel("Connection details", topPadding = 25.dp, bottomPadding = 0.dp)
                        SetupField("WEB CLIENT ID", clientId) { clientId = it }
                        SetupField("CLIENT SECRET", clientSecret, secure = true) { clientSecret = it }
                        SettingsAction("SAVE & OPEN GOOGLE CONSENT", FitColors.White, onClick = {
                            viewModel.prepareGoogleAuthorization(clientId, clientSecret)?.let { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        })
                        SetupField("PASTE REDIRECT URL OR CODE", authorizationCode) { authorizationCode = it }
                        SettingsAction(
                            label = if (state.syncing) "CONNECTING…" else "CONNECT GOOGLE HEALTH",
                            color = FitColors.Green,
                            onClick = { viewModel.connectGoogle(clientId, clientSecret, authorizationCode) },
                            loading = state.syncing,
                        )
                    } else {
                        Spacer(Modifier.height(24.dp))
                        SettingsAction(if (state.syncing) "SYNCING…" else "SYNC NOW", FitColors.Green, viewModel::sync, state.syncing)
                        SettingsAction("DISCONNECT", FitColors.Coral, viewModel::disconnectGoogle)
                    }
                }

                SettingsPage.APPEARANCE -> {
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("DARK THEME", color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 10.sp))
                            Spacer(Modifier.height(5.dp))
                            Text(if (darkTheme) "On" else "Off", color = FitColors.Muted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = { onToggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FitColors.White,
                                checkedTrackColor = FitColors.Green,
                                uncheckedThumbColor = FitColors.White,
                                uncheckedTrackColor = FitColors.Track,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                    Text(
                        "The dark theme matches the health dashboard and reduces glare at night.",
                        color = FitColors.Muted,
                        style = FitType.Body.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        modifier = Modifier.padding(top = 13.dp),
                    )
                }

                SettingsPage.COACH -> {
                    SettingsStateLine(
                        "COACH CONNECTION",
                        if (state.coachProvider == CoachProvider.OPENROUTER && state.openRouterConfigured || state.coachProvider == CoachProvider.CODEX && state.coachConnected) "READY" else "SETUP NEEDED",
                        state.openRouterConfigured || state.coachConnected,
                    )
                    SectionLabel("Configuration", topPadding = 25.dp, bottomPadding = 10.dp)
                    SettingsValueRow("PROVIDER", if (state.coachProvider == CoachProvider.OPENROUTER) "OpenRouter" else "Local Codex")
                    if (state.coachProvider == CoachProvider.OPENROUTER) {
                        Spacer(Modifier.height(8.dp))
                        SettingsValueRow("MODEL", "DeepSeek V4 Flash")
                        Spacer(Modifier.height(8.dp))
                        SettingsValueRow("API KEY", if (state.openRouterConfigured) "Saved securely" else "Not configured")
                        SetupField("OPENROUTER API KEY", openRouterKey, secure = true) { openRouterKey = it }
                        SettingsAction("SAVE API KEY", FitColors.Green, onClick = {
                            viewModel.saveOpenRouterApiKey(openRouterKey)
                            openRouterKey = ""
                        })
                        if (state.openRouterConfigured) SettingsAction("REMOVE API KEY", FitColors.Coral, viewModel::clearOpenRouterApiKey)
                        SettingsAction("USE LOCAL CODEX", FitColors.White, onClick = { viewModel.selectCoachProvider(CoachProvider.CODEX) })
                    } else {
                        Spacer(Modifier.height(8.dp))
                        SettingsValueRow("MAC COMPANION", if (state.coachConnected) "Connected" else "Not connected")
                        Spacer(Modifier.height(8.dp))
                        SettingsValueRow("NETWORK", "Tailscale")
                        SetupField("PASTE PAIRING DETAILS", pairing) { pairing = it }
                        SettingsAction("PAIR MAC COACH", FitColors.Green, onClick = { viewModel.pairCoach(pairing) })
                        SettingsAction("USE OPENROUTER", FitColors.White, onClick = { viewModel.selectCoachProvider(CoachProvider.OPENROUTER) })
                    }
                }

                SettingsPage.PRIVACY -> {
                    Spacer(Modifier.height(28.dp))
                    SettingsValueRow("HEALTH HISTORY", "30 days")
                    Spacer(Modifier.height(8.dp))
                    SettingsValueRow("PROCESSING", "On device")
                    Spacer(Modifier.height(8.dp))
                    SettingsValueRow("COACH CREDENTIALS", "Encrypted storage")
                    SectionLabel("Scores", topPadding = 28.dp, bottomPadding = 10.dp)
                    Column(Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).padding(15.dp)) {
                        Text("YOUR DATA STAYS YOURS", color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 10.sp))
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "Readiness and sleep scores are calculated locally from your sleep, HRV, and resting heart-rate signals. They are not medical scores.",
                            color = FitColors.White,
                            style = FitType.Body.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        )
                    }
                }
            }

            state.setupMessage?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    it,
                    color = if (it.contains("failed", true) || it.contains("could not", true)) FitColors.Coral else FitColors.Muted,
                    style = FitType.Body.copy(fontSize = 12.sp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        if (!imeVisible) BottomNav(selected = null, onDestination = onDestination)
    }
}

@Composable
private fun SettingsHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clickable(onClick = onClose), contentAlignment = Alignment.CenterStart) {
            Text("×", color = FitColors.White, fontSize = 33.sp, fontWeight = FontWeight.Light)
        }
        Text(
            title,
            color = FitColors.White,
            style = FitType.Eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.8.sp),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(42.dp))
    }
}

@Composable
private fun SettingsMenuRow(icon: FitIcon, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlineIcon(icon, FitColors.Muted, 22.dp)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 10.sp))
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = FitColors.Muted, fontSize = 10.sp)
            }
        }
        Text("›", color = FitColors.Muted, fontSize = 23.sp)
    }
}

@Composable
private fun SettingsStateLine(label: String, value: String, healthy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (healthy) FitColors.Green else FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 8.sp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 12.sp))
        }
        Box(Modifier.size(8.dp).background(if (healthy) FitColors.Green else FitColors.Coral, CircleShape))
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().background(FitColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FitColors.White, style = FitType.Eyebrow.copy(fontSize = 9.sp), modifier = Modifier.weight(1f))
        Text(value, color = FitColors.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SetupField(label: String, value: String, secure: Boolean = false, onValueChange: (String) -> Unit) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) bringIntoViewRequester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(label, color = FitColors.Muted, style = FitType.Eyebrow.copy(fontSize = 9.sp))
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = FitType.Body.copy(color = FitColors.White),
            cursorBrush = SolidColor(FitColors.Green),
            singleLine = true,
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .background(FitColors.Surface, RoundedCornerShape(4.dp))
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 13.dp),
        )
    }
}

@Composable
private fun SettingsAction(label: String, color: Color, onClick: () -> Unit, loading: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .border(1.dp, color, RoundedCornerShape(24.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, style = FitType.Eyebrow.copy(fontSize = 9.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (loading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = color, strokeWidth = 2.dp)
        }
    }
}
