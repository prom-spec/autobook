package com.autobook.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.autobook.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen dialog for cropping an image to book-cover aspect ratio (2:3).
 */
@Composable
fun CoverCropDialog(
    imageUri: Uri,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Decode the image
    val sourceBitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                // Decode with sample size to avoid OOM on huge images
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                val w = opts.outWidth
                val h = opts.outHeight
                var sample = 1
                while (w / sample > 2048 || h / sample > 2048) sample *= 2
                opts.inJustDecodeBounds = false
                opts.inSampleSize = sample
                // Re-open stream (can't rewind)
                context.contentResolver.openInputStream(imageUri)?.use { s2 ->
                    BitmapFactory.decodeStream(s2, null, opts)
                }
            }
        } catch (e: Exception) { null }
    }

    if (sourceBitmap == null) {
        onDismiss()
        return
    }

    val bitmapImage = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    // Canvas size in px
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Crop window: 2:3 aspect ratio (w:h)
    val cropAspect = 2f / 3f

    // Image transform state (offset = top-left of image in canvas coords, scale relative to fit)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    // Compute crop rect centered in canvas
    val cropRect = remember(canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@remember Rect.Zero
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        // Crop window fills ~80% of the smaller dimension
        val maxCropW = cw * 0.8f
        val maxCropH = ch * 0.8f
        val cropW: Float
        val cropH: Float
        if (maxCropW / maxCropH < cropAspect) {
            cropW = maxCropW
            cropH = cropW / cropAspect
        } else {
            cropH = maxCropH
            cropW = cropH * cropAspect
        }
        val left = (cw - cropW) / 2f
        val top = (ch - cropH) / 2f
        Rect(left, top, left + cropW, top + cropH)
    }

    // Initialize: fit image so it covers the crop rect
    LaunchedEffect(canvasSize, sourceBitmap) {
        if (canvasSize.width == 0 || canvasSize.height == 0 || initialized) return@LaunchedEffect
        val bw = sourceBitmap.width.toFloat()
        val bh = sourceBitmap.height.toFloat()
        val cRect = cropRect
        if (cRect == Rect.Zero) return@LaunchedEffect
        // Scale image to cover crop rect
        val scaleToFill = max(cRect.width / bw, cRect.height / bh)
        scale = scaleToFill
        // Center image on crop rect
        offsetX = cRect.left - (bw * scaleToFill - cRect.width) / 2f
        offsetY = cRect.top - (bh * scaleToFill - cRect.height) / 2f
        initialized = true
    }

    // Clamp so image always covers the crop rect
    fun clamp() {
        val bw = sourceBitmap.width * scale
        val bh = sourceBitmap.height * scale
        // Image right edge must be >= crop right, left edge <= crop left
        offsetX = min(cropRect.left, max(cropRect.right - bw, offsetX))
        offsetY = min(cropRect.top, max(cropRect.bottom - bh, offsetY))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        containerColor = Navy,
        title = {
            Text("Crop Cover", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f) // roughly portrait
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val oldScale = scale
                            val minScale = max(
                                cropRect.width / sourceBitmap.width,
                                cropRect.height / sourceBitmap.height
                            )
                            scale = (scale * zoom).coerceIn(minScale, minScale * 5f)

                            // Adjust offset to zoom toward center of crop
                            val cx = cropRect.center.x
                            val cy = cropRect.center.y
                            offsetX = cx - (cx - offsetX) * (scale / oldScale) + pan.x
                            offsetY = cy - (cy - offsetY) * (scale / oldScale) + pan.y
                            clamp()
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw the image
                    drawImage(
                        image = bitmapImage,
                        dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                        dstSize = IntSize(
                            (sourceBitmap.width * scale).roundToInt(),
                            (sourceBitmap.height * scale).roundToInt()
                        )
                    )

                    // Draw semi-transparent overlay outside crop rect
                    val overlay = Color.Black.copy(alpha = 0.6f)
                    // Top
                    drawRect(overlay, Offset.Zero, Size(size.width, cropRect.top))
                    // Bottom
                    drawRect(overlay, Offset(0f, cropRect.bottom), Size(size.width, size.height - cropRect.bottom))
                    // Left
                    drawRect(overlay, Offset(0f, cropRect.top), Size(cropRect.left, cropRect.height))
                    // Right
                    drawRect(overlay, Offset(cropRect.right, cropRect.top), Size(size.width - cropRect.right, cropRect.height))

                    // Draw crop border
                    drawRect(
                        color = Amber,
                        topLeft = Offset(cropRect.left, cropRect.top),
                        size = Size(cropRect.width, cropRect.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )

                    // Draw rule-of-thirds grid lines
                    val thirdW = cropRect.width / 3f
                    val thirdH = cropRect.height / 3f
                    val gridColor = Color.White.copy(alpha = 0.3f)
                    for (i in 1..2) {
                        drawLine(gridColor, Offset(cropRect.left + thirdW * i, cropRect.top), Offset(cropRect.left + thirdW * i, cropRect.bottom), strokeWidth = 1f)
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + thirdH * i), Offset(cropRect.right, cropRect.top + thirdH * i), strokeWidth = 1f)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Extract the crop region from the source bitmap
                    val bw = sourceBitmap.width * scale
                    val bh = sourceBitmap.height * scale

                    // Crop rect in image-pixel coordinates
                    val srcLeft = ((cropRect.left - offsetX) / scale).roundToInt().coerceIn(0, sourceBitmap.width)
                    val srcTop = ((cropRect.top - offsetY) / scale).roundToInt().coerceIn(0, sourceBitmap.height)
                    val srcW = (cropRect.width / scale).roundToInt().coerceIn(1, sourceBitmap.width - srcLeft)
                    val srcH = (cropRect.height / scale).roundToInt().coerceIn(1, sourceBitmap.height - srcTop)

                    val cropped = Bitmap.createBitmap(sourceBitmap, srcLeft, srcTop, srcW, srcH)

                    // Scale to reasonable cover size (400x600)
                    val final = Bitmap.createScaledBitmap(cropped, 400, 600, true)
                    onConfirm(final)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Amber)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("Cancel")
            }
        }
    )
}
