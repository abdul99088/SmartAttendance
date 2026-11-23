
package com.example.smartattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.data.entity.Student
import com.example.smartattendance.ui.AttendanceDashboardSection
import com.example.smartattendance.ui.CameraLiveRecognitionSection
import com.example.smartattendance.ui.FaceEnrollmentSection
import com.example.smartattendance.ui.FaceRecognitionSection
import com.example.smartattendance.ui.ManualAttendanceSection
import com.example.smartattendance.ui.theme.SmartAttendanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AttendanceDatabase.getDatabase(applicationContext)

        setContent {
            SmartAttendanceTheme {
                StudentRegistrationScreen(database = database)
            }
        }
    }
}

@Composable
fun StudentRegistrationScreen(database: AttendanceDatabase) {

    val scope = rememberCoroutineScope()

    val students by database.studentDao()
        .getAllStudents()
        .collectAsState(initial = emptyList())

    var studentName by remember { mutableStateOf("") }
    var rollNumber by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }

    var consentGiven by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var rollError by remember { mutableStateOf<String?>(null) }
    var classError by remember { mutableStateOf<String?>(null) }
    var consentError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "SMART ATTENDANCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Face Recognition System",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Manage students and prepare your class for automated attendance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Registered students counter
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    Text(
                        text = "REGISTERED STUDENTS",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = students.size.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Students stored in your local database",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Registration section
            Text(
                text = "Register a student",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = studentName,
                onValueChange = {
                    studentName = it
                    nameError = null
                    generalError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Student full name") },
                placeholder = { Text("e.g. Ali Ahmed") },
                singleLine = true,
                enabled = !isSaving,
                isError = nameError != null,
                supportingText = {
                    Text(nameError ?: "2–60 characters")
                }
            )

            OutlinedTextField(
                value = rollNumber,
                onValueChange = {
                    rollNumber = it
                    rollError = null
                    generalError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Roll number") },
                placeholder = { Text("e.g. 2024-CE-101") },
                singleLine = true,
                enabled = !isSaving,
                isError = rollError != null,
                supportingText = {
                    Text(rollError ?: "2–30 characters")
                }
            )

            OutlinedTextField(
                value = className,
                onValueChange = {
                    className = it
                    classError = null
                    generalError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Class / Section") },
                placeholder = { Text("e.g. CE-5A") },
                singleLine = true,
                enabled = !isSaving,
                isError = classError != null,
                supportingText = {
                    Text(classError ?: "2–30 characters")
                }
            )

            // Biometric consent
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Checkbox(
                        checked = consentGiven,
                        onCheckedChange = {
                            consentGiven = it
                            consentError = null
                        },
                        enabled = !isSaving
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {

                        Text(
                            text = "Biometric data consent",
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "I confirm that the student has been informed and has consented to face-data processing for attendance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (consentError != null) {
                            Text(
                                text = consentError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Register student
            Button(
                onClick = {

                    if (isSaving) return@Button

                    val name = studentName.trim()
                    val roll = rollNumber.trim()
                    val section = className.trim()

                    nameError = null
                    rollError = null
                    classError = null
                    consentError = null
                    generalError = null

                    // Name validation
                    if (name.isBlank()) {
                        nameError = "Student name is required."
                    } else if (name.length !in 2..60) {
                        nameError = "Name must contain 2–60 characters."
                    } else if (name.none { it.isLetter() }) {
                        nameError = "Enter a valid name containing letters."
                    } else if (name.any { it.isISOControl() }) {
                        nameError = "Name contains invalid characters."
                    }

                    // Roll number validation
                    if (roll.isBlank()) {
                        rollError = "Roll number is required."
                    } else if (roll.length !in 2..30) {
                        rollError = "Roll number must contain 2–30 characters."
                    } else if (roll.any { it.isISOControl() }) {
                        rollError = "Roll number contains invalid characters."
                    }

                    // Class validation
                    if (section.isBlank()) {
                        classError = "Class / section is required."
                    } else if (section.length !in 2..30) {
                        classError = "Class must contain 2–30 characters."
                    } else if (section.none { it.isLetterOrDigit() }) {
                        classError = "Enter a valid class or section."
                    } else if (section.any { it.isISOControl() }) {
                        classError = "Class contains invalid characters."
                    }

                    // Consent validation
                    if (!consentGiven) {
                        consentError = "Consent is required before registration."
                    }

                    // Stop if validation fails
                    if (
                        nameError != null ||
                        rollError != null ||
                        classError != null ||
                        consentError != null
                    ) {
                        return@Button
                    }

                    scope.launch {

                        isSaving = true

                        try {

                            val existing = withContext(Dispatchers.IO) {
                                database.studentDao().findByRollNumber(roll)
                            }

                            if (existing != null) {

                                rollError = "This roll number is already registered."

                            } else {

                                withContext(Dispatchers.IO) {
                                    database.studentDao().insert(
                                        Student(
                                            studentName = name,
                                            rollNumber = roll,
                                            className = section,
                                            consentGiven = true
                                        )
                                    )
                                }

                                studentName = ""
                                rollNumber = ""
                                className = ""
                                consentGiven = false

                                generalError = null
                            }

                        } catch (e: Exception) {

                            generalError = "Registration failed: ${
                                e.localizedMessage ?: "Unexpected database error."
                            }"

                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {

                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Register Student")
                }
            }

            if (generalError != null) {
                Text(
                    text = generalError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Registered students list
            Text(
                text = "Registered students (${students.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (students.isEmpty()) {

                Text(
                    text = "No students registered yet. Add your first student above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            } else {

                students.forEach { student ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column(modifier = Modifier.weight(1f)) {

                                Text(
                                    text = student.studentName,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Roll: ${student.rollNumber}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "Class: ${student.className}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = if (student.consentGiven) {
                                    "CONSENTED"
                                } else {
                                    "NO CONSENT"
                                },
                                color = if (student.consentGiven) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Face enrollment
            FaceEnrollmentSection(
                students = students,
                database = database
            )

            HorizontalDivider()

            // Gallery-based face recognition
            FaceRecognitionSection(
                database = database
            )

            HorizontalDivider()

            // Live camera recognition
            CameraLiveRecognitionSection(
                database = database
            )

            HorizontalDivider()

            // Manual attendance
            ManualAttendanceSection(
                database = database,
                students = students
            )

            HorizontalDivider()

            // Attendance dashboard
            AttendanceDashboardSection(
                database = database
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
