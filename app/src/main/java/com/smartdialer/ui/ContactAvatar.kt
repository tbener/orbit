package com.smartdialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartdialer.data.Contact
import com.smartdialer.ui.theme.BubbleColors
import com.smartdialer.ui.theme.Orbit

/**
 * An orb: the contact's photo, or their initials on a soft gradient, with a thin luminous rim, a faint
 * sheen at the top-left and (for the more important ones) a glow in the orb's own color. [glow] runs from
 * 0 (none) to 1 (the top bubble).
 */
@Composable
fun ContactAvatar(
    contact: Contact,
    diameter: Dp,
    initialsSp: Int,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    glow: Float = 0f,
) {
    val dark = Orbit.colors.isDark
    Box(
        modifier = modifier
            .size(diameter)
            .drawBehind {
                if (glow > 0f) {
                    val radius = size.minDimension / 2f * (1.35f + 0.6f * glow)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.55f to colors.bottom.copy(alpha = (if (dark) 0.55f else 0.40f) * glow),
                            1f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                }
            }
            .shadow(
                elevation = 3.dp + 10.dp * glow,
                shape = CircleShape,
                clip = false,
                ambientColor = colors.bottom.copy(alpha = 0.6f),
                spotColor = colors.bottom.copy(alpha = 0.8f),
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.top, colors.bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(contact.name),
            color = colors.content,
            fontSize = initialsSp.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Sheen: a soft highlight at the top-left, as if lit from above.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = if (dark) 0.34f else 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.22f),
                            radius = size.width * 0.62f,
                        ),
                    )
                },
        )
        // Rim: a thin luminous edge, brightest at the top-left.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = Orbit.sizing.hairline,
                    brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.4f))),
                    shape = CircleShape,
                ),
        )
    }
}

fun initialsOf(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words.first().first()}${words.last().first()}"
    }.uppercase()
}
