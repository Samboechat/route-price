package com.routeprice.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.routeprice.core.designsystem.Brand
import com.routeprice.core.designsystem.theme.Spacing

/** Símbolo da marca: rota sinuosa ligando dois pontos. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.12f
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.8f)
            cubicTo(w * 0.2f, h * 0.35f, w * 0.8f, h * 0.65f, w * 0.8f, h * 0.2f)
        }
        drawPath(path, ink.copy(alpha = 0.9f), style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(accent, radius = w * 0.13f, center = Offset(w * 0.2f, h * 0.8f))
        drawCircle(accent, radius = w * 0.13f, center = Offset(w * 0.8f, h * 0.2f))
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = Brand.NAME },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BrandMark()
        Text(
            buildAnnotatedString {
                append(Brand.WORDMARK_PRIMARY)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(" " + Brand.WORDMARK_SECONDARY) }
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
