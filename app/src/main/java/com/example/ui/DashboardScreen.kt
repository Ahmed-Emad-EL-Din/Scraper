package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag("dashboard_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "WebView Tracker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant Canvas-based custom illustration (dashed web screen + highlighter/target effect)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .testTag("illustration_container"),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = IndigoPrimary
                val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                val accentColor = MaterialTheme.colorScheme.tertiary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // Draw web window mockup outline
                    drawRoundRect(
                        color = secondaryColor,
                        topLeft = Offset(width * 0.1f, height * 0.2f),
                        size = Size(width * 0.8f, height * 0.6f),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Web browser head bar separator
                    drawLine(
                        color = secondaryColor,
                        start = Offset(width * 0.1f, height * 0.35f),
                        end = Offset(width * 0.9f, height * 0.35f),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw 3 header dots representing browser control buttons
                    val dotRadius = 4.dp.toPx()
                    drawCircle(color = secondaryColor, radius = dotRadius, center = Offset(width * 0.18f, height * 0.275f))
                    drawCircle(color = secondaryColor, radius = dotRadius, center = Offset(width * 0.24f, height * 0.275f))
                    drawCircle(color = secondaryColor, radius = dotRadius, center = Offset(width * 0.30f, height * 0.275f))

                    // Draw mock lines (representing elements in the webpage)
                    drawLine(
                        color = secondaryColor,
                        start = Offset(width * 0.2f, height * 0.45f),
                        end = Offset(width * 0.6f, height * 0.45f),
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    drawLine(
                        color = secondaryColor,
                        start = Offset(width * 0.2f, height * 0.52f),
                        end = Offset(width * 0.5f, height * 0.52f),
                        strokeWidth = 4.dp.toPx()
                    )

                    // Draw element tracker selector box (high contrast target box overlay)
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(width * 0.18f, height * 0.58f),
                        size = Size(width * 0.64f, height * 0.16f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
                    )

                    // Small Target circle in center of selector
                    drawCircle(
                        color = primaryColor,
                        radius = 16.dp.toPx(),
                        center = Offset(width * 0.5f, height * 0.66f),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(width * 0.5f, height * 0.66f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "No trackers active",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No trackers active. Open the browser to start tracking.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .testTag("empty_state_text")
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onNavigateToBrowser,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_browser_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Open Web Browser",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
