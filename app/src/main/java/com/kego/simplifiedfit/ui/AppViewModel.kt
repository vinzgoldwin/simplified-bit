package com.kego.simplifiedfit.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kego.simplifiedfit.SimplifiedFitApplication
import com.kego.simplifiedfit.data.CoachClient
import com.kego.simplifiedfit.data.CoachConnection
import com.kego.simplifiedfit.data.DailyHealth
import com.kego.simplifiedfit.data.GoogleCredentials
import com.kego.simplifiedfit.data.GoogleHealthClient
import com.kego.simplifiedfit.data.GoogleSetupCredentials
import com.kego.simplifiedfit.domain.ScoreCalculator
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import com.kego.simplifiedfit.domain.SleepSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class AppUiState(
    val snapshot: HealthSnapshot,
    val googleConnected: Boolean,
    val coachConnected: Boolean,
    val syncing: Boolean = false,
    val setupMessage: String? = null,
    val coachMessage: String? = null,
    val coachReply: String? = null,
    val coachBusy: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SimplifiedFitApplication
    var state by mutableStateOf(
        AppUiState(
            snapshot = HealthSnapshot.empty(),
            googleConnected = app.secureStore.googleCredentials() != null,
            coachConnected = app.secureStore.coachConnection()?.let { CoachClient(it).isHealthy() } == true,
        ),
    )
        private set

    init {
        val local = app.healthRepository.recent()
        if (local.isNotEmpty()) state = state.copy(snapshot = local.toSnapshot())
        if (state.googleConnected) sync()
    }

    fun sync() {
        if (!state.googleConnected || state.syncing) return
        viewModelScope.launch {
            state = state.copy(syncing = true, setupMessage = null)
            runCatching { withContext(Dispatchers.IO) { app.healthRepository.sync() } }
                .onSuccess { state = state.copy(snapshot = it.toSnapshot(), syncing = false, setupMessage = "Google Health synced") }
                .onFailure { state = state.copy(syncing = false, setupMessage = it.message ?: "Sync failed") }
        }
    }

    fun googleSetupCredentials(): GoogleSetupCredentials? = app.secureStore.googleSetupCredentials()

    fun prepareGoogleAuthorization(clientId: String, clientSecret: String): String? {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            state = state.copy(setupMessage = "Enter the client ID and secret first")
            return null
        }
        val setup = GoogleSetupCredentials(clientId.trim(), clientSecret.trim())
        app.secureStore.saveGoogleSetupCredentials(setup)
        state = state.copy(setupMessage = "Credentials saved. Complete Google consent in your browser.")
        return GoogleHealthClient.authorizationUrl(setup.clientId)
    }

    fun connectGoogle(clientId: String, clientSecret: String, authorizationCode: String) {
        if (clientId.isBlank() || clientSecret.isBlank() || authorizationCode.isBlank()) {
            state = state.copy(setupMessage = "Client ID, secret, and authorization code are required")
            return
        }
        viewModelScope.launch {
            state = state.copy(syncing = true, setupMessage = "Connecting Google Health…")
            runCatching {
                withContext(Dispatchers.IO) {
                    val temporary = GoogleCredentials(clientId.trim(), clientSecret.trim(), "")
                    val credentials = GoogleHealthClient(temporary).exchangeAuthorizationCode(authorizationCode.trim())
                    app.secureStore.saveGoogleCredentials(credentials)
                    app.secureStore.clearGoogleSetupCredentials()
                    app.healthRepository.sync()
                }
            }.onSuccess {
                state = state.copy(
                    snapshot = it.toSnapshot(),
                    googleConnected = true,
                    syncing = false,
                    setupMessage = "Google Health connected",
                )
            }.onFailure {
                state = state.copy(syncing = false, setupMessage = it.message ?: "Could not connect Google Health")
            }
        }
    }

    fun disconnectGoogle() {
        app.secureStore.clearGoogleCredentials()
        state = state.copy(googleConnected = false, setupMessage = "Google Health disconnected")
    }

    fun pairCoach(pairingText: String) {
        val parts = pairingText.trim().split('|', limit = 2)
        if (parts.size != 2 || !parts[0].startsWith("http")) {
            state = state.copy(setupMessage = "Pairing details should look like http://host:7447|token")
            return
        }
        val connection = CoachConnection(parts[0], parts[1])
        viewModelScope.launch {
            val healthy = withContext(Dispatchers.IO) { CoachClient(connection).isHealthy() }
            if (healthy) {
                app.secureStore.saveCoachConnection(connection)
                state = state.copy(coachConnected = true, setupMessage = "Mac Coach connected")
            } else {
                state = state.copy(coachConnected = false, setupMessage = "Could not reach Mac Coach")
            }
        }
    }

    fun askCoach(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank() || state.coachBusy) return

        val connection = app.secureStore.coachConnection()
        state = state.copy(coachMessage = trimmedMessage, coachReply = null, coachBusy = connection != null)
        if (connection == null) {
            state = state.copy(coachBusy = false, coachReply = "Pair the Mac companion in Settings first.")
            return
        }
        viewModelScope.launch {
            state = state.copy(coachBusy = true, coachReply = null)
            runCatching {
                withContext(Dispatchers.IO) { CoachClient(connection).ask(trimmedMessage, state.snapshot.coachContext) }
            }.onSuccess {
                state = state.copy(coachBusy = false, coachReply = it, coachConnected = true)
            }.onFailure {
                state = state.copy(coachBusy = false, coachReply = it.message ?: "Coach unavailable", coachConnected = false)
            }
        }
    }

    private fun List<DailyHealth>.toSnapshot(): HealthSnapshot {
        if (isEmpty()) return HealthSnapshot.empty()
        val byDate = associateBy { it.date }
        val today = LocalDate.now()
        val current = byDate[today] ?: first()
        fun sleepBreakdown(day: DailyHealth): SleepScoreBreakdown? {
            val asleep = day.asleepMinutes ?: return null
            val inBed = day.inBedMinutes ?: return null
            return ScoreCalculator.sleepBreakdown(
                SleepSignals(
                    asleepMinutes = asleep,
                    targetMinutes = 480,
                    inBedMinutes = inBed,
                    remMinutes = day.remMinutes,
                    deepMinutes = day.deepMinutes,
                    awakeMinutes = day.awakeMinutes,
                    restlessnessMinutes = day.restlessnessMinutes,
                ),
            )
        }
        val sleepBreakdowns = associate { day -> day.date to sleepBreakdown(day) }
        val currentSleepBreakdown = sleepBreakdowns[current.date]
        fun trend(value: (DailyHealth) -> Float?): List<DayPoint> = (6 downTo 0).mapNotNull { offset ->
            val date = today.minusDays(offset.toLong())
            value(byDate[date] ?: return@mapNotNull null)?.let {
                DayPoint(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH), it)
            }
        }
        val calories = current.totalCalories?.roundToInt() ?: 0
        val active = current.activeCalories?.roundToInt() ?: 0
        val heartValues = mapNotNull { it.latestHeartRate }
        return HealthSnapshot(
            readiness = current.readinessScore ?: 0,
            sleepScore = currentSleepBreakdown?.total ?: 0,
            steps = current.steps ?: 0,
            latestHeartRate = current.latestHeartRate ?: current.restingHeartRate?.roundToInt() ?: 0,
            restingHeartRate = current.restingHeartRate?.roundToInt() ?: 0,
            lowHeartRate = heartValues.minOrNull() ?: 0,
            highHeartRate = heartValues.maxOrNull() ?: 0,
            totalCalories = calories,
            activeCalories = active,
            restingCalories = (calories - active).coerceAtLeast(0),
            sleepMinutes = current.asleepMinutes ?: 0,
            sleepTargetMinutes = 480,
            awakeMinutes = current.awakeMinutes ?: 0,
            restlessnessMinutes = current.restlessnessMinutes ?: 0,
            remMinutes = current.remMinutes ?: 0,
            lightMinutes = current.lightMinutes ?: 0,
            deepMinutes = current.deepMinutes ?: 0,
            sleepBreakdown = currentSleepBreakdown ?: SleepScoreBreakdown(),
            lastSync = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("H:mm")),
            validNights = count { (it.asleepMinutes ?: 0) >= 180 },
            stepTrend = trend { it.steps?.toFloat() },
            sleepTrend = trend { sleepBreakdowns[it.date]?.total?.toFloat() },
            hrv = current.hrv ?: 0.0,
            hrvTrend = trend { it.hrv?.toFloat() },
            restingHeartRateTrend = trend { it.restingHeartRate?.toFloat() },
            calorieTrend = trend { it.totalCalories?.toFloat() },
            coachContext = buildCoachContext(this, current, currentSleepBreakdown),
        )
    }
}
