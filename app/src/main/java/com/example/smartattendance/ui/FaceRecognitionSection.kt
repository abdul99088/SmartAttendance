
package com.example.smartattendance.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.recognition.FaceRecognitionRepository
import com.example.smartattendance.recognition.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FaceRecognitionSection(database: AttendanceDatabase) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context, database) {
        FaceRecognitionRepository(context, database)
    }

    DisposableEffect(repository) {
        onDispose {
            repository.close()
        }
    }

    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultStatus by remember { mutableStateOf<RecognitionStatus?>(null) }
    var similarityText by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->

        if (uri == null) {
            resultMessage = "No photo selected."
            return@rememberLauncherForActivityResult
        }

        if (!isProcessing) {
            scope.launch {

                isProcessing = true
                resultMessage = "Analyzing face and checking enrolled students..."
                resultStatus = null
                similarityText = ""

                var bitmap: Bitmap? = null

                try {

                    bitmap = withContext(Dispatchers.IO) {
                        decodeRecognitionBitmap(context, uri)
                    } ?: throw Exception("Unable to open the selected image.")

                    val result = repository.recognizeAndMarkAttendance(bitmap)

                    resultMessage = result.message
                    resultStatus = result.status

                    similarityText = result.similarity?.let {
                        "Similarity: ${"%.3f".format(java.util.Locale.US, it)}"
                    } ?: ""

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {

                    resultMessage = e.localizedMessage
                        ?: "Face recognition failed."

                    resultStatus = null

                } finally {
                    bitmap?.recycle()
                    isProcessing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        HorizontalDivider()

        Text(
            text = "FACE RECOGNITION & ATTENDANCE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Select a photograph. The system will compare the detected face against enrolled students and mark attendance if a sufficiently close match is found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                resultMessage = ""
                resultStatus = null
                galleryLauncher.launch("image/*")
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Select Photo for Recognition")
        }

        if (isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (resultMessage.isNotBlank()) {

            val resultColor = when (resultStatus) {
                RecognitionStatus.ATTENDANCE_MARKED ->
                    MaterialTheme.colorScheme.primary

                RecognitionStatus.ALREADY_MARKED ->
                    MaterialTheme.colorScheme.tertiary

                RecognitionStatus.UNKNOWN_FACE,
                RecognitionStatus.NO_ENROLLED_FACES ->
                    MaterialTheme.colorScheme.error

                null -> MaterialTheme.colorScheme.onSurface
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = resultMessage,
                        color = resultColor,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (similarityText.isNotBlank()) {
                        Text(
                            text = similarityText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun decodeRecognitionBitmap(
    context: Context,
    uri: Uri
): Bitmap? {

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    var sampleSize = 1

    while (
        bounds.outWidth / sampleSize > 1600 ||
        bounds.outHeight / sampleSize > 1600
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}
