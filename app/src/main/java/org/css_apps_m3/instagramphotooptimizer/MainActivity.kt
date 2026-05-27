package org.css_apps_m3.instagramphotooptimizer

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import org.css_apps_m3.instagramphotooptimizer.ui.theme.ExpressivePalette
import org.css_apps_m3.instagramphotooptimizer.ui.theme.ExpressiveThemeOptions
import org.css_apps_m3.instagramphotooptimizer.ui.theme.ExpressiveTypographyStyle
import org.css_apps_m3.instagramphotooptimizer.ui.theme.InstagramPhotoOptimizerTheme
import org.css_apps_m3.instagramphotooptimizer.ui.theme.rememberDefaultExpressiveThemeOptions
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
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ExportMode { POST, STORY }
private enum class AppScreen { EDITOR, SETTINGS }
private enum class FileSizeMode { STRICT_1_6_MB, ADAPTIVE_BEST_QUALITY }
private enum class ResolutionProfile { STANDARD_IG, HIGH_DETAIL }
private enum class DepthHandlingMode { OPTIMIZE_MAY_LOSE_DEPTH, PRESERVE_SOCIAL_DEPTH }

private data class AdvancedSettings(
    val fileSizeMode: FileSizeMode = FileSizeMode.ADAPTIVE_BEST_QUALITY,
    val resolutionProfile: ResolutionProfile = ResolutionProfile.HIGH_DETAIL,
    val keepDepthAndXmpMetadata: Boolean = true,
    val depthHandlingMode: DepthHandlingMode = DepthHandlingMode.OPTIMIZE_MAY_LOSE_DEPTH,
    val antiAliasingLevel: Int = 2,
    val preserveHdrIfAvailable: Boolean = true
)

private data class ZoomDialogImage(
    val bitmap: Bitmap,
    val title: String
)

