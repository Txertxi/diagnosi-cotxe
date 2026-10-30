package cat.sergibonet.diagnosi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/** Pàgina 1: el que cal mirar mentre es condueix. */
class DrivePage(ctx: Context) : BasePage(ctx, 1280f, 520f) {

    private val speedDrum = DrumPainter(127f, 180f, 6f, false, true)
    private val tripDrum = DrumPainter(66f, 92f, 4f, true, true, true)
    private val compass = CompassPainter()
    private val altimeter = AltimeterPainter()
    private val clock = ClockPainter()
    private val white = Txt(Pal.WHITE)
    private val whiteLight = Txt(Pal.WHITE, false)
    private val gray = Txt(Pal.GRAY, false)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cal = Calendar.getInstance()

    private var speedDisp = 0.0
    private var headingDisp = 0.0
    private var headingTarget = 0.0
    private var haveHeading = false
    private var altDisp = 0.0
    private var haveAlt = false
    private var distDisp = 0.0
    private var haveDist = false

    override fun paintPage(c: Canvas) {
        val fresh = GpsHub.hasFreshFix()
        val raw = GpsHub.speedKmh

        // Velocitat suavitzada: per sota del llindar es considera aturat (0).
        val target = if (fresh && raw >= Cfg.STOP_KMH) min(raw, 999.0) else 0.0
        speedDisp += (target - speedDisp) * (1.0 - exp(-dt / 0.7))
        if (abs(target - speedDisp) < 0.02) speedDisp = target

        // Rumb: només s'actualitza en moviment; parat es manté l'últim.
        val headingOk = fresh && GpsHub.hasBearing && raw >= Cfg.HEADING_MIN_KMH
        if (headingOk) {
            headingTarget = GpsHub.bearing
            if (!haveHeading) {
                haveHeading = true
                headingDisp = headingTarget
            }
        }
        headingDisp = Geo.norm360(
            headingDisp + Geo.shortestDiff(headingDisp, headingTarget) * (1.0 - exp(-dt / 0.35))
        )

        // Alçada suavitzada: l'agulla no ha de tremolar amb el soroll del GPS.
        val altTarget = GpsHub.correctedAltitude()
        if (!altTarget.isNaN()) {
            if (!haveAlt) {
                altDisp = altTarget
                haveAlt = true
            } else {
                altDisp += (altTarget - altDisp) * (1.0 - exp(-dt / 1.2))
            }
        }

        // Distància suavitzada: la roda gira sense salts entre lectures del GPS.
        val distTarget = GpsHub.trip.distanceM
        if (!haveDist || distTarget < distDisp - 1.0) {
            distDisp = distTarget
            haveDist = true
        } else {
            distDisp += (distTarget - distDisp) * (1.0 - exp(-dt / 0.4))
            if (abs(distTarget - distDisp) < 0.5) distDisp = distTarget
        }

        val now = GpsHub.nowMs()
        val tz = TimeZone.getDefault()

        // ---- Columna esquerra: velocitat, distància del viatge, estat del GPS
        speedDrum.draw(c, 30f, 14f, Drum.positions(speedDisp, 3).reversedArray())
        gray.draw(c, "km/h", 423f, 236f, 34f, Paint.Align.RIGHT)
        gray.draw(c, "Viatge (km)", 30f, 298f, 30f)
        tripDrum.draw(c, 30f, 312f, Drum.positions(distDisp / 100.0, 4).reversedArray())

        dot.color = GpsHub.statusColor()
        c.drawCircle(42f, 470f, 12f, dot)
        val gpsText = if (fresh) {
            "GPS ${GpsHub.satsUsed}/${GpsHub.satsVisible}"
        } else {
            GpsHub.statusLabel()
        }
        gray.draw(c, gpsText, 66f, 481f, 34f, Paint.Align.LEFT, 330f)

        // ---- Tres esferes de la mateixa mida
        val dimAlpha = if (headingOk) 255 else 120
        compass.draw(c, 545f, 128f, 112f, headingDisp.toFloat(), dimAlpha)
        altimeter.draw(c, 825f, 128f, 112f, if (haveAlt && !altTarget.isNaN()) altDisp else Double.NaN)
        clock.draw(c, 1105f, 128f, 112f, now, tz)

        // ---- Text sota cada esfera (rumb, alçada i data: la mateixa mida)
        val headText = if (haveHeading) {
            "${headingDisp.roundToInt() % 360}° ${Geo.cardinal(headingDisp)}"
        } else {
            "---"
        }
        white.draw(c, headText, 545f, 284f, 32f, Paint.Align.CENTER, 240f, dimAlpha)
        val alt = GpsHub.correctedAltitude()
        val altText = if (alt.isNaN()) "-- m" else Fmt.num(alt, 0) + " m"
        white.draw(c, altText, 825f, 284f, 32f, Paint.Align.CENTER, 240f)

        white.draw(c, Fmt.hm(now, tz), 1105f, 372f, 88f, Paint.Align.CENTER, 300f)
        cal.timeZone = tz
        cal.timeInMillis = now
        white.draw(c, Cat.weekday(cal), 1105f, 424f, 32f, Paint.Align.CENTER, 300f)
        whiteLight.draw(c, Cat.dayMonthYear(cal), 1105f, 464f, 32f, Paint.Align.CENTER, 340f)
    }
}

