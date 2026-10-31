package com.routeprice.feature.quote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.routeprice.core.designsystem.Brand
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.SketchProjection
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.util.BrFormat
import com.routeprice.core.domain.util.PolylineCodec
import java.io.OutputStream

/**
 * Desenha o orçamento do passageiro em um Canvas: a mesma arte gera o PDF e a imagem PNG.
 * O layout é feito numa largura lógica de [WIDTH] unidades e depois escalado.
 */
class QuoteDocumentRenderer(context: Context) {

    private val regular: Typeface = font(context, com.routeprice.core.designsystem.R.font.manrope, 500)
    private val bold: Typeface = font(context, com.routeprice.core.designsystem.R.font.manrope, 800)
    private val display: Typeface = font(context, com.routeprice.core.designsystem.R.font.space_grotesk, 700)

    fun writePdf(quote: TripQuote, out: OutputStream) {
        val picture = record(quote)
        val scale = PDF_WIDTH / WIDTH
        val doc = PdfDocument()
        val pageHeight = (picture.height * scale).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH.toInt(), pageHeight, 1).create())
        page.canvas.scale(scale, scale)
        page.canvas.drawPicture(picture)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    fun renderBitmap(quote: TripQuote, widthPx: Int = IMAGE_WIDTH_PX): Bitmap {
        val picture = record(quote)
        val scale = widthPx / WIDTH
        val bitmap = createBitmap(widthPx, (picture.height * scale).toInt())
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.drawPicture(picture)
        return bitmap
    }

    private fun record(quote: TripQuote): Picture {
        val draft = Picture()
        val height = draw(draft.beginRecording(WIDTH.toInt(), MAX_HEIGHT), quote)
        draft.endRecording()
        val picture = Picture()
        draw(picture.beginRecording(WIDTH.toInt(), height.toInt()), quote)
        picture.endRecording()
        return picture
    }

    /** Desenha e devolve a altura usada. */
    @Suppress("LongMethod")
    private fun draw(c: Canvas, q: TripQuote): Float {
        c.drawColor(PAPER)
        var y = 0f

        // Cabeçalho
        val headerH = 112f
        c.drawRect(0f, 0f, WIDTH, headerH, fill(INK))
        c.drawRect(0f, headerH - 4f, WIDTH, headerH, fill(Brand.ACCENT_ARGB))
        text(c, Brand.WORDMARK_PRIMARY, PAD, 34f, 13f, bold, Color.WHITE)
        text(c, Brand.WORDMARK_SECONDARY, PAD + measure(Brand.WORDMARK_PRIMARY + " ", 13f, bold), 34f, 13f, bold, Brand.ACCENT_ARGB)
        text(c, "Orçamento de viagem", PAD, 66f, 22f, display, Color.WHITE)
        text(c, "Emitido em ${BrFormat.dateTime(q.updatedAtMillis)}", PAD, 90f, 10f, regular, MUTED_ON_INK)
        y = headerH + 16f

        if (q.isDemo) {
            c.drawRoundRect(RectF(PAD, y, WIDTH - PAD, y + 28f), 6f, 6f, fill(AMBER_BG))
            text(c, "EXEMPLO — dados fictícios de demonstração", PAD + 10f, y + 18f, 10f, bold, AMBER)
            y += 40f
        }

        // Motorista e passageiro
        val d = q.driver
        if (d.name.isNotBlank() || q.passengerName.isNotBlank()) {
            val colW = (WIDTH - 2 * PAD) / 2
            if (d.name.isNotBlank()) {
                label(c, "MOTORISTA", PAD, y)
                text(c, d.name, PAD, y + 16f, 12f, bold, INK)
                val details = listOfNotNull(
                    d.phone.ifBlank { null },
                    q.vehicle?.let { v -> listOf(v.name, v.plate).filter { it.isNotBlank() }.joinToString(" · ") },
                    d.vehicleDescriptionForPassenger.ifBlank { null },
                ).joinToString("\n")
                if (details.isNotBlank()) paragraph(c, details, PAD, y + 22f, colW - 8f, 9.5f, regular, MUTED)
            }
            if (q.passengerName.isNotBlank()) {
                label(c, "PASSAGEIRO", PAD + colW, y)
                text(c, q.passengerName, PAD + colW, y + 16f, 12f, bold, INK)
            }
            y += 64f
        }

        // Trajeto
        label(c, "TRAJETO", PAD, y)
        y += 10f
        val places = listOf(q.origin) + q.stops + q.destination
        places.forEachIndexed { i, p ->
            val marker = when (i) {
                0 -> "A"
                places.lastIndex -> "B"
                else -> i.toString()
            }
            val color = if (i == places.lastIndex) ACCENT_DARK else INK
            c.drawCircle(PAD + 9f, y + 11f, 9f, fill(color))
            centered(c, marker, PAD + 9f, y + 14.5f, 9f, bold, Color.WHITE)
            if (i < places.lastIndex) c.drawRect(PAD + 8f, y + 21f, PAD + 10f, y + 30f, fill(LINE))
            val h = paragraph(c, p.displayText, PAD + 28f, y + 3f, WIDTH - 2 * PAD - 28f, 11f, bold, INK)
            y += maxOf(30f, h + 10f)
        }
        y += 4f

        // Desenho da rota
        val sketch = RectF(PAD, y, WIDTH - PAD, y + 120f)
        drawSketch(c, sketch, q.route.encodedPolyline)
        y = sketch.bottom + 14f

        // Indicadores
        val tileW = (WIDTH - 2 * PAD - 16f) / 3
        listOf(
            "DISTÂNCIA" to BrFormat.distance(q.route.distanceMeters),
            "TEMPO ESTIMADO" to BrFormat.duration(q.route.durationSeconds),
            "PEDÁGIOS" to if (q.input.tolls.isPositive) BrFormat.money(q.input.tolls) else "—",
        ).forEachIndexed { i, (k, v) ->
            val x = PAD + i * (tileW + 8f)
            c.drawRoundRect(RectF(x, y, x + tileW, y + 50f), 8f, 8f, fill(TILE))
            label(c, k, x + 10f, y + 17f)
            text(c, v, x + 10f, y + 39f, 14f, display, INK)
        }
        y += 58f
        y += paragraph(c, QuoteCopy.routeSourceLabel(q.route.source) + ".", PAD, y, WIDTH - 2 * PAD, 8.5f, regular, MUTED) + 12f

        // Composição
        label(c, "COMPOSIÇÃO DO VALOR", PAD, y)
        y += 8f
        PassengerView.lines(q).forEach { line ->
            val top = y
            val labelH = paragraph(c, line.label, PAD, y + 4f, 200f, 10.5f, bold, INK)
            val amount = BrFormat.money(line.amount)
            text(c, amount, WIDTH - PAD - measure(amount, 11f, display), y + 15f, 11f, display, INK)
            var lineY = y + 4f + labelH
            val sub = listOfNotNull(line.detail, QuoteCopy.sourceLabel(line.source)).joinToString(" · ")
            lineY += paragraph(c, sub, PAD, lineY + 1f, 240f, 8.5f, regular, sourceColor(line.source))
            y = maxOf(lineY, top + 24f) + 6f
            c.drawRect(PAD, y - 3f, WIDTH - PAD, y - 2.5f, fill(LINE))
        }

        // Total
        y += 6f
        c.drawRoundRect(RectF(PAD, y, WIDTH - PAD, y + 62f), 10f, 10f, fill(INK))
        text(c, "VALOR TOTAL", PAD + 14f, y + 22f, 9f, bold, MUTED_ON_INK)
        text(c, "Válido até ${BrFormat.dateTime(q.validUntilMillis)}", PAD + 14f, y + 44f, 9f, regular, MUTED_ON_INK)
        val total = BrFormat.money(PassengerView.total(q))
        text(c, total, WIDTH - PAD - 14f - measure(total, 24f, display), y + 41f, 24f, display, Brand.ACCENT_ARGB)
        y += 78f

        // Entenda o valor
        text(c, "Entenda o valor da sua viagem", PAD, y + 10f, 14f, display, INK)
        y += 22f
        PassengerView.kindsPresent(q).forEach { kind ->
            text(c, QuoteCopy.lineLabel(kind), PAD, y + 10f, 10f, bold, INK)
            y += 14f
            y += paragraph(c, QuoteCopy.lineExplanation(kind), PAD, y, WIDTH - 2 * PAD, 9f, regular, MUTED) + 8f
        }
        y += 4f
        listOf(ValueSource.EXTERNAL, ValueSource.DRIVER_INFORMED, ValueSource.ESTIMATED).forEach { s ->
            c.drawCircle(PAD + 4f, y + 6f, 4f, fill(sourceColor(s)))
            y += paragraph(c, "${QuoteCopy.sourceLabel(s)}: ${QuoteCopy.sourceExplanation(s)}", PAD + 14f, y, WIDTH - 2 * PAD - 14f, 8.5f, regular, MUTED) + 4f
        }
        y += 8f
        y += paragraph(
            c,
            "Tempo e distância são estimativas e podem variar com o trânsito. Pedágios são repassados sem acréscimo. " +
                "Nenhum valor de concorrente ou tarifa oficial é usado neste cálculo.",
            PAD, y, WIDTH - 2 * PAD, 8f, regular, MUTED,
        )
        y += 14f
        text(c, Brand.SHARE_SIGNATURE, PAD, y + 8f, 8f, bold, MUTED)
        return y + 28f
    }

    private fun drawSketch(c: Canvas, r: RectF, encoded: String?) {
        c.drawRoundRect(r, 10f, 10f, fill(TILE))
        val points = encoded?.let { runCatching { PolylineCodec.decode(it) }.getOrNull() }.orEmpty()
        val projected = SketchProjection.project(points, r.width(), r.height(), 16f)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (projected.size >= 2) {
            val path = Path()
            projected.forEachIndexed { i, o -> if (i == 0) path.moveTo(r.left + o.x, r.top + o.y) else path.lineTo(r.left + o.x, r.top + o.y) }
            c.drawPath(path, stroke.apply { color = Brand.ACCENT_ARGB; strokeWidth = 7f; alpha = 110 })
            c.drawPath(path, stroke.apply { color = ACCENT_DARK; strokeWidth = 3f; alpha = 255 })
            val a = projected.first()
            val b = projected.last()
            c.drawCircle(r.left + a.x, r.top + a.y, 5f, fill(INK))
            c.drawCircle(r.left + b.x, r.top + b.y, 5f, fill(ACCENT_DARK))
        } else {
            centered(c, "Rota informada pelo motorista", r.centerX(), r.centerY() + 3f, 9f, regular, MUTED)
        }
    }

    private fun sourceColor(s: ValueSource): Int = when (s) {
        ValueSource.EXTERNAL -> SKY
        ValueSource.DRIVER_INFORMED -> ACCENT_DARK
        ValueSource.ESTIMATED -> AMBER
    }

    private fun paint(size: Float, tf: Typeface, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = tf
        this.color = color
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface, color: Int) =
        c.drawText(s, x, y, paint(size, tf, color))

    private fun centered(c: Canvas, s: String, cx: Float, y: Float, size: Float, tf: Typeface, color: Int) =
        c.drawText(s, cx - measure(s, size, tf) / 2, y, paint(size, tf, color))

    private fun label(c: Canvas, s: String, x: Float, y: Float) = text(c, s, x, y, 7.5f, bold, MUTED)

    private fun measure(s: String, size: Float, tf: Typeface) = paint(size, tf, INK).measureText(s)

    /** Texto com quebra de linha; devolve a altura ocupada. */
    private fun paragraph(c: Canvas, s: String, x: Float, y: Float, width: Float, size: Float, tf: Typeface, color: Int): Float {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint(size, tf, color), width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()
        c.withTranslation(x, y) { layout.draw(this) }
        return layout.height.toFloat()
    }

    private companion object {
        const val WIDTH = 400f
        const val PAD = 22f
        const val PDF_WIDTH = 595f
        const val IMAGE_WIDTH_PX = 1080
        const val MAX_HEIGHT = 6000
        val PAPER = Color.rgb(250, 250, 248)
        val INK = Brand.INK_ARGB
        val MUTED = Color.rgb(96, 104, 114)
        val MUTED_ON_INK = Color.rgb(170, 178, 188)
        val LINE = Color.rgb(228, 231, 226)
        val TILE = Color.rgb(240, 242, 238)
        val ACCENT_DARK = Color.rgb(63, 106, 0)
        val SKY = Color.rgb(11, 103, 168)
        val AMBER = Color.rgb(138, 90, 0)
        val AMBER_BG = Color.rgb(253, 240, 210)

        fun font(context: Context, res: Int, weight: Int): Typeface {
            val base = runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.SANS_SERIF
            return Typeface.create(base, weight, false)
        }
    }
}
