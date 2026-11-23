
package com.example.smartattendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.recognition.FaceRecognitionRepository
import com.example.smartattendance.recognition.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
fun CameraLiveRecognitionSection(database: AttendanceDatabase) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraStarted by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf("") }
    var similarityText by remember { mutableStateOf("") }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted

            if (!granted) {
                cameraError = "Camera permission was denied."
            } else {
                cameraError = null
            }
        }

    val repository = remember(context, database) {
        FaceRecognitionRepository(
            context.applicationContext,
            database
        )
    }

    DisposableEffect(repository) {
        onDispose {
            repository.close()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    val processing = remember { AtomicBoolean(false) }
    val lastAnalysisTime = remember { AtomicLong(0L) }
    val cameraActive = remember { AtomicBoolean(true) }

    DisposableEffect(cameraExecutor) {
        onDispose {
            cameraActive.set(false)
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(
        hasPermission,
        previewView,
        lifecycleOwner
    ) {

        var provider: ProcessCameraProvider? = null
        val active = AtomicBoolean(true)

        if (hasPermission) {

            cameraStarted = false
            cameraError = null

            val future = ProcessCameraProvider.getInstance(context)

            future.addListener({

                if (!active.get()) {
                    return@addListener
                }

                try {

                    val cameraProvider = future.get()
                    provider = cameraProvider

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->

                        val now = SystemClock.elapsedRealtime()
                        val previous = lastAnalysisTime.get()

                        if (
                            !cameraActive.get() ||
                            processing.get() ||
                            now - previous < 2000L
                        ) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        if (!lastAnalysisTime.compareAndSet(previous, now)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        if (!processing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        var bitmap: Bitmap? = null

                        try {

                            // Convert the camera's YUV frame into a Bitmap.
                            bitmap = imageProxyToBitmap(imageProxy)

                            // Correct the camera frame rotation.
                            val rotation =
                                imageProxy.imageInfo.rotationDegrees

                            if (rotation != 0) {

                                val matrix = Matrix().apply {
                                    postRotate(rotation.toFloat())
                                }

                                val rotated = Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    bitmap.width,
                                    bitmap.height,
                                    matrix,
                                    true
                                )

                                bitmap.recycle()
                                bitmap = rotated
                            }

                        } catch (e: Exception) {

                            bitmap?.recycle()
                            bitmap = null

                        } finally {
                            imageProxy.close()
                        }

                        val frameBitmap = bitmap

                        if (frameBitmap == null) {
                            processing.set(false)
                            return@setAnalyzer
                        }

                        scope.launch {

                            try {

                                val result = withContext(Dispatchers.IO) {
                                    repository.recognizeAndMarkAttendance(
                                        frameBitmap
                                    )
                                }

                                resultMessage = result.message

                                similarityText =
                                    result.similarity?.let {
                                        "Similarity: ${
                                            String.format(
                                                "%.2f",
                                                it
                                            )
                                        }"
                                    } ?: ""

                                if (
                                    result.status ==
                                    RecognitionStatus.ATTENDANCE_MARKED ||
                                    result.status ==
                                    RecognitionStatus.ALREADY_MARKED
                                ) {
                                    cameraActive.set(false)
                                }

                            } catch (e: Exception) {

                                resultMessage =
                                    "Recognition error: ${
                                        e.localizedMessage
                                            ?: "Please try again."
                                    }"

                            } finally {

                                frameBitmap.recycle()
                                processing.set(false)
                            }
                        }
                    }

                    // Prefer the front camera, otherwise use the back camera.
                    val selector = when {
                        cameraProvider.hasCamera(
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        ) -> CameraSelector.DEFAULT_FRONT_CAMERA

                        cameraProvider.hasCamera(
                            CameraSelector.DEFAULT_BACK_CAMERA
                        ) -> CameraSelector.DEFAULT_BACK_CAMERA

                        else -> throw IllegalStateException(
                            "No camera is available on this device."
                        )
                    }

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        analysis
                    )

                    cameraStarted = true

                } catch (e: Exception) {

                    cameraStarted = false

                    cameraError =
                        e.localizedMessage
                            ?: "Unable to start the camera."

                }

            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            active.set(false)
            provider?.unbindAll()
            cameraStarted = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "LIVE CAMERA RECOGNITION",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Position one face in front of the camera. The system will scan periodically and mark attendance when a registered face is recognized.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasPermission) {

            Button(
                onClick = {
                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Camera Permission")
            }

        } else {

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )

            Text(
                text = if (cameraStarted) {
                    if (cameraActive.get()) {
                        "Camera is running. Scanning every 2 seconds."
                    } else {
                        "Recognition completed. Camera scanning paused."
                    }
                } else {
                    "Starting camera..."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (
                !cameraActive.get() &&
                resultMessage.isNotBlank()
            ) {

                Button(
                    onClick = {

                        resultMessage = ""
                        similarityText = ""
                        cameraError = null

                        lastAnalysisTime.set(0L)
                        cameraActive.set(true)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Again")
                }
            }
        }

        if (cameraError != null) {

            Text(
                text = cameraError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (resultMessage.isNotBlank()) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = resultMessage,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (similarityText.isNotBlank()) {
                        Text(
                            text = similarityText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts a CameraX YUV_420_888 ImageProxy into an Android Bitmap.
 *
 * This avoids depending on the ImageProxy.toBitmap() extension.
 */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {

    require(image.format == ImageFormat.YUV_420_888) {
        "Unsupported camera image format."
    }

    val width = image.width
    val height = image.height

    val planes = image.planes

    require(planes.size >= 3) {
        "Camera image does not contain three YUV planes."
    }

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer.duplicate()
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride

    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride

    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val ySize = width * height
    val nv21 = ByteArray(ySize + ySize / 2)

    // Copy the Y (brightness) plane.
    for (row in 0 until height) {
        for (col in 0 until width) {

            val sourceIndex =
                row * yRowStride + col * yPixelStride

            nv21[row * width + col] =
                yBuffer.get(sourceIndex)
        }
    }

    // Interleave V and U values to produce NV21.
    var outputIndex = ySize

    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {

            val vIndex =
                row * vRowStride + col * vPixelStride

            val uIndex =
                row * uRowStride + col * uPixelStride

            nv21[outputIndex++] = vBuffer.get(vIndex)
            nv21[outputIndex++] = uBuffer.get(uIndex)
        }
    }

    val yuvImage = YuvImage(
        nv21,
        ImageFormat.NV21,
        width,
        height,
        null
    )

    val outputStream = ByteArrayOutputStream()

    val success = yuvImage.compressToJpeg(
        Rect(0, 0, width, height),
        90,
        outputStream
    )

    check(success) {
        "Unable to convert camera frame."
    }

    val jpegBytes = outputStream.toByteArray()

    return BitmapFactory.decodeByteArray(
        jpegBytes,
        0,
        jpegBytes.size
    ) ?: throw IllegalStateException(
        "Unable to decode camera frame."
    )
}
