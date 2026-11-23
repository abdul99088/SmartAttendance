
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
import com.example.smartattendance.data.entity.Student
import com.example.smartattendance.recognition.FaceEnrollmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FaceEnrollmentSection(
    students: List<Student>,
    database: AttendanceDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context, database) {
        FaceEnrollmentRepository(context, database)
    }

    DisposableEffect(repository) {
        onDispose {
            repository.close()
        }
    }

    var selectedStudentId by remember {
        mutableStateOf<Long?>(null)
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var statusMessage by remember {
        mutableStateOf("")
    }

    var statusIsError by remember {
        mutableStateOf(false)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->

        val studentId = selectedStudentId

        if (uri != null && studentId != null) {

            scope.launch {
                isProcessing = true
                statusMessage = "Detecting face and generating embedding..."
                statusIsError = false

                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeGalleryBitmap(context, uri)
                    } ?: throw Exception("Unable to open selected image.")

                    try {
                        val sampleId = repository.enrollFace(
                            studentId = studentId,
                            bitmap = bitmap
                        )

                        statusMessage =
                            "Face enrolled successfully! Sample ID: $sampleId"

                    } finally {
                        bitmap.recycle()
                    }

                } catch (e: Exception) {
                    statusMessage =
                        e.localizedMessage ?: "Face enrollment failed."

                    statusIsError = true

                } finally {
                    isProcessing = false
                    selectedStudentId = null
                }
            }
        } else {
            selectedStudentId = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        HorizontalDivider()

        Text(
            text = "FACE ENROLLMENT",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Select a registered student and add a face photograph. Each successful enrollment creates an encrypted face sample.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (students.isEmpty()) {
            Text("Register a student before enrolling a face.")
        }

        students.forEach { student ->

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = student.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Roll: ${student.rollNumber} • ${student.className}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!student.consentGiven) {
                        Text(
                            text = "Biometric consent is required.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (!student.isActive) {
                        Text(
                            text = "This student is inactive.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Button(
                            onClick = {
                                selectedStudentId = student.id
                                galleryLauncher.launch("image/*")
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Face Photo")
                        }
                    }
                }
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun decodeGalleryBitmap(
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
