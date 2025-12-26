package it.srik.TypeQ25.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import it.srik.TypeQ25.R

// BlackBerry colors
private val BlackBerryBlack = Color(0xFF1A1A1A)
private val BlackBerryDarkGray = Color(0xFF2D2D2D)
private val BlackBerryGray = Color(0xFF3F3F3F)
private val BlackBerrySilver = Color(0xFFBDBDBD)
private val BlackBerryBlue = Color(0xFF00A0DC)

/**
 * Custom top bar with BlackBerry theme.
 * Features gradient background with subtle grid pattern.
 */
@Composable
fun CustomTopBar(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BlackBerryBlack,
                            BlackBerryDarkGray,
                            BlackBerryBlack
                        )
                    )
                )
        ) {
            // Subtle grid pattern overlay
            GridPattern(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Centered title and subtitle
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TypeQ25",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BlackBerrySilver
                    )
                    Text(
                        text = "Physical Keyboard IME",
                        style = MaterialTheme.typography.bodySmall,
                        color = BlackBerryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Settings icon on the right
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            color = BlackBerryBlue
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_content_description),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridPattern(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val lineColor = BlackBerryGray.copy(alpha = 0.3f)
        val spacing = 60f
        
        // Draw vertical lines
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 1f
            )
            x += spacing
        }
        
        // Draw horizontal lines
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacing
        }
    }
}

