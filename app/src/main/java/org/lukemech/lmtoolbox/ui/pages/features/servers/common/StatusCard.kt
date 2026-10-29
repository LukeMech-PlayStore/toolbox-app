package org.lukemech.lmtoolbox.ui.pages.features.servers.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How a server is doing, as its [PhaseDot] shows it. */
enum class ServerHealth { Running, Busy, Failed, Stopped }

/** A dot in the color of how a server is doing: green running, amber starting or stopping, red failed, gray stopped. */
@Composable
fun PhaseDot(health: ServerHealth, size: Dp = 14.dp) {
    val color = when (health) {
        ServerHealth.Running -> Color(0xFF2E9E4F)
        ServerHealth.Busy -> Color(0xFFE0A100)
        ServerHealth.Failed -> MaterialTheme.colorScheme.error
        ServerHealth.Stopped -> MaterialTheme.colorScheme.outline
    }
    Icon(Icons.Default.Circle, contentDescription = null, tint = color, modifier = Modifier.size(size))
}

/** A server's status on top of its page: its [health]'s dot and [title] large, [details] (if any) under them. */
@Composable
fun StatusCard(health: ServerHealth, title: String, details: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhaseDot(health, 18.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            details?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
