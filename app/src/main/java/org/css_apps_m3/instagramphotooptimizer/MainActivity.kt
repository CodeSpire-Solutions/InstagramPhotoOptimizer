package org.css_apps_m3.instagramphotooptimizer

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

private enum class ExportMode { POST, STORY }
private enum class FileSizeMode { STRICT_1_6_MB, ADAPTIVE_BEST_QUALITY }
private enum class ResolutionProfile { STANDARD_IG, HIGH_DETAIL }

private data class AdvancedSettings(
    val fileSizeMode: FileSizeMode = FileSizeMode.ADAPTIVE_BEST_QUALITY,
    val resolutionProfile: ResolutionProfile = ResolutionProfile.HIGH_DETAIL,
    val keepDepthAndXmpMetadata: Boolean = true
)

class MainActivity : ComponentActivity() {
    private var sharedImageUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedImageUris = extractSharedImageUris(intent)
        setContent { MainScreen(sharedImageUris) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImageUris = extractSharedImageUris(intent)
    }
}

@Composable
fun MainScreen(incomingUris: List<Uri>) {
    val bg = Color(0xFF090B10)
    val surface = Color(0xFF111522)
    val border = Color(0xFF222A3D)
    val textPrimary = Color(0xFFF3F4F6)
    val textSecondary = Color(0xFFA8B1C5)
    val primary = Color(0xFFE5E7EB)
    val primaryText = Color(0xFF0B1020)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var optimizedFile by remember { mutableStateOf<File?>(null) }
    var optimizedZip by remember { mutableStateOf<File?>(null) }
    var optimizeStatus by remember { mutableStateOf<String?>(null) }
    var isOptimizing by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.POST) }
    var showAdvanced by remember { mutableStateOf(false) }
    var advancedSettings by remember { mutableStateOf(AdvancedSettings()) }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            selectedImageUris = incomingUris.distinct()
            optimizedFile = null
            optimizedZip = null
            optimizeStatus = "Imported ${incomingUris.size} image(s) via Share."
        }
    }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris.distinct()
            optimizedFile = null
            optimizedZip = null
            optimizeStatus = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextBlock("Instagram Photo Optimizer", 24.sp, FontWeight.Bold, textPrimary)
            Spacer(modifier = Modifier.height(24.dp))

            TextBlock("Mode", 14.sp, FontWeight.SemiBold, textPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ModeChip(
                    label = "Post (1080x1350)",
                    isSelected = exportMode == ExportMode.POST,
                    onClick = { exportMode = ExportMode.POST },
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    border = border,
                    textPrimary = textPrimary,
                    primary = primary,
                    primaryText = primaryText
                )
                Spacer(modifier = Modifier.size(8.dp))
                ModeChip(
                    label = "Story (1080x1920)",
                    isSelected = exportMode == ExportMode.STORY,
                    onClick = { exportMode = ExportMode.STORY },
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    border = border,
                    textPrimary = textPrimary,
                    primary = primary,
                    primaryText = primaryText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SecondaryAction(
                label = if (showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings",
                onClick = { showAdvanced = !showAdvanced },
                surface = surface,
                border = border,
                textPrimary = textPrimary
            )

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface)
                        .border(1.dp, border, RoundedCornerShape(14.dp)).padding(12.dp)
                ) {
                    Column {
                        TextBlock("Export Size Policy", 13.sp, FontWeight.SemiBold, textPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ModeChip(
                                label = "Adaptive Best",
                                isSelected = advancedSettings.fileSizeMode == FileSizeMode.ADAPTIVE_BEST_QUALITY,
                                onClick = { advancedSettings = advancedSettings.copy(fileSizeMode = FileSizeMode.ADAPTIVE_BEST_QUALITY) },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            ModeChip(
                                label = "Strict 1.6MB",
                                isSelected = advancedSettings.fileSizeMode == FileSizeMode.STRICT_1_6_MB,
                                onClick = { advancedSettings = advancedSettings.copy(fileSizeMode = FileSizeMode.STRICT_1_6_MB) },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextBlock("Resolution Profile", 13.sp, FontWeight.SemiBold, textPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ModeChip(
                                label = "Standard IG",
                                isSelected = advancedSettings.resolutionProfile == ResolutionProfile.STANDARD_IG,
                                onClick = { advancedSettings = advancedSettings.copy(resolutionProfile = ResolutionProfile.STANDARD_IG) },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            ModeChip(
                                label = "High Detail",
                                isSelected = advancedSettings.resolutionProfile == ResolutionProfile.HIGH_DETAIL,
                                onClick = { advancedSettings = advancedSettings.copy(resolutionProfile = ResolutionProfile.HIGH_DETAIL) },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            PrimaryAction("Select Photos", primary, primaryText) { multiPickerLauncher.launch("image/*") }

            if (selectedImageUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                TextBlock("Selected: ${selectedImageUris.size} image(s)", 13.sp, FontWeight.Medium, textSecondary)
            }

            Spacer(modifier = Modifier.height(20.dp))

            selectedImageUris.firstOrNull()?.let { uri ->
                var previewBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(uri) {
                    previewBitmap = withContext(Dispatchers.IO) { decodePreviewBitmap(context, uri) }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface)
                        .border(1.dp, border, RoundedCornerShape(16.dp)).padding(8.dp)
                ) {
                    previewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } ?: Box(modifier = Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                        TextBlock("Loading preview...", 13.sp, FontWeight.Medium, textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                PrimaryAction(
                    if (isOptimizing) "Optimizing..." else "Optimize ${selectedImageUris.size} Photo(s)",
                    primary,
                    primaryText
                ) {
                    if (isOptimizing) return@PrimaryAction
                    isOptimizing = true
                    scope.launch {
                        if (selectedImageUris.size == 1) {
                            val output = withContext(Dispatchers.Default) {
                                optimizeForInstagram(context, selectedImageUris.first(), exportMode, advancedSettings)
                            }
                            optimizedFile = output
                            optimizedZip = null
                            optimizeStatus = "Single image optimized."
                        } else {
                            val zip = withContext(Dispatchers.Default) {
                                optimizeMultipleToZip(context, selectedImageUris, exportMode, advancedSettings)
                            }
                            optimizedZip = zip
                            optimizedFile = null
                            optimizeStatus = "Batch optimized and packed as ZIP (${selectedImageUris.size} files)."
                        }
                        isOptimizing = false
                    }
                }
            }

            if (isOptimizing) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C2436))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    TextBlock("Optimizing...", 12.sp, FontWeight.SemiBold, textSecondary)
                }
            }

            optimizeStatus?.let {
                Spacer(modifier = Modifier.height(18.dp))
                TextBlock(it, 12.sp, FontWeight.Medium, textSecondary)
            }

            optimizedFile?.let { file ->
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryAction("Share to Instagram", primary, primaryText) { shareToInstagram(context, file) }
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryAction(
                    label = "Save to Gallery",
                    onClick = {
                        val saved = saveOptimizedImage(context, file, exportMode)
                        Toast.makeText(context, if (saved) "Saved to gallery." else "Saving failed.", Toast.LENGTH_SHORT).show()
                    },
                    surface = surface,
                    border = border,
                    textPrimary = textPrimary
                )
            }

            optimizedZip?.let { zip ->
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryAction("Share ZIP", primary, primaryText) { shareZip(context, zip) }
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryAction(
                    label = "Save ZIP to Downloads",
                    onClick = {
                        val saved = saveZipToDownloads(context, zip)
                        Toast.makeText(context, if (saved) "ZIP saved." else "ZIP save failed.", Toast.LENGTH_SHORT).show()
                    },
                    surface = surface,
                    border = border,
                    textPrimary = textPrimary
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color,
    border: Color,
    textPrimary: Color,
    primary: Color,
    primaryText: Color
) {
    val bg = if (isSelected) primary else surface
    val fg = if (isSelected) primaryText else textPrimary
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun TextBlock(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, color: Color) {
    BasicText(text, style = TextStyle(fontSize = size, fontWeight = weight, color = color))
}

@Composable
private fun PrimaryAction(label: String, primary: Color, primaryText: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(primary)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit, surface: Color, border: Color, textPrimary: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(surface)
            .border(1.dp, border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

private fun optimizeMultipleToZip(
    context: Context,
    uris: List<Uri>,
    mode: ExportMode,
    settings: AdvancedSettings
): File {
    val zipFile = File(context.cacheDir, "instagram_optimized_batch.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        uris.forEachIndexed { index, uri ->
            val optimized = optimizeForInstagram(context, uri, mode, settings, "instagram_optimized_${index + 1}.jpg")
            zos.putNextEntry(ZipEntry("instagram_optimized_${index + 1}.jpg"))
            optimized.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
    return zipFile
}

private fun optimizeForInstagram(
    context: Context,
    uri: Uri,
    mode: ExportMode,
    settings: AdvancedSettings,
    outputFileName: String = "instagram_optimized.jpg"
): File {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
    var targetWidth = if (settings.resolutionProfile == ResolutionProfile.HIGH_DETAIL) 1440 else 1080
    var targetHeight = when {
        mode == ExportMode.STORY && settings.resolutionProfile == ResolutionProfile.HIGH_DETAIL -> 2560
        mode == ExportMode.STORY -> 1920
        settings.resolutionProfile == ResolutionProfile.HIGH_DETAIL -> 1800
        else -> 1350
    }

    val decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        val scale = minOf(targetWidth / width.toFloat(), targetHeight / height.toFloat(), 1f)
        targetWidth = (width * scale).toInt().coerceAtLeast(1)
        targetHeight = (height * scale).toInt().coerceAtLeast(1)

        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setTargetColorSpace(srgb)
        decoder.setTargetSampleSize(computeDecodeSampleSize(width, height, targetWidth, targetHeight))
        decoder.isMutableRequired = false
    }

    val resizedBitmap = highQualityResize(decodedBitmap, targetWidth, targetHeight)
    val file = File(context.cacheDir, outputFileName)
    FileOutputStream(file).use { it.write(compressJpegSmart(resizedBitmap, settings)) }

    if (settings.keepDepthAndXmpMetadata) {
        copyMetadataForSocialMedia(context, uri, file)
    }
    return file
}

private fun compressJpegSmart(bitmap: Bitmap, settings: AdvancedSettings): ByteArray {
    val preferredTarget = if (settings.fileSizeMode == FileSizeMode.STRICT_1_6_MB) 1_600 * 1024 else 3_600 * 1024
    var working = bitmap
    var best = encodeJpeg(working, 95)

    repeat(8) {
        val attempt = findBestJpegAtOrBelowTarget(working, preferredTarget)
        if (attempt != null) return attempt

        val nextW = (working.width * 0.96f).toInt().coerceAtLeast(900)
        val nextH = (working.height * 0.96f).toInt().coerceAtLeast(900)
        if (nextW == working.width || nextH == working.height) return best
        working = highQualityResize(working, nextW, nextH)
        best = encodeJpeg(working, 95)
    }
    return best
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.toByteArray()
}

private fun findBestJpegAtOrBelowTarget(bitmap: Bitmap, targetBytes: Int, minQuality: Int = 82): ByteArray? {
    var low = minQuality
    var high = 98
    var best: ByteArray? = null
    while (low <= high) {
        val mid = (low + high) / 2
        val bytes = encodeJpeg(bitmap, mid)
        if (bytes.size <= targetBytes) {
            best = bytes
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

private fun computeDecodeSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
    val wantedW = (targetWidth * 2).coerceAtLeast(1)
    val wantedH = (targetHeight * 2).coerceAtLeast(1)
    val ratioW = sourceWidth.toFloat() / wantedW.toFloat()
    val ratioH = sourceHeight.toFloat() / wantedH.toFloat()
    return minOf(ratioW, ratioH).toInt().coerceAtLeast(1)
}

private fun highQualityResize(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    if (source.width == targetWidth && source.height == targetHeight) return source

    val downscaleRatio = maxOf(source.width, source.height).toFloat() / maxOf(targetWidth, targetHeight).toFloat().coerceAtLeast(1f)
    val blurRadius = when {
        downscaleRatio >= 4.0f -> 2
        downscaleRatio >= 2.4f -> 1
        else -> 0
    }

    var current = if (blurRadius > 0) applyBoxBlur(source, blurRadius) else source
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
        val halfW = (current.width / 2).coerceAtLeast(targetWidth)
        val halfH = (current.height / 2).coerceAtLeast(targetHeight)
        val half = Bitmap.createBitmap(halfW, halfH, Bitmap.Config.ARGB_8888)
        Canvas(half).drawBitmap(current, null, Rect(0, 0, halfW, halfH), paint)
        current = half
    }

    if (current.width != targetWidth || current.height != targetHeight) {
        val exact = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(exact).drawBitmap(current, null, Rect(0, 0, targetWidth, targetHeight), paint)
        current = exact
    }
    return current
}

private fun applyBoxBlur(source: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return source
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return source

    val src = IntArray(width * height)
    val tmp = IntArray(width * height)
    val out = IntArray(width * height)
    source.getPixels(src, 0, width, 0, 0, width, height)
    val kernelSize = radius * 2 + 1

    for (y in 0 until height) {
        var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
        for (kx in -radius..radius) {
            val x = kx.coerceIn(0, width - 1)
            val c = src[y * width + x]
            sumA += (c ushr 24) and 0xFF
            sumR += (c ushr 16) and 0xFF
            sumG += (c ushr 8) and 0xFF
            sumB += c and 0xFF
        }
        for (x in 0 until width) {
            tmp[y * width + x] = ((sumA / kernelSize) shl 24) or ((sumR / kernelSize) shl 16) or ((sumG / kernelSize) shl 8) or (sumB / kernelSize)
            val removeX = (x - radius).coerceIn(0, width - 1)
            val addX = (x + radius + 1).coerceIn(0, width - 1)
            val cRemove = src[y * width + removeX]
            val cAdd = src[y * width + addX]
            sumA += ((cAdd ushr 24) and 0xFF) - ((cRemove ushr 24) and 0xFF)
            sumR += ((cAdd ushr 16) and 0xFF) - ((cRemove ushr 16) and 0xFF)
            sumG += ((cAdd ushr 8) and 0xFF) - ((cRemove ushr 8) and 0xFF)
            sumB += (cAdd and 0xFF) - (cRemove and 0xFF)
        }
    }

    for (x in 0 until width) {
        var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
        for (ky in -radius..radius) {
            val y = ky.coerceIn(0, height - 1)
            val c = tmp[y * width + x]
            sumA += (c ushr 24) and 0xFF
            sumR += (c ushr 16) and 0xFF
            sumG += (c ushr 8) and 0xFF
            sumB += c and 0xFF
        }
        for (y in 0 until height) {
            out[y * width + x] = ((sumA / kernelSize) shl 24) or ((sumR / kernelSize) shl 16) or ((sumG / kernelSize) shl 8) or (sumB / kernelSize)
            val removeY = (y - radius).coerceIn(0, height - 1)
            val addY = (y + radius + 1).coerceIn(0, height - 1)
            val cRemove = tmp[removeY * width + x]
            val cAdd = tmp[addY * width + x]
            sumA += ((cAdd ushr 24) and 0xFF) - ((cRemove ushr 24) and 0xFF)
            sumR += ((cAdd ushr 16) and 0xFF) - ((cRemove ushr 16) and 0xFF)
            sumG += ((cAdd ushr 8) and 0xFF) - ((cRemove ushr 8) and 0xFF)
            sumB += (cAdd and 0xFF) - (cRemove and 0xFF)
        }
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(out, 0, width, 0, 0, width, height)
    }
}

private fun decodePreviewBitmap(context: Context, uri: Uri, maxEdge: Int = 2048): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val sampleSize = ceil(maxOf(info.size.width, info.size.height) / maxEdge.toFloat()).toInt().coerceAtLeast(1)
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setTargetSampleSize(sampleSize)
        decoder.isMutableRequired = false
    }
}

private fun copyMetadataForSocialMedia(context: Context, sourceUri: Uri, outputFile: File) {
    try {
        val srcExif = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) } ?: return
        val outExif = ExifInterface(outputFile.absolutePath)
        val tagsToCopy = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_XMP
        )
        for (tag in tagsToCopy) {
            srcExif.getAttribute(tag)?.let { outExif.setAttribute(tag, it) }
        }
        outExif.saveAttributes()
    } catch (_: Exception) {
    }
}

private fun saveOptimizedImage(context: Context, sourceFile: File, mode: ExportMode): Boolean {
    val resolver = context.contentResolver
    val fileName = if (mode == ExportMode.STORY) "instagram_story_${System.currentTimeMillis()}.jpg" else "instagram_post_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InstagramOptimizer")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { output -> sourceFile.inputStream().use { input -> input.copyTo(output) } } ?: return false
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: IOException) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun saveZipToDownloads(context: Context, zipFile: File): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "instagram_optimized_${System.currentTimeMillis()}.zip")
        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/InstagramOptimizer")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { output -> zipFile.inputStream().use { it.copyTo(output) } } ?: return false
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: IOException) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun shareToInstagram(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        setPackage("com.instagram.android")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Instagram is not installed.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareZip(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ZIP"))
}

private fun extractSharedImageUris(intent: Intent?): List<Uri> {
    if (intent == null) return emptyList()
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            listOfNotNull(uri)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.filterNotNull() ?: emptyList()
        }
        else -> emptyList()
    }
}
