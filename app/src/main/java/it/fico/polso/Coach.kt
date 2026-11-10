package it.fico.polso

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Valori di una giornata usati per la valutazione (null = non misurato). */
data class DailyMetrics(
    val date: LocalDate,
    val steps: Long?,
    val sleepHours: Double?,
    val bedtimeFrom18: Int?,   // minuti dalle 18:00 all'addormentamento
    val restingHr: Double?,
    val hrv: Double?,
    val spo2Min: Double?,
)

data class Tip(val title: String, val text: String, val color: Color)

data class CoachResult(
    val phrase: String,
    val score: Double?,          // indice di lungo periodo, ~0..1,2 (1 = in linea con gli obiettivi)
    val recentScore: Double?,    // media ultimi 7 giorni
    val daysOfData: Int,
    val background: Color,
    val tips: List<Tip>,
)

/**
 * Il "coach" di Polso: calcola un indice composto giornaliero, lo media
 * con pesi che decadono esponenzialmente (dimezzamento a 45 e 180 giorni)
 * e sceglie una frase e, se c'è qualcosa di notevole, qualche consiglio.
 */
object Coach {

    const val MIN_DAYS = 10        // sotto: "in calibrazione"
    const val MIN_DAYS_TIPS = 21   // sotto: niente consigli

    // ------------------------------------------------------------------ indice

    /** Punteggio del sonno: pieno tra 7 e 9 ore. */
    private fun sleepScore(h: Double): Double = when {
        h < 4.0 -> 0.0
        h < 7.0 -> (h - 4.0) / 3.0
        h <= 9.0 -> 1.0
        h <= 11.0 -> 1.0 - (h - 9.0) * 0.15
        else -> 0.7
    }

