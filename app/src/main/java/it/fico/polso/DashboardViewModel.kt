package it.fico.polso

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

enum class Status { CHECKING, UNAVAILABLE, NEEDS_PERMISSION, READY }

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)

    var status by mutableStateOf(Status.CHECKING)
        private set
    var day by mutableStateOf(LocalDate.now())
        private set
    var data by mutableStateOf<DayData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var granted by mutableStateOf<Set<String>>(emptySet())
        private set
    var showSettings by mutableStateOf(false)

    // --- storico ---
    var historyAnchor by mutableStateOf(LocalDate.now())
        private set
    var history by mutableStateOf<HistorySeries?>(null)
        private set
    var historyLoading by mutableStateOf(false)
        private set
    var historyUnavailable by mutableStateOf(false)
        private set

    // --- valutazione ---
    var coach by mutableStateOf<CoachResult?>(null)
        private set
    private var longHistory: List<DailyMetrics>? = null
    private var longHistoryAt = 0L
    private var coachJob: Job? = null

    private var repo: HealthRepo? = null
    private var loadJob: Job? = null
    private var historyJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (HealthConnectClient.getSdkStatus(ctx) != HealthConnectClient.SDK_AVAILABLE) {
                status = Status.UNAVAILABLE
                return@launch
            }
            val r = repo ?: HealthRepo(ctx).also { repo = it }
            granted = try {
                r.granted()
            } catch (e: Exception) {
                emptySet()
            }
            if (granted.none { it in HealthRepo.PERMISSIONS }) {
                status = Status.NEEDS_PERMISSION
                return@launch
            }
            status = Status.READY
            load()
            loadHistory()
            loadCoach()
        }
    }

    private fun load() {
        val r = repo ?: return
        val target = day
        val perms = granted
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            val result = try {
                r.load(target, perms)
            } catch (e: Exception) {
                null
            }
            if (target == day) data = result
            loading = false
        }
    }

    private fun loadHistory() {
        val r = repo ?: return
        val metric = settings.historyMetric
        val period = settings.historyPeriod
        val anchor = historyAnchor
        val perms = granted
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            historyLoading = true
            val (from, to) = periodRange(period, anchor)
            val daily = try {
                r.dailySeries(metric, from, to, perms)
            } catch (e: Exception) {
                null
            }
            historyUnavailable = daily == null
            history = daily?.let { buildSeries(metric, period, anchor, it) }
            historyLoading = false
        }
    }

    private fun loadCoach() {
        val r = repo ?: return
        val perms = granted
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // lo storico lungo si ricarica al massimo ogni 20 minuti
                val hist = longHistory?.takeIf { now - longHistoryAt < 20 * 60_000L }
                    ?: r.longHistory(perms).also {
                        longHistory = it
                        longHistoryAt = now
                    }
                val hourly = r.hourlyProfile(perms)
                val seed = settings.nextSeed()
                val goal = settings.stepGoal
                coach = withContext(Dispatchers.Default) {
                    Coach.evaluate(hist, hourly, goal, LocalDateTime.now(), seed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // la valutazione è un di più: se fallisce, il cruscotto resta com'è
            }
        }
    }

    fun shiftDay(delta: Long) {
        val next = day.plusDays(delta)
        if (next.isAfter(LocalDate.now())) return
        day = next
        load()
    }

    fun goToday() {
        day = LocalDate.now()
        load()
    }

    fun setHistory(metric: Metric = settings.historyMetric, period: HistoryPeriod = settings.historyPeriod) {
        if (period != settings.historyPeriod) historyAnchor = LocalDate.now()
        settings.setHistory(metric, period)
        loadHistory()
    }

    fun shiftHistory(delta: Long) {
        val next = shiftAnchor(settings.historyPeriod, historyAnchor, delta)
        val (from, _) = periodRange(settings.historyPeriod, next)
        if (from.isAfter(LocalDate.now())) return
        historyAnchor = if (next.isAfter(LocalDate.now())) LocalDate.now() else next
        loadHistory()
    }

    val canShiftHistoryForward: Boolean
        get() {
            val (_, to) = periodRange(settings.historyPeriod, historyAnchor)
            return to.isBefore(LocalDate.now())
        }

    val missingPermissions: Boolean
        get() = !granted.containsAll(HealthRepo.PERMISSIONS)

    val hasHistoryPermission: Boolean
        get() = HealthRepo.HISTORY_PERMISSION in granted
}
