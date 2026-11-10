package it.fico.polso

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Le schede del cruscotto, nell'ordine predefinito. */
enum class CardId(val label: String) {
    STEPS("Passi del giorno"),
    HISTORY("Storico"),
    WEEK("Ultimi 7 giorni"),
    HEART("Battito"),
    SLEEP("Sonno"),
    HRV("Variabilità cardiaca (HRV)"),
    SPO2("Ossigeno nel sangue"),
    RESPIRATION("Frequenza respiratoria"),
    WORKOUTS("Allenamenti"),
    TIPS("Consigli di Polso"),
}

/** Preferenze salvate sul telefono, osservabili da Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("polso", Context.MODE_PRIVATE)

    var stepGoal by mutableStateOf(prefs.getInt("goal", 8000))
        private set
    var order by mutableStateOf(readOrder())
        private set
    var hidden by mutableStateOf(prefs.getStringSet("hidden", emptySet())!!.toSet())
        private set
    var historyMetric by mutableStateOf(
        enumOrDefault(prefs.getString("histMetric", null), Metric.STEPS)
    )
        private set
    var historyPeriod by mutableStateOf(
        enumOrDefault(prefs.getString("histPeriod", null), HistoryPeriod.MONTH)
    )
        private set

    /** Contatore delle consultazioni: fa ruotare le frasi. */
    fun nextSeed(): Int {
        val n = prefs.getInt("seed", 0) + 1
        prefs.edit().putInt("seed", n).apply()
        return n
    }

    private fun readOrder(): List<CardId> {
        val saved = prefs.getString("order", null)
            ?.split(",")
            ?.mapNotNull { name -> CardId.values().firstOrNull { it.name == name } }
            ?: emptyList()
        // schede nuove aggiunte in coda
        return saved + CardId.values().filter { it !in saved }
    }

    fun setGoal(v: Int) {
        stepGoal = v
        prefs.edit().putInt("goal", v).apply()
    }

    fun toggle(card: CardId) {
        hidden = if (card.name in hidden) hidden - card.name else hidden + card.name
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    fun isVisible(card: CardId) = card.name !in hidden

    fun move(card: CardId, delta: Int) {
        val list = order.toMutableList()
        val i = list.indexOf(card)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list[i] = list[j]
        list[j] = card
        order = list
        prefs.edit().putString("order", list.joinToString(",") { it.name }).apply()
    }

    fun resetLayout() {
        order = CardId.values().toList()
        hidden = emptySet()
        prefs.edit().remove("order").remove("hidden").apply()
    }

    fun setHistory(metric: Metric, period: HistoryPeriod) {
        historyMetric = metric
        historyPeriod = period
        prefs.edit()
            .putString("histMetric", metric.name)
            .putString("histPeriod", period.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
