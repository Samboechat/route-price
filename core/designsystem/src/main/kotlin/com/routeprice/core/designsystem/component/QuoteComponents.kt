package com.routeprice.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.theme.NumericStyle
import com.routeprice.core.designsystem.theme.RpTheme
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.util.BrFormat
import com.routeprice.core.domain.util.PolylineCodec
import java.util.Locale

@Composable
fun sourceColor(source: ValueSource): Color = when (source) {
    ValueSource.EXTERNAL -> RpTheme.extended.sourceExternal
    ValueSource.DRIVER_INFORMED -> RpTheme.extended.sourceDriver
    ValueSource.ESTIMATED -> RpTheme.extended.sourceEstimated
}

@Composable
fun SourceBadge(source: ValueSource, modifier: Modifier = Modifier) =
    SourceBadge(QuoteCopy.sourceLabel(source), sourceColor(source), modifier)

@Composable
fun StatusChip(status: QuoteStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        QuoteStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
        QuoteStatus.SENT -> RpTheme.extended.sourceExternal
        QuoteStatus.ACCEPTED -> RpTheme.extended.positive
        QuoteStatus.DECLINED -> RpTheme.extended.negative
    }
    SourceBadge(QuoteCopy.status(status), color, modifier)
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) = SourceBadge("Exemplo", RpTheme.extended.warning, modifier)

/**
 * Desenho do trajeto a partir da polyline da rota (sem depender do SDK de mapas).
 * Usado no histórico, no orçamento e como alternativa quando o mapa não está configurado.
 */
@Composable
fun RouteSketch(
    encodedPolyline: String?,
    modifier: Modifier = Modifier,
    stopsCount: Int = 0,
    lineColor: Color = RpTheme.extended.mapRoute,
) {
    val points = remember(encodedPolyline) {
        encodedPolyline?.let { runCatching { PolylineCodec.decode(it) }.getOrNull() }.orEmpty()
    }
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .semantics { contentDescription = "Desenho do trajeto com $stopsCount paradas" },
    ) {
        val step = 24.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        val pad = 20.dp.toPx()
        val projected = SketchProjection.project(points, size.width, size.height, pad)
        if (projected.size >= 2) {
            val path = Path().apply {
                moveTo(projected.first().x, projected.first().y)
                projected.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, lineColor.copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, lineColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(ink, 7.dp.toPx(), projected.first())
            drawCircle(lineColor, 4.dp.toPx(), projected.first())
            drawCircle(lineColor, 7.dp.toPx(), projected.last())
            drawCircle(ink, 3.dp.toPx(), projected.last())
        } else {
            val a = Offset(pad * 2, size.height - pad * 2)
            val b = Offset(size.width - pad * 2, pad * 2)
            drawLine(lineColor.copy(alpha = 0.6f), a, b, 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
            drawCircle(lineColor, 6.dp.toPx(), a)
            drawCircle(lineColor, 6.dp.toPx(), b)
        }
    }
}

/** Projeção simples (equiretangular corrigida pela latitude) que cabe o trajeto na área. */
object SketchProjection {
    fun project(points: List<GeoPoint>, width: Float, height: Float, padding: Float): List<Offset> {
        if (points.size < 2) return emptyList()
        val meanLat = points.map { it.latitude }.average()
        val k = kotlin.math.cos(Math.toRadians(meanLat))
        val xs = points.map { it.longitude * k }
        val ys = points.map { it.latitude }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val spanX = (maxX - minX).takeIf { it > 1e-9 } ?: 1e-9
        val spanY = (maxY - minY).takeIf { it > 1e-9 } ?: 1e-9
        val availW = width - 2 * padding
        val availH = height - 2 * padding
        val scale = minOf(availW / spanX, availH / spanY)
        val offX = padding + (availW - spanX * scale) / 2
        val offY = padding + (availH - spanY * scale) / 2
        return points.indices.map { i ->
            Offset((offX + (xs[i] - minX) * scale).toFloat(), (offY + (maxY - ys[i]) * scale).toFloat())
        }
    }
}

/** Linha do tempo: embarque → paradas → destino. */
@Composable
fun RouteTimeline(origin: PlaceRef, stops: List<PlaceRef>, destination: PlaceRef, modifier: Modifier = Modifier) {
    val all = listOf(origin) + stops + destination
    Column(modifier) {
        all.forEachIndexed { index, place ->
            val label = when (index) {
                0 -> "A"
                all.lastIndex -> "B"
                else -> index.toString()
            }
            val color = when (index) {
                0 -> MaterialTheme.colorScheme.onSurface
                all.lastIndex -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            val kind = when (index) {
                0 -> "Embarque"
                all.lastIndex -> "Destino"
                else -> "Parada $index"
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = Spacing.xs)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StopMarker(label, color)
                    if (index < all.lastIndex) {
                        Box(Modifier.width(2.dp).height(18.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(kind.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place.displayText, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (place.address.isNotBlank() && place.address != place.label) {
                        Text(
                            place.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Item compacto de orçamento para listas (início e histórico). */
@Composable
fun QuoteListItem(quote: TripQuote, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    RpCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteSketch(quote.route.encodedPolyline, Modifier.width(64.dp).height(64.dp), quote.stops.size)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${quote.origin.displayText} → ${quote.destination.displayText}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val stops = if (quote.stops.isEmpty()) "" else " · ${quote.stops.size} parada(s)"
                Text(
                    "${BrFormat.distance(quote.route.distanceMeters)} · ${BrFormat.duration(quote.route.durationSeconds)}$stops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(quote.status)
                    if (quote.isDemo) DemoBadge()
                    Text(BrFormat.date(quote.createdAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(BrFormat.money(quote.finalPrice), style = NumericStyle.merge(MaterialTheme.typography.titleMedium))
                trailing?.invoke()
            }
        }
    }
}
