package com.patchcam.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.patchcam.app.models.OcrLineItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraScanner(
    frameCount: Int,
    liveLines: List<OcrLineItem>,
    onLiveLines: (List<OcrLineItem>) -> Unit,
    onCaptureFrame: (List<OcrLineItem>) -> Unit,
    onGalleryFrame: (List<OcrLineItem>) -> Unit,
    onAnalyze: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraGranted = granted
        }

    var captureRequest by remember { mutableIntStateOf(0) }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                processGalleryImage(
                    context = context,
                    uri = uri,
                    onResult = onGalleryFrame
                )
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!cameraGranted) {
            EmptyCameraCard(
                onCamera = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onGallery = {
                    galleryLauncher.launch("image/*")
                }
            )
        } else {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                onLines = onLiveLines,
                captureRequest = captureRequest,
                onCaptured = onCaptureFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            )
        }

        LiveDetectionCard(liveLines)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = "Gallery"
                )
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }

            Button(
                onClick = {
                    captureRequest++
                },
                enabled = cameraGranted,
                modifier = Modifier
                    .weight(1.35f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Capture"
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (frameCount == 0) "Capture" else "Add frame"
                )
            }
        }

        if (frameCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF20242E),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Captured frames",
                                tint = Color(0xFFAFC5FF)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$frameCount frame${if (frameCount == 1) "" else "s"} captured",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Move down for more lines",
                            color = Color(0xFF8D96AA),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "For long errors, capture one section at a time. Move down until the next lines are visible, then tap Add frame.",
                        color = Color(0xFF9EA7BC),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "Analyze patch",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (cameraGranted && frameCount == 0) {
            Text(
                text = if (liveLines.isEmpty()) {
                    "OCR is looking for code and error text. You can capture even while OCR is still scanning."
                } else {
                    "${liveLines.size} line${if (liveLines.size == 1) "" else "s"} visible. Capture this section, then move down for the next section."
                },
                color = Color(0xFF8D96AA),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LiveDetectionCard(
    liveLines: List<OcrLineItem>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF171A22)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE OCR",
                        color = Color(0xFF9EA7BC),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (liveLines.isEmpty()) {
                            "Looking for readable code/error text…"
                        } else {
                            "${liveLines.size} lines visible"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = if (liveLines.isEmpty()) {
                        Color(0xFF303544)
                    } else {
                        Color(0xFF213D32)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (liveLines.isEmpty()) "SCANNING" else "TEXT FOUND",
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                        color = if (liveLines.isEmpty()) {
                            Color(0xFFB8C0D0)
                        } else {
                            Color(0xFF77E0A7)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (liveLines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = liveLines
                        .takeLast(5)
                        .joinToString("\n") { it.text },
                    color = Color(0xFFD8DCE7),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5
                )
            }
        }
    }
}

@Composable
private fun EmptyCameraCard(
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1B1E27)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = null,
                tint = Color(0xFFAFC5FF),
                modifier = Modifier.size(54.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Camera access is required",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "PatchCam reads the visible code and error automatically. You do not need to type what you see.",
                color = Color(0xFF9EA7BC),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCamera,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Enable camera")
                }

                OutlinedButton(
                    onClick = onGallery,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Gallery")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: LifecycleOwner,
    onLines: (List<OcrLineItem>) -> Unit,
    captureRequest: Int,
    onCaptured: (List<OcrLineItem>) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    val executor: ExecutorService = remember {
        Executors.newSingleThreadExecutor()
    }

    val processing = remember {
        AtomicBoolean(false)
    }

    val recognizer = remember {
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )
    }

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener(
            {
                val provider = runCatching {
                    providerFuture.get()
                }.getOrNull() ?: return@addListener

                val preview = Preview.Builder()
                    .build()

                preview.surfaceProvider = previewView.surfaceProvider

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                imageCapture = capture

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .setImageQueueDepth(1)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    processProxyImage(
                        proxy = proxy,
                        recognizer = recognizer,
                        processing = processing,
                        onResult = onLines
                    )
                }

                runCatching {
                    provider.unbindAll()

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        capture
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            runCatching {
                ProcessCameraProvider
                    .getInstance(context)
                    .get()
                    .unbindAll()
            }

            runCatching {
                recognizer.close()
            }

            executor.shutdown()
        }
    }

    LaunchedEffect(captureRequest) {
        if (captureRequest <= 0) {
            return@LaunchedEffect
        }

        val capture = imageCapture ?: return@LaunchedEffect

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {

                override fun onCaptureSuccess(
                    image: ImageProxy
                ) {
                    val mediaImage = image.image

                    if (mediaImage == null) {
                        image.close()
                        return
                    }

                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        image.imageInfo.rotationDegrees
                    )

                    recognizer.process(input)
                        .addOnSuccessListener(executor) { result ->
                            onCaptured(extractLines(result))
                        }
                        .addOnCompleteListener(executor) {
                            image.close()
                        }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    // Capture can be retried immediately.
                }
            }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = Color(0xFF454C5D),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.62f)
                .border(
                    width = 2.dp,
                    color = Color(0xFFAFC5FF),
                    shape = RoundedCornerShape(22.dp)
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = Color(0xCC11141B),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "AUTO OCR",
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                color = Color(0xFFDDE5FF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            color = Color(0xAA11141B),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Capture this section • move down for more lines",
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 7.dp
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun processProxyImage(
    proxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    processing: AtomicBoolean,
    onResult: (List<OcrLineItem>) -> Unit
) {
    val mediaImage = proxy.image

    if (mediaImage == null) {
        proxy.close()
        return
    }

    if (!processing.compareAndSet(false, true)) {
        proxy.close()
        return
    }

    val input = InputImage.fromMediaImage(
        mediaImage,
        proxy.imageInfo.rotationDegrees
    )

    recognizer.process(input)
        .addOnSuccessListener { result ->
            onResult(extractLines(result))
        }
        .addOnCompleteListener {
            processing.set(false)
            proxy.close()
        }
}

private fun extractLines(
    result: MlText
): List<OcrLineItem> {
    return result.textBlocks
        .flatMap { block ->
            block.lines.mapNotNull { line ->
                val text = line.text.trim()

                if (text.isBlank()) {
                    return@mapNotNull null
                }

                val box = line.boundingBox ?: Rect(
                    0,
                    0,
                    0,
                    0
                )

                OcrLineItem(
                    text = text,
                    boundingBox = box
                )
            }
        }
        .sortedWith(
            compareBy<OcrLineItem> { it.boundingBox.top }
                .thenBy { it.boundingBox.left }
        )
}

private fun processGalleryImage(
    context: Context,
    uri: Uri,
    onResult: (List<OcrLineItem>) -> Unit
) {
    runCatching {
        val image = InputImage.fromFilePath(
            context,
            uri
        )

        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        recognizer.process(image)
            .addOnSuccessListener { result ->
                onResult(extractLines(result))
            }
            .addOnCompleteListener {
                recognizer.close()
            }
    }
}