/** Pàgina 2: viatge, dades del GPS i hores del sol. */
class TripPage(ctx: Context) : BasePage(ctx, 1280f, 520f) {

    var onReset: (() -> Unit)? = null

    private val drum = DrumPainter(127f, 180f, 6f, true, true, true)
    private val gray = Txt(Pal.GRAY, false)
    private val resetRect = RectF(940f, 440f, 1260f, 510f)
    private var distDisp = 0.0
    private var haveDist = false

    override fun paintPage(c: Canvas) {
        val t = GpsHub.trip

        if (!haveDist || t.distanceM < distDisp - 1.0) {
            distDisp = t.distanceM
            haveDist = true
        } else {
            distDisp += (t.distanceM - distDisp) * (1.0 - exp(-dt / 0.4))
            if (abs(t.distanceM - distDisp) < 0.5) distDisp = t.distanceM
        }

        gray.draw(c, "Distància del viatge (km)", 40f, 44f, 36f)
        drum.draw(c, 40f, 62f, Drum.positions(distDisp / 100.0, 4).reversedArray())

        row(c, "En marxa", Fmt.hms(t.movingMs), 322f, 40f, 600f, 34f, 44f)
        row(c, "Aturat", Fmt.hms(t.stoppedMs), 378f, 40f, 600f, 34f, 44f)
        row(c, "Velocitat mitjana", Fmt.num(t.avgKmh(), 0) + " km/h", 434f, 40f, 600f, 34f, 44f)
        row(c, "Velocitat màxima", Fmt.num(t.maxKmh, 0) + " km/h", 490f, 40f, 600f, 34f, 44f)

        val tz = TimeZone.getDefault()
        SunCache.refresh(GpsHub.nowMs(), tz, GpsHub.lat, GpsHub.lon)
        val alt = GpsHub.correctedAltitude()
        val raw = GpsHub.altitudeRaw
        val corr = Prefs.altOffset
        val rawText = if (raw.isNaN()) "--" else Fmt.num(raw, 0) + " m (corr. " + Fmt.signed(corr, 0) + ")"
        val x0 = 660f
        val x1 = 1260f
        row(c, "Estat", GpsHub.statusLabel(), 52f, x0, x1, 30f, 34f, GpsHub.statusColor())
        row(c, "Satèl·lits", "${GpsHub.satsUsed} usats de ${GpsHub.satsVisible}", 98f, x0, x1, 30f, 34f)
        row(c, "Precisió", if (GpsHub.accuracy.isNaN()) "--" else "±" + Fmt.num(GpsHub.accuracy, 0) + " m", 144f, x0, x1, 30f, 34f)
        row(c, "Latitud", Fmt.coord(GpsHub.lat, "N", "S"), 190f, x0, x1, 30f, 34f)
        row(c, "Longitud", Fmt.coord(GpsHub.lon, "E", "O"), 236f, x0, x1, 30f, 34f)
        row(c, "Alçada", if (alt.isNaN()) "--" else Fmt.num(alt, 0) + " m", 282f, x0, x1, 30f, 34f)
        row(c, "Alçada GPS", rawText, 328f, x0, x1, 30f, 34f)
        row(c, "Sortida del sol", SunCache.rise, 374f, x0, x1, 30f, 34f)
        row(c, "Posta del sol", SunCache.set, 420f, x0, x1, 30f, 34f)

        drawButton(c, resetRect, "Posa a zero", 36f)
    }

    override fun onTap(x: Float, y: Float) {
        if (resetRect.contains(x, y)) onReset?.invoke()
    }
}
