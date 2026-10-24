package com.smartdialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartdialer.data.Contact
import com.smartdialer.ui.theme.BubbleColors

/**
 * A circle with the contact's photo, or their initials when there is no photo (or it fails to load).
 * [elevation] gives it a soft shadow.
 */
@Composable
fun ContactAvatar(
    contact: Contact,
    diameter: Dp,
    initialsSp: Int,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .shadow(elevation, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.container),
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