    /** Mediana dei valori validi negli [n] giorni precedenti a [i]. */
    private fun baseline(days: List<DailyMetrics>, i: Int, n: Int, pick: (DailyMetrics) -> Double?): Double? {
        val vals = (maxOf(0, i - n) until i).mapNotNull { pick(days[it]) }
        if (vals.size < 5) return null
        val s = vals.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /**
     * Indice composto di ogni giorno: passi 55%, sonno 25%, cuore 20%
     * (battito a riposo e HRV rispetto alla propria mediana di 30 giorni).
     * I pesi si ridistribuiscono se manca una componente. SpO₂ minima
     * sotto il 90% riduce l'indice.
     */
    fun dailyIndex(days: List<DailyMetrics>, goal: Int): Map<LocalDate, Double> {
        val out = mutableMapOf<LocalDate, Double>()
        days.forEachIndexed { i, d ->
            var sum = 0.0
            var w = 0.0
            d.steps?.let {
                sum += 0.55 * (it.toDouble() / goal).coerceAtMost(1.5)
                w += 0.55
            }
            d.sleepHours?.let {
                sum += 0.25 * sleepScore(it)
                w += 0.25
            }
            val rBase = baseline(days, i, 30) { it.restingHr }
            val hBase = baseline(days, i, 30) { it.hrv }
            val heartParts = mutableListOf<Double>()
            if (d.restingHr != null && rBase != null) {
                // ogni bpm sopra la propria norma toglie il 10%; sotto la norma piccolo bonus
                heartParts += (1.0 - (d.restingHr - rBase) / 10.0).coerceIn(0.0, 1.2)
            }
            if (d.hrv != null && hBase != null && hBase > 0) {
                heartParts += (d.hrv / hBase).coerceIn(0.0, 1.2)
            }
            if (heartParts.isNotEmpty()) {
                sum += 0.20 * heartParts.average()
                w += 0.20
            }
            // un giorno con soli dati cardiaci non basta per un giudizio
            if (w >= 0.25) {
                var idx = sum / w
                val m = d.spo2Min
                if (m != null && m < 85) {
                    idx *= 0.8
                } else if (m != null && m < 90) {
                    idx *= 0.9
                }
                out[d.date] = idx
            }
        }
        return out
    }

    /** Media pesata con peso 0,5^(età/dimezzamento), su tutto lo storico. */
    private fun decayedMean(idx: Map<LocalDate, Double>, ref: LocalDate, halfLife: Double): Double? {
        var num = 0.0
        var den = 0.0
        idx.forEach { (d, v) ->
            val age = ChronoUnit.DAYS.between(d, ref).toDouble()
            if (age >= 0) {
                val wt = 0.5.pow(age / halfLife)
                num += wt * v
                den += wt
            }
        }
        return if (den > 0) num / den else null
    }

    // ------------------------------------------------------------------ colore

    private val stops = listOf(
        0.30 to Color(0xFF2B0716), // magenta-rosso scurissimo
        0.50 to Color(0xFF1E0B14),
        0.70 to Color(0xFF111317), // antracite neutro
        0.90 to Color(0xFF0B1711),
        1.10 to Color(0xFF071C10), // verde bosco quasi nero
    )

    fun backgroundFor(score: Double?): Color {
        if (score == null) return Palette.Bg
        if (score <= stops.first().first) return stops.first().second
        if (score >= stops.last().first) return stops.last().second
        for (k in 0 until stops.size - 1) {
            val (a, ca) = stops[k]
            val (b, cb) = stops[k + 1]
            if (score in a..b) return lerp(ca, cb, ((score - a) / (b - a)).toFloat())
        }
        return Palette.Bg
    }

    // ------------------------------------------------------------------ valutazione

    fun evaluate(
        history: List<DailyMetrics>,
        hourly: Map<LocalDate, List<Long>>,
        goal: Int,
        now: java.time.LocalDateTime,
        seed: Int,
    ): CoachResult {
        val today = now.toLocalDate()
        val hour = now.hour
        val past = history.filter { it.date.isBefore(today) }
        val idx = dailyIndex(history, goal).filterKeys { it.isBefore(today) }
        val daysOfData = idx.size
        val yesterday = today.minusDays(1)

        val score = if (daysOfData >= MIN_DAYS) {
            val a = decayedMean(idx, yesterday, 45.0)
            val b = decayedMean(idx, yesterday, 180.0)
            if (a != null && b != null) 0.65 * a + 0.35 * b else a ?: b
        } else null
        val recent = idx.filterKeys { ChronoUnit.DAYS.between(it, today) <= 7 }.values
            .takeIf { it.size >= 3 }?.average()

        // ----- oggi
        val todayHours = hourly[today] ?: emptyList()
        val todaySteps = todayHours.sum()
        val pastProfiles = hourly.filterKeys { it.isBefore(today) }.values
            .filter { it.sum() > 500 } // giorni in cui la band era al polso
        val typicalByNow = pastProfiles
            .map { it.take(hour + 1).sum() }
            .sorted()
            .let { if (it.size >= 5) it[it.size / 2] else null }
        val pace = typicalByNow?.takeIf { it > 200 }?.let { todaySteps.toDouble() / it }
        val left = (goal - todaySteps).coerceAtLeast(0L)

        val todayM = history.firstOrNull { it.date == today }
        val lastSleep = todayM?.sleepHours
        val restBase = past.takeLast(30).mapNotNull { it.restingHr }.takeIf { it.size >= 5 }?.sorted()?.let { it[it.size / 2] }
        val todayRest = todayM?.restingHr
        val restDelta = if (todayRest != null && restBase != null) todayRest - restBase else null
        val lastSpo2 = todayM?.spo2Min

        val stepDays = past.filter { (it.steps ?: 0L) > 0L }
        val record = stepDays.maxOfOrNull { it.steps ?: 0L } ?: 0L

        fun streak(pred: (Long) -> Boolean): Int {
            var n = 0
            var d = yesterday
            while (true) {
                val s = past.firstOrNull { it.date == d }?.steps ?: break
                if (!pred(s)) break
                n++
                d = d.minusDays(1)
            }
            return n
        }
        val streakUp = streak { it >= goal }
        val streakDown = streak { it < goal * 0.6 }

        val ctx = PhraseContext(
            steps = todaySteps, left = left, goal = goal, hour = hour,
            typical = typicalByNow, sleep = lastSleep, restDelta = restDelta,
            spo2 = lastSpo2, record = record,
        )

        val category = when {
            hour < 5 -> Cat.NIGHT
            daysOfData < MIN_DAYS -> Cat.CALIBRATING
            lastSpo2 != null && lastSpo2 < 90 -> Cat.SPO2_LOW
            restDelta != null && restDelta >= 7 -> Cat.RECOVERY
            record > 0 && todaySteps > record && stepDays.size >= 14 -> Cat.RECORD
            todaySteps >= goal && hour < 15 -> Cat.GOAL_EARLY
            todaySteps >= goal -> Cat.GOAL_DONE
            hour < 11 && lastSleep != null && lastSleep < 5.5 -> Cat.SHORT_SLEEP
            hour < 11 && lastSleep != null && lastSleep >= 7.5 -> Cat.GOOD_SLEEP
            hour >= 19 && left <= goal * 0.25 -> Cat.EVENING_CLOSE
            hour >= 20 && todaySteps < goal * 0.5 -> Cat.EVENING_FAR
            hour >= 11 && pace != null && pace < 0.5 -> Cat.BEHIND
            hour >= 10 && pace != null && pace > 1.3 -> Cat.AHEAD
            streakDown >= 3 -> Cat.STREAK_DOWN
            streakUp >= 3 -> Cat.STREAK_UP
            recent != null && score != null && recent > score + 0.12 -> Cat.TREND_UP
            recent != null && score != null && recent < score - 0.12 -> Cat.TREND_DOWN
            score != null && score >= 0.9 -> Cat.GENERIC_GOOD
            score != null && score < 0.55 -> Cat.GENERIC_BAD
            else -> Cat.GENERIC_MID
        }
        val n = when (category) {
            Cat.STREAK_DOWN -> streakDown
            Cat.STREAK_UP -> streakUp
            Cat.RECOVERY -> restDelta?.toInt() ?: 0
            else -> 0
        }
        val phrase = Phrases.pick(category, seed, ctx.copy(n = n))

        val tips = if (daysOfData >= MIN_DAYS_TIPS) tips(history, hourly, goal, today) else emptyList()

        return CoachResult(
            phrase = phrase,
            score = score,
            recentScore = recent,
            daysOfData = daysOfData,
            background = backgroundFor(score),
            tips = tips,
        )
    }

    // ------------------------------------------------------------------ consigli

    private fun fmt(v: Long) = String.format(Locale.ITALY, "%,d", v)
    private fun fmtH(h: Double): String {
        val m = Math.round(h * 60)
        return "${m / 60} h ${"%02d".format(m % 60)}"
    }

    /** Consigli solo quando c'è qualcosa che lo merita; al massimo due. */
    private fun tips(
        history: List<DailyMetrics>,
        hourly: Map<LocalDate, List<Long>>,
        goal: Int,
        today: LocalDate,
    ): List<Tip> {
        val out = mutableListOf<Tip>()
        val past = history.filter { it.date.isBefore(today) }
        fun window(fromDaysAgo: Long, toDaysAgo: Long) =
            past.filter { ChronoUnit.DAYS.between(it.date, today) in toDaysAgo..fromDaysAgo }

        val last7 = window(7, 1)
        val last14 = window(14, 1)
        val last30 = window(30, 1)
        val prev90 = window(120, 31)
        val longSteps = window(180, 8).mapNotNull { it.steps }.filter { it > 0 }
        val longAvg = longSteps.takeIf { it.size >= 14 }?.average()
        val avg7 = last7.mapNotNull { it.steps }.filter { it > 0 }.takeIf { it.size >= 4 }?.average()

        // 1. ossigeno notturno basso ripetuto
        val lowNights = last14.count { (it.spo2Min ?: 100.0) < 90 }
        if (lowNights >= 3) {
            out += Tip(
                "Ossigeno di notte",
                "In $lowNights notti delle ultime due settimane la SpO₂ è scesa sotto il 90%. Può dipendere da come " +
                    "porti la band: allacciala un dito sopra l'osso del polso. Se però russi o ti svegli stanco, " +
                    "è un dato da portare al medico, non da interpretare da soli.",
                Palette.Spo2,
            )
        }

        // 2. battito a riposo in salita
        val rest7 = last7.mapNotNull { it.restingHr }
        val rest30 = window(37, 8).mapNotNull { it.restingHr }
        if (rest7.size >= 4 && rest30.size >= 10) {
            val delta = rest7.sorted()[rest7.size / 2] - rest30.sorted()[rest30.size / 2]
            if (delta >= 5) {
                out += Tip(
                    "Il cuore a riposo è salito",
                    "Nell'ultima settimana il battito a riposo è di circa ${delta.toInt()} bpm sopra la tua norma. " +
                        "Di solito c'entrano poco sonno, stress, alcol la sera o un'influenza in arrivo. Qualche giorno " +
                        "più leggero aiuta; se resta alto per più di due settimane, parlane col medico.",
                    Palette.Resting,
                )
            }
        }

        // 3. settimana ferma
        if (avg7 != null && longAvg != null && avg7 < longAvg * 0.6) {
            val profile = hourly.filterKeys { it.isBefore(today) }.values.filter { it.sum() > 500 }
            val quietHour = if (profile.size >= 7) {
                (9..19).minByOrNull { h -> profile.map { it[h] }.average() }
            } else null
            out += Tip(
                "Una settimana ferma",
                "Negli ultimi 7 giorni hai fatto in media ${fmt(avg7.toLong())} passi, contro i tuoi soliti " +
                    "${fmt(longAvg.toLong())}. " +
                    (if (quietHour != null) "La tua ora più immobile è quella delle $quietHour: un quarto d'ora a piedi lì " +
                        "rende più che altrove, perché spezza la sedentarietà dove è più lunga. " else "") +
                    "Non serve recuperare tutto in un giorno: basta invertire la tendenza.",
                Palette.Kcal,
            )
        }

        // 4. attività fuori dal comune ieri
        val y = past.lastOrNull { it.date == today.minusDays(1) }?.steps
        if (y != null && longAvg != null && y > longAvg * 1.8 && y > goal) {
            out += Tip(
                "Ieri hai esagerato (bene)",
                "Ieri ${fmt(y)} passi, quasi il doppio del tuo solito. Ottimo, ma il giorno dopo un carico così " +
                    "conviene ascoltare il corpo: se stamattina il battito a riposo è più alto di qualche battito, " +
                    "oggi camminata tranquilla e acqua. Se è normale, sei più allenato di quanto credi.",
                Palette.Steps,
            )
        }

        // 5. debito di sonno
        val sleep7 = last7.mapNotNull { it.sleepHours }
        if (sleep7.size >= 5 && sleep7.average() < 6.25) {
            out += Tip(
                "Debito di sonno",
                "Nell'ultima settimana hai dormito in media ${fmtH(sleep7.average())} a notte. Sotto le 7 ore " +
                    "il recupero ne risente, e con lui la voglia di muoversi. Un orario fisso per andare a letto " +
                    "vale più di qualsiasi integratore.",
                Palette.Sleep,
            )
        }

        // 6. orari irregolari
        val beds = last14.mapNotNull { it.bedtimeFrom18 }
        if (beds.size >= 8) {
            val mean = beds.average()
            val sd = sqrt(beds.map { (it - mean).pow(2) }.average())
            if (sd > 70) {
                out += Tip(
                    "Orari ballerini",
                    "Vai a letto a orari molto diversi: lo scarto tipico è di circa ${sd.toInt()} minuti. Il corpo " +
                        "ama la routine; ridurre lo scarto migliora il sonno anche a parità di ore.",
                    Palette.Sleep,
                )
            }
        }

        // 7. feriali contro weekend
        val eight = window(56, 1).filter { (it.steps ?: 0L) > 0L }
        val we = eight.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
            .mapNotNull { it.steps }
        val wd = eight.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
            .mapNotNull { it.steps }
        if (we.size >= 6 && wd.size >= 15) {
            val a = we.average()
            val b = wd.average()
            if (a < b * 0.65) {
                out += Tip(
                    "Il weekend ti ferma",
                    "Nel fine settimana cammini il ${((1 - a / b) * 100).toInt()}% in meno che nei giorni feriali. " +
                        "Il sabato è il giorno giusto per una camminata lunga senza orologio: niente orari, solo strada.",
                    Palette.Distance,
                )
            } else if (b < a * 0.65) {
                out += Tip(
                    "I giorni feriali ti inchiodano",
                    "Dal lunedì al venerdì fai il ${((1 - b / a) * 100).toInt()}% di passi in meno rispetto al weekend. " +
                        "Telefonate camminando e pausa pranzo a piedi sono i due trucchi che costano meno.",
                    Palette.Distance,
                )
            }
        }

        // 8. miglioramento consolidato
        val s30 = last30.mapNotNull { it.steps }.filter { it > 0 }
        val s90 = prev90.mapNotNull { it.steps }.filter { it > 0 }
        if (s30.size >= 20 && s90.size >= 45 && s30.average() > s90.average() * 1.25) {
            out += Tip(
                "Abitudine nuova",
                "Nell'ultimo mese cammini il ${((s30.average() / s90.average() - 1) * 100).toInt()}% in più dei tre mesi " +
                    "prima. Non è un caso, è un'abitudine che si sta formando: proteggila dalle settimane storte.",
                Palette.Steps,
            )
        }

        return out.take(2)
    }
}

enum class Cat {
    NIGHT, CALIBRATING, SPO2_LOW, RECOVERY, RECORD, GOAL_EARLY, GOAL_DONE, SHORT_SLEEP, GOOD_SLEEP,
    EVENING_CLOSE, EVENING_FAR, BEHIND, AHEAD, STREAK_DOWN, STREAK_UP, TREND_UP, TREND_DOWN,
    GENERIC_GOOD, GENERIC_MID, GENERIC_BAD,
}

data class PhraseContext(
    val steps: Long,
    val left: Long,
    val goal: Int,
    val hour: Int,
    val typical: Long?,
    val sleep: Double?,
    val restDelta: Double?,
    val spo2: Double?,
    val record: Long,
    val n: Int = 0,
)

/** Frasi: ironiche, schiette, all'occorrenza ruvide. */
object Phrases {
    private val pool: Map<Cat, List<String>> = mapOf(
        Cat.NIGHT to listOf(
            "È notte fonda. L'unico obiettivo adesso è dormire.",
            "A quest'ora i passi non contano. Il sonno sì.",
            "Il cruscotto può aspettare. Il cuscino no.",
        ),
        Cat.CALIBRATING to listOf(
            "Ti sto ancora studiando. Tra qualche giorno smetto di essere educato.",
            "Pochi dati per giudicarti. Approfittane finché dura.",
            "Fase di osservazione: prendo appunti. Il verdetto arriva presto.",
        ),
        Cat.SPO2_LOW to listOf(
            "Stanotte l'ossigeno è sceso al {spo2}%. Una volta è un dato; se si ripete, è un discorso da fare col medico.",
            "SpO₂ notturna al {spo2}%. Controlla come porti la band; se ricapita, parlane col medico.",
        ),
        Cat.RECOVERY to listOf(
            "Battito a riposo più alto del solito di {n} bpm. Oggi il corpo chiede tregua, non record.",
            "Cuore su di giri a riposo (+{n} bpm). Giornata da passeggiata, non da impresa.",
            "Il battito a riposo dice stanchezza. Dormi, bevi, e l'eroismo rimandalo.",
        ),
        Cat.RECORD to listOf(
            "{steps} passi: mai fatti tanti. Domani i polpacci te lo ricorderanno.",
            "Nuovo record personale. Il divano è in lutto.",
            "Hai battuto te stesso. Era ora che qualcuno lo facesse.",
        ),
        Cat.GOAL_EARLY to listOf(
            "Obiettivo chiuso prima delle {h}. Il resto della giornata è tutto margine.",
            "Quota fatta e non è nemmeno pomeriggio. Chi sei, e cosa ne hai fatto di te?",
            "{steps} passi prima delle {h}. Oggi si gioca in attacco.",
        ),
        Cat.GOAL_DONE to listOf(
            "Obiettivo fatto. Tutto quello che viene adesso è gratis.",
            "{steps} passi, quota raggiunta. Puoi sederti con la coscienza pulita, per una volta.",
            "Obiettivo superato. Non montarti la testa. Un po' sì, dai.",
            "Missione compiuta. Il divano stasera te lo sei guadagnato.",
        ),
        Cat.SHORT_SLEEP to listOf(
            "{sleep} di sonno. Oggi niente prove di forza: cammina, ma con giudizio.",
            "Hai dormito {sleep}. Il caffè non è un piano di recupero.",
            "Notte corta ({sleep}). Una passeggiata all'aria aperta vale più del terzo caffè.",
        ),
        Cat.GOOD_SLEEP to listOf(
            "{sleep} di sonno vero. Oggi non hai scuse.",
            "Dormito come si deve. Adesso dimostralo con le gambe.",
            "Batterie cariche ({sleep}). Sarebbe un peccato sprecarle sulla sedia.",
        ),
        Cat.EVENING_CLOSE to listOf(
            "Mancano {left} passi. Un giro dell'isolato e chiudi in pari.",
            "{left} passi all'obiettivo: è il giro del palazzo, non il Cammino di Santiago.",
            "Sei a un passo, anzi a {left}. Portali a casa.",
        ),
        Cat.EVENING_FAR to listOf(
            "Giornata quasi andata con {steps} passi. Archivia e domani riparti, senza drammi e senza alibi.",
            "Bilancio magro stasera: {steps} passi. Domani si recupera. Forse.",
            "Oggi le scarpe hanno lavorato meno di te alla scrivania. Domani si cambia.",
        ),
        Cat.BEHIND to listOf(
            "Alle {h} sei a {steps} passi; di solito a quest'ora sei a {typical}. La sedia ringrazia, tu meno.",
            "Ritmo da bradipo in ferie: {steps} passi. Alzati, anche solo per fare finta.",
            "Sei sotto la tua media di quest'ora. Dieci minuti a piedi e il grafico smette di vergognarsi.",
            "{steps} passi alle {h}. Il pavimento si chiede se esisti.",
            "Oggi le tue scarpe riposano più di te. Mancano {left} passi.",
        ),
        Cat.AHEAD to listOf(
            "Alle {h} sei già a {steps}, sopra il tuo solito. Continua così.",
            "Ritmo sopra la media: oggi dentro le tue scarpe c'è un altro. Tienitelo.",
            "Stai andando forte. Non fermarti proprio adesso che il grafico si diverte.",
        ),
        Cat.STREAK_DOWN to listOf(
            "{n} giorni di fila sotto obiettivo. Non è una pausa, sta diventando un'abitudine.",
            "{n} giorni da soprammobile. Oggi rompi la serie, anche con poco.",
            "{n} giorni di stasi. Il corpo si adatta a tutto, anche al peggio: non dargli l'idea.",
        ),
        Cat.STREAK_UP to listOf(
            "{n} giorni di fila a obiettivo. Non spezzare la catena proprio oggi.",
            "Serie aperta da {n} giorni. Ormai camminare è un vizio: tienilo.",
            "{n} giorni consecutivi. La costanza è noiosa, ma funziona.",
        ),
        Cat.TREND_UP to listOf(
            "Ultima settimana sopra la tua media. Qualcosa sta cambiando, e in meglio.",
            "Il trend sale. Continua così e lo sfondo vira verso il bosco.",
        ),
        Cat.TREND_DOWN to listOf(
            "Ultima settimana sotto la tua media. Lo sfondo sta arrossendo, e non per timidezza.",
            "Il trend scende da giorni. Niente prediche: oggi basta fare meglio di ieri.",
        ),
        Cat.GENERIC_GOOD to listOf(
            "Andamento solido. Non ti serve un motivatore, ti serve solo non smettere.",
            "Le cose vanno bene. Ti tengo d'occhio comunque.",
        ),
        Cat.GENERIC_MID to listOf(
            "Né eroe né divano. Oggi scegli da che parte stare.",
            "Media onesta. Con un po' di cattiveria in più diventa buona.",
            "Tutto nella norma. Che, detto tra noi, non è un complimento.",
        ),
        Cat.GENERIC_BAD to listOf(
            "Il tuo storico chiede pietà. Cominciamo da dieci minuti, oggi.",
            "Sedentarietà cronica in vista. Non serve una maratona: serve uscire di casa.",
            "Lo sfondo è rosso per un motivo. Un passo alla volta, letteralmente.",
        ),
    )

    private fun fmt(v: Long) = String.format(Locale.ITALY, "%,d", v)

    private fun fmtSleep(h: Double): String {
        val m = Math.round(h * 60)
        return "${m / 60} h ${"%02d".format(m % 60)}"
    }

    fun pick(cat: Cat, seed: Int, c: PhraseContext): String {
        val list = pool[cat] ?: return ""
        val template = list[Math.floorMod(seed + cat.ordinal * 7, list.size)]
        return template
            .replace("{steps}", fmt(c.steps))
            .replace("{left}", fmt(c.left))
            .replace("{goal}", fmt(c.goal.toLong()))
            .replace("{h}", "%d".format(c.hour))
            .replace("{typical}", c.typical?.let { fmt(it) } ?: "di più")
            .replace("{sleep}", c.sleep?.let { fmtSleep(it) } ?: "poche ore")
            .replace("{spo2}", c.spo2?.let { "%.0f".format(it) } ?: "?")
            .replace("{n}", c.n.toString())
    }
}