class MainActivity : ComponentActivity() {
    private var sharedImageUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemThemeOptions = rememberDefaultExpressiveThemeOptions()

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var optimizedFile by remember { mutableStateOf<File?>(null) }
    var optimizedZip by remember { mutableStateOf<File?>(null) }
    var optimizeStatus by remember { mutableStateOf<String?>(null) }
    var isOptimizing by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.POST) }
    var showAdvanced by remember { mutableStateOf(false) }
    var advancedSettings by remember { mutableStateOf(loadAdvancedSettings(context)) }
    var selectedImageHasHdr by remember { mutableStateOf<Boolean?>(null) }
    var themeOptions by remember { mutableStateOf(loadThemeOptions(context, systemThemeOptions)) }
    var currentScreen by remember { mutableStateOf(AppScreen.EDITOR) }
    var zoomDialogImage by remember { mutableStateOf<ZoomDialogImage?>(null) }
    var showResultDetails by remember { mutableStateOf(false) }

    LaunchedEffect(advancedSettings) {
        saveAdvancedSettings(context, advancedSettings)
    }
    LaunchedEffect(themeOptions) {
        saveThemeOptions(context, themeOptions)
    }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            selectedImageUris = incomingUris.distinct()
            optimizedFile = null
            optimizedZip = null
            optimizeStatus = "Imported ${incomingUris.size} image(s) via Share."
        }
    }

    LaunchedEffect(selectedImageUris) {
        val first = selectedImageUris.firstOrNull()
        selectedImageHasHdr = if (first == null) {
            null
        } else {
            withContext(Dispatchers.IO) { hasHdrLikeSource(context, first) }
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

    InstagramPhotoOptimizerTheme(options = themeOptions) {
        val colors = MaterialTheme.colorScheme
        val bg = colors.background
        val surface = colors.surfaceContainerHigh
        val border = colors.outline.copy(alpha = 0.5f)
        val textPrimary = colors.onSurface
        val textSecondary = colors.onSurfaceVariant
        val primary = colors.primary
        val primaryText = colors.onPrimary

        if (currentScreen == AppScreen.SETTINGS) {
            ThemeSettingsScreen(
                themeOptions = themeOptions,
                onThemeOptionsChange = { themeOptions = it },
                onBack = { currentScreen = AppScreen.EDITOR },
                surface = surface,
                border = border,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                primary = primary,
                primaryText = primaryText
            )
            return@InstagramPhotoOptimizerTheme
        }

        Box(modifier = Modifier.fillMaxSize().background(bg)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextBlock("Instagram Photo Optimizer", 24.sp, FontWeight.Bold, textPrimary)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { currentScreen = AppScreen.SETTINGS }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Theme Settings",
                        tint = textPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

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

                        Spacer(modifier = Modifier.height(12.dp))
                        TextBlock("Depth Data for Social Media", 13.sp, FontWeight.SemiBold, textPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ModeChip(
                                label = "Optimize (may lose)",
                                isSelected = advancedSettings.depthHandlingMode == DepthHandlingMode.OPTIMIZE_MAY_LOSE_DEPTH,
                                onClick = {
                                    advancedSettings = advancedSettings.copy(
                                        depthHandlingMode = DepthHandlingMode.OPTIMIZE_MAY_LOSE_DEPTH
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            ModeChip(
                                label = "Preserve (no re-encode)",
                                isSelected = advancedSettings.depthHandlingMode == DepthHandlingMode.PRESERVE_SOCIAL_DEPTH,
                                onClick = {
                                    advancedSettings = advancedSettings.copy(
                                        depthHandlingMode = DepthHandlingMode.PRESERVE_SOCIAL_DEPTH
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                surface = surface,
                                border = border,
                                textPrimary = textPrimary,
                                primary = primary,
                                primaryText = primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextBlock("Anti-Aliasing Strength", 13.sp, FontWeight.SemiBold, textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = advancedSettings.antiAliasingLevel.toFloat(),
                            onValueChange = {
                                advancedSettings = advancedSettings.copy(
                                    antiAliasingLevel = it.roundToInt().coerceIn(0, 3)
                                )
                            },
                            valueRange = 0f..3f,
                            steps = 2
                        )
                        val aaLabel = when (advancedSettings.antiAliasingLevel) {
                            0 -> "Off"
                            1 -> "Low"
                            2 -> "Medium"
                            else -> "High"
                        }
                        TextBlock("Current: $aaLabel", 12.sp, FontWeight.Medium, textSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextBlock("HDR Handling", 13.sp, FontWeight.SemiBold, textPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (selectedImageHasHdr == true) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                ModeChip(
                                    label = "Preserve HDR",
                                    isSelected = advancedSettings.preserveHdrIfAvailable,
                                    onClick = {
                                        advancedSettings = advancedSettings.copy(
                                            preserveHdrIfAvailable = true
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    surface = surface,
                                    border = border,
                                    textPrimary = textPrimary,
                                    primary = primary,
                                    primaryText = primaryText
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                ModeChip(
                                    label = "Force SDR Optimize",
                                    isSelected = !advancedSettings.preserveHdrIfAvailable,
                                    onClick = {
                                        advancedSettings = advancedSettings.copy(
                                            preserveHdrIfAvailable = false
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    surface = surface,
                                    border = border,
                                    textPrimary = textPrimary,
                                    primary = primary,
                                    primaryText = primaryText
                                )
                            }
                        } else {
                            TextBlock(
                                "No HDR detected. This option is irrelevant for SDR images.",
                                12.sp,
                                FontWeight.Medium,
                                textSecondary
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
                ImageCanvasCard(
                    title = "Original Canvas",
                    subtitle = "Tap to enlarge",
                    surface = surface,
                    border = border,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                ) {
                    previewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                zoomDialogImage = ZoomDialogImage(bitmap, "Originalbild")
                            },
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
                        try {
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
                        } catch (_: OutOfMemoryError) {
                            optimizeStatus = "Image too large for current settings. Try Standard IG profile."
                            Toast.makeText(context, "Optimization failed: out of memory.", Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {
                            optimizeStatus = "Optimization failed. Please try again."
                            Toast.makeText(context, "Optimization failed.", Toast.LENGTH_LONG).show()
                        } finally {
                            isOptimizing = false
                        }
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
                var optimizedPreviewBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
                var isApplyingResultRotation by remember(file) { mutableStateOf(false) }
                LaunchedEffect(file) {
                    optimizedPreviewBitmap = withContext(Dispatchers.IO) {
                        decodePreviewBitmap(context, Uri.fromFile(file))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ImageCanvasCard(
                    title = "Result Canvas",
                    subtitle = "Tap for fullscreen + zoom",
                    surface = Color(0xFF182334),
                    border = Color(0xFF35557A),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                ) {
                    optimizedPreviewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                zoomDialogImage = ZoomDialogImage(bitmap, "Optimized Result")
                            },
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (isApplyingResultRotation) return@OutlinedButton
                                    isApplyingResultRotation = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            rotateJpegFileInPlace(file, quarterTurns = 3)
                                        }
                                        if (ok) {
                                            optimizedPreviewBitmap = withContext(Dispatchers.IO) {
                                                decodePreviewBitmap(context, Uri.fromFile(file))
                                            }
                                        } else {
                                            Toast.makeText(context, "Rotation failed.", Toast.LENGTH_SHORT).show()
                                        }
                                        isApplyingResultRotation = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("↺ Left")
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (isApplyingResultRotation) return@OutlinedButton
                                    isApplyingResultRotation = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            rotateJpegFileInPlace(file, quarterTurns = 1)
                                        }
                                        if (ok) {
                                            optimizedPreviewBitmap = withContext(Dispatchers.IO) {
                                                decodePreviewBitmap(context, Uri.fromFile(file))
                                            }
                                        } else {
                                            Toast.makeText(context, "Rotation failed.", Toast.LENGTH_SHORT).show()
                                        }
                                        isApplyingResultRotation = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Right ↻")
                            }
                        }
                    } ?: Box(modifier = Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                        TextBlock("Loading optimized preview...", 13.sp, FontWeight.Medium, textSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { showResultDetails = !showResultDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showResultDetails) "Hide details" else "Show details")
                }
                if (showResultDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF162033))
                            .border(1.dp, Color(0xFF2A3E5E), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            TextBlock("File: ${file.name}", 12.sp, FontWeight.Medium, textSecondary)
                            TextBlock("Size: ${file.length() / 1024} KB", 12.sp, FontWeight.Medium, textSecondary)
                            optimizedPreviewBitmap?.let { bmp ->
                                TextBlock("Resolution: ${bmp.width} x ${bmp.height}", 12.sp, FontWeight.Medium, textSecondary)
                            }
                            TextBlock("Mode: ${if (exportMode == ExportMode.POST) "Post" else "Story"}", 12.sp, FontWeight.Medium, textSecondary)
                        }
                    }
                }
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

    zoomDialogImage?.let { data ->
        ZoomableImageDialog(
            image = data.bitmap,
            title = data.title,
            onDismiss = { zoomDialogImage = null }
        )
    }
}

@Composable
private fun ImageCanvasCard(
    title: String,
    subtitle: String,
    surface: Color,
    border: Color,
    textPrimary: Color,
    textSecondary: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            TextBlock(title, 14.sp, FontWeight.SemiBold, textPrimary)
            TextBlock(subtitle, 12.sp, FontWeight.Medium, textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeSettingsScreen(
    themeOptions: ExpressiveThemeOptions,
    onThemeOptionsChange: (ExpressiveThemeOptions) -> Unit,
    onBack: () -> Unit,
    surface: Color,
    border: Color,
    textPrimary: Color,
    textSecondary: Color,
    primary: Color,
    primaryText: Color
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            TextBlock("Theme Settings", 24.sp, FontWeight.Bold, textPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryAction(
                label = "Back",
                onClick = onBack,
                surface = surface,
                border = border,
                textPrimary = textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface)
                    .border(1.dp, border, RoundedCornerShape(14.dp)).padding(12.dp)
            ) {
                Column {
                    TextBlock("Material 3 Expressive Optionen", 14.sp, FontWeight.SemiBold, textPrimary)
                    TextBlock("Paletten, Typografie, Kontrast und Dark/Light", 12.sp, FontWeight.Medium, textSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ModeChip(
                            label = if (themeOptions.useDarkTheme) "Dark On" else "Dark Off",
                            isSelected = true,
                            onClick = { onThemeOptionsChange(themeOptions.copy(useDarkTheme = !themeOptions.useDarkTheme)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        ModeChip(
                            label = if (themeOptions.useHighContrast) "High Contrast" else "Standard Contrast",
                            isSelected = themeOptions.useHighContrast,
                            onClick = { onThemeOptionsChange(themeOptions.copy(useHighContrast = !themeOptions.useHighContrast)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ModeChip(
                            label = "Dynamic",
                            isSelected = themeOptions.palette == ExpressivePalette.DYNAMIC,
                            onClick = { onThemeOptionsChange(themeOptions.copy(palette = ExpressivePalette.DYNAMIC)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        ModeChip(
                            label = "Midnight",
                            isSelected = themeOptions.palette == ExpressivePalette.MIDNIGHT,
                            onClick = { onThemeOptionsChange(themeOptions.copy(palette = ExpressivePalette.MIDNIGHT)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ModeChip(
                            label = "Sunset",
                            isSelected = themeOptions.palette == ExpressivePalette.SUNSET,
                            onClick = { onThemeOptionsChange(themeOptions.copy(palette = ExpressivePalette.SUNSET)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        ModeChip(
                            label = "Forest",
                            isSelected = themeOptions.palette == ExpressivePalette.FOREST,
                            onClick = { onThemeOptionsChange(themeOptions.copy(palette = ExpressivePalette.FOREST)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ModeChip(
                        label = "Mono",
                        isSelected = themeOptions.palette == ExpressivePalette.MONO,
                        onClick = { onThemeOptionsChange(themeOptions.copy(palette = ExpressivePalette.MONO)) },
                        modifier = Modifier.fillMaxWidth(),
                        surface = surface,
                        border = border,
                        textPrimary = textPrimary,
                        primary = primary,
                        primaryText = primaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ModeChip(
                            label = "Type Balanced",
                            isSelected = themeOptions.typographyStyle == ExpressiveTypographyStyle.BALANCED,
                            onClick = { onThemeOptionsChange(themeOptions.copy(typographyStyle = ExpressiveTypographyStyle.BALANCED)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        ModeChip(
                            label = "Type Compact",
                            isSelected = themeOptions.typographyStyle == ExpressiveTypographyStyle.COMPACT,
                            onClick = { onThemeOptionsChange(themeOptions.copy(typographyStyle = ExpressiveTypographyStyle.COMPACT)) },
                            modifier = Modifier.weight(1f),
                            surface = surface,
                            border = border,
                            textPrimary = textPrimary,
                            primary = primary,
                            primaryText = primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ModeChip(
                        label = "Type Reading",
                        isSelected = themeOptions.typographyStyle == ExpressiveTypographyStyle.READING,
                        onClick = { onThemeOptionsChange(themeOptions.copy(typographyStyle = ExpressiveTypographyStyle.READING)) },
                        modifier = Modifier.fillMaxWidth(),
                        surface = surface,
                        border = border,
                        textPrimary = textPrimary,
                        primary = primary,
                        primaryText = primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageDialog(image: Bitmap, title: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1524)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextBlock(title, 16.sp, FontWeight.SemiBold, Color(0xFFF3F4F6))
                TextBlock("Pinch to zoom, drag to pan", 12.sp, FontWeight.Medium, Color(0xFFA8B1C5))
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF2A3D5E))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF050913))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tap outside or press Close",
                    color = Color(0xFFA8B1C5),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
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
        FilterChip(
            selected = isSelected,
            onClick = onClick,
            label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = bg,
                selectedLabelColor = fg,
                containerColor = surface,
                labelColor = textPrimary
            )
        )
    }
}

@Composable
private fun TextBlock(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, color: Color) {
    Text(text = text, fontSize = size, fontWeight = weight, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PrimaryAction(label: String, primary: Color, primaryText: Color, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = primaryText)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit, surface: Color, border: Color, textPrimary: Color) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = textPrimary)
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
            val ext = optimized.extension.ifBlank { "jpg" }
            zos.putNextEntry(ZipEntry("instagram_optimized_${index + 1}.$ext"))
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
    if (settings.preserveHdrIfAvailable && hasHdrLikeSource(context, uri)) {
        val sourceBytes = getSourceSizeBytes(context, uri)
        val maxPreserveHdrBytes = if (settings.fileSizeMode == FileSizeMode.STRICT_1_6_MB) {
            6L * 1024L * 1024L
        } else {
            12L * 1024L * 1024L
        }

        if (sourceBytes in 1..maxPreserveHdrBytes) {
            return copyOriginalToCache(context, uri, outputNameWithSourceExtension(context, uri, outputFileName))
        }
    }

    if (
        settings.depthHandlingMode == DepthHandlingMode.PRESERVE_SOCIAL_DEPTH &&
        settings.keepDepthAndXmpMetadata &&
        hasDepthLikeMetadata(context, uri)
    ) {
        return copyOriginalToCache(context, uri, outputFileName)
    }

    try {
        return optimizeForInstagramInternal(context, uri, mode, settings, outputFileName)
    } catch (_: OutOfMemoryError) {
        // Fallback profile for very large images/devices with low memory.
        val safeSettings = settings.copy(
            fileSizeMode = FileSizeMode.STRICT_1_6_MB,
            resolutionProfile = ResolutionProfile.STANDARD_IG
        )
        return optimizeForInstagramInternal(context, uri, mode, safeSettings, outputFileName)
    }
}

private fun getSourceSizeBytes(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }
}

private fun copyOriginalToCache(context: Context, uri: Uri, outputFileName: String): File {
    val output = File(context.cacheDir, outputFileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(output).use { out -> input.copyTo(out) }
    }
    return output
}

private fun hasDepthLikeMetadata(context: Context, sourceUri: Uri): Boolean {
    return try {
        val exif = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) } ?: return false
        val xmp = exif.getAttribute(ExifInterface.TAG_XMP)?.lowercase().orEmpty()
        xmp.contains("gdepth") ||
            xmp.contains("depth") ||
            xmp.contains("disparity") ||
            xmp.contains("portrait")
    } catch (_: Exception) {
        false
    }
}

private fun optimizeForInstagramInternal(
    context: Context,
    uri: Uri,
    mode: ExportMode,
    settings: AdvancedSettings,
    outputFileName: String
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

        val sampleSize = computeDecodeSampleSize(width, height, targetWidth, targetHeight)

        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setTargetColorSpace(srgb)
        decoder.setTargetSampleSize(sampleSize)
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
        if (settings.fileSizeMode == FileSizeMode.ADAPTIVE_BEST_QUALITY) {
            val attempt16 = findBestJpegAtOrBelowTarget(working, 1_600 * 1024, 90)
            if (attempt16 != null) return attempt16
        }

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
    return min(ratioW, ratioH).toInt().coerceAtLeast(1)
}

private fun highQualityResize(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    if (source.width == targetWidth && source.height == targetHeight) return source

    val downscaleRatio = maxOf(source.width, source.height).toFloat() / maxOf(targetWidth, targetHeight).toFloat().coerceAtLeast(1f)
    val blurRadius = when {
        downscaleRatio >= 4.0f -> 2
        downscaleRatio >= 2.4f -> 1
        else -> 0
    }

    var current = if (blurRadius > 0) applyBoxBlurSafe(source, blurRadius) else source
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

private fun applyBoxBlurSafe(source: Bitmap, radius: Int): Bitmap {
    return try {
        applyBoxBlur(source, radius)
    } catch (_: OutOfMemoryError) {
        source
    } catch (_: Exception) {
        source
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

private fun rotateBitmapByQuarterTurns(source: Bitmap, quarterTurns: Int): Bitmap {
    val normalized = ((quarterTurns % 4) + 4) % 4
    if (normalized == 0) return source
    val matrix = Matrix().apply { postRotate((normalized * 90).toFloat()) }
    return try {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } catch (_: Exception) {
        source
    }
}

private fun rotateJpegFileInPlace(file: File, quarterTurns: Int): Boolean {
    if (isHdrLikeFile(file)) {
        return rotateByExifOrientationTag(file, quarterTurns)
    }
    return try {
        val source = ImageDecoder.createSource(file)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.isMutableRequired = false
        }
        val rotated = rotateBitmapByQuarterTurns(bitmap, quarterTurns)
        FileOutputStream(file, false).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
        true
    } catch (_: Exception) {
        false
    }
}

private fun outputNameWithSourceExtension(context: Context, uri: Uri, fallbackName: String): String {
    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    val ext = when {
        mime.contains("heic") || mime.contains("heif") -> "heic"
        mime.contains("avif") -> "avif"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        else -> "jpg"
    }
    val base = fallbackName.substringBeforeLast('.', fallbackName)
    return "$base.$ext"
}

private fun hasHdrLikeSource(context: Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    val mimeSuggestsHdrContainer = mime.contains("heic") || mime.contains("heif")

    val xmpSuggestsHdr = try {
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val xmp = exif?.getAttribute(ExifInterface.TAG_XMP)?.lowercase().orEmpty()
        xmp.contains("gainmap") || xmp.contains("hdrgm") || xmp.contains("ultrahdr") || xmp.contains("iso21496")
    } catch (_: Exception) {
        false
    }

    val wideGamutSuggestsHdr = try {
        var isWide = false
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            isWide = info.colorSpace?.isWideGamut == true
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.setTargetSampleSize(32)
            decoder.isMutableRequired = false
        }
        isWide
    } catch (_: Exception) {
        false
    }

    return mimeSuggestsHdrContainer || xmpSuggestsHdr || wideGamutSuggestsHdr
}

private fun isHdrLikeFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext == "heic" || ext == "heif"
}

private fun rotateByExifOrientationTag(file: File, quarterTurns: Int): Boolean {
    return try {
        val exif = ExifInterface(file.absolutePath)
        val normalizedTurns = ((quarterTurns % 4) + 4) % 4
        if (normalizedTurns == 0) return true
        val current = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val sequence = listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        val idx = sequence.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = sequence[(idx + normalizedTurns) % 4]
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, next.toString())
        exif.saveAttributes()
        true
    } catch (_: Exception) {
        false
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
    val mimeType = inferImageMimeTypeFromFile(sourceFile)
    val ext = extensionFromMimeType(mimeType)
    val fileName = if (mode == ExportMode.STORY) {
        "instagram_story_${System.currentTimeMillis()}.$ext"
    } else {
        "instagram_post_${System.currentTimeMillis()}.$ext"
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
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
        type = inferImageMimeTypeFromFile(file)
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

private fun inferImageMimeTypeFromFile(file: File): String {
    return when (file.extension.lowercase()) {
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}

private fun extensionFromMimeType(mimeType: String): String {
    return when (mimeType.lowercase()) {
        "image/heic", "image/heif" -> "heic"
        "image/avif" -> "avif"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
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

private fun loadAdvancedSettings(context: Context): AdvancedSettings {
    val prefs = context.getSharedPreferences("optimizer_settings", Context.MODE_PRIVATE)
    return AdvancedSettings(
        fileSizeMode = if (prefs.getInt("file_size_mode", 1) == 0) {
            FileSizeMode.STRICT_1_6_MB
        } else {
            FileSizeMode.ADAPTIVE_BEST_QUALITY
        },
        resolutionProfile = if (prefs.getInt("resolution_profile", 1) == 0) {
            ResolutionProfile.STANDARD_IG
        } else {
            ResolutionProfile.HIGH_DETAIL
        },
        keepDepthAndXmpMetadata = prefs.getBoolean("keep_depth_xmp", true),
        depthHandlingMode = if (prefs.getInt("depth_mode", 0) == 1) {
            DepthHandlingMode.PRESERVE_SOCIAL_DEPTH
        } else {
            DepthHandlingMode.OPTIMIZE_MAY_LOSE_DEPTH
        },
        antiAliasingLevel = prefs.getInt("aa_level", 2).coerceIn(0, 3),
        preserveHdrIfAvailable = prefs.getBoolean("preserve_hdr_if_available", true)
    )
}

private fun saveAdvancedSettings(context: Context, settings: AdvancedSettings) {
    val prefs = context.getSharedPreferences("optimizer_settings", Context.MODE_PRIVATE)
    prefs.edit()
        .putInt("file_size_mode", if (settings.fileSizeMode == FileSizeMode.STRICT_1_6_MB) 0 else 1)
        .putInt("resolution_profile", if (settings.resolutionProfile == ResolutionProfile.STANDARD_IG) 0 else 1)
        .putBoolean("keep_depth_xmp", settings.keepDepthAndXmpMetadata)
        .putInt("depth_mode", if (settings.depthHandlingMode == DepthHandlingMode.PRESERVE_SOCIAL_DEPTH) 1 else 0)
        .putInt("aa_level", settings.antiAliasingLevel)
        .putBoolean("preserve_hdr_if_available", settings.preserveHdrIfAvailable)
        .apply()
}

private fun loadThemeOptions(
    context: Context,
    fallback: ExpressiveThemeOptions
): ExpressiveThemeOptions {
    val prefs = context.getSharedPreferences("optimizer_theme", Context.MODE_PRIVATE)
    val palette = prefs.getString("palette", null)?.let {
        runCatching { ExpressivePalette.valueOf(it) }.getOrNull()
    } ?: fallback.palette
    val typography = prefs.getString("typography", null)?.let {
        runCatching { ExpressiveTypographyStyle.valueOf(it) }.getOrNull()
    } ?: fallback.typographyStyle
    return fallback.copy(
        useDarkTheme = prefs.getBoolean("dark_theme", fallback.useDarkTheme),
        palette = palette,
        typographyStyle = typography,
        useHighContrast = prefs.getBoolean("high_contrast", false)
    )
}

private fun saveThemeOptions(context: Context, options: ExpressiveThemeOptions) {
    val prefs = context.getSharedPreferences("optimizer_theme", Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("dark_theme", options.useDarkTheme)
        .putString("palette", options.palette.name)
        .putString("typography", options.typographyStyle.name)
        .putBoolean("high_contrast", options.useHighContrast)
        .apply()
}
