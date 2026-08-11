package com.flow.shift.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.shift.theme.AppFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun AppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    fallbackFontSize: TextUnit = 16.sp
) {
    val iconBitmap = rememberAppIconBitmap(packageName)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (iconBitmap == null) colorForPackage(packageName).copy(alpha = 0.85f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = appName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
            )
        } else {
            Text(
                text = appName.take(1).uppercase(),
                color = Color.White,
                fontFamily = AppFontFamily,
                fontSize = fallbackFontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun rememberAppIconBitmap(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val cached = remember(packageName) { iconCache[packageName] }
    if (cached != null) {
        return cached
    }

    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        val bitmap = withContext(Dispatchers.IO) {
            iconCache[packageName] ?: runCatching {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 150
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 150
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                bmp.asImageBitmap().also { loaded ->
                    iconCache[packageName] = loaded
                }
            }.getOrNull()
        }
        value = bitmap
    }
    return iconBitmap
}

private fun colorForPackage(packageName: String): Color {
    val palette = listOf(
        Color(0xFFE1306C),
        Color(0xFFFF0000),
        Color(0xFFFF4500),
        Color(0xFF1877F2),
        Color(0xFF0EA5E9),
        Color(0xFF22C55E),
        Color(0xFFA855F7),
        Color(0xFFF59E0B),
        Color(0xFF3B82F6),
        Color(0xFFEC4899)
    )
    val index = abs(packageName.hashCode()) % palette.size
    return palette[index]
}
