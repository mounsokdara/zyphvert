package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

/** Privacy-first banner: the app's core promise, front and centre. */
@Composable
fun HeroCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
    ) {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 36.dp)
                    .alpha(0.12f),
            )
            Column(Modifier.padding(24.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("100% offline", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Convert any file to any format, on your phone",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Video, audio, or image: one converter each, and it can turn the file into anything else. No ads, no tracking, no account.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(if (isWideLayout()) 0.72f else 0.92f),
                )
            }
        }
    }
}
