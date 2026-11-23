package com.example.smartattendance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.data.entity.Attendance
import com.example.smartattendance.data.entity.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManualAttendanceSection(
    database: AttendanceDatabase,
    students: List<Student>
) {
    val scope = rememberCoroutineScope()

    val eligibleStudents = students.filter { it.isActive }

    var selectedStudentId by remember {
        mutableStateOf<Long?>(null)
    }

    var studentMenuExpanded by remember {
        mutableStateOf(false)
    }

    var selectedStatus by remember {
        mutableStateOf("PRESENT")
    }

    var attendanceDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var successMessage by remember {
        mutableStateOf<String?>(null)
    }

    val selectedStudent = eligibleStudents.find {
        it.id == selectedStudentId
    }

    LaunchedEffect(eligibleStudents) {
        if (selectedStudentId == null ||
            eligibleStudents.none { it.id == selectedStudentId }
        ) {
            selectedStudentId = eligibleStudents.firstOrNull()?.id
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "MANUAL ATTENDANCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Mark or correct attendance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Use this option when face recognition is unavailable or a teacher needs to correct a record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Student selection
            Text(
                text = "Select student",
                fontWeight = FontWeight.SemiBold
            )

            Box(modifier = Modifier.fillMaxWidth()) {

                OutlinedButton(
                    onClick = {
                        studentMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = eligibleStudents.isNotEmpty()
                ) {
                    Text(
                        selectedStudent?.let {
                            "${it.studentName} (${it.rollNumber})"
                        } ?: "Select a student"
                    )
                }

                DropdownMenu(
                    expanded = studentMenuExpanded,
                    onDismissRequest = {
                        studentMenuExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    eligibleStudents.forEach { student ->

                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        student.studentName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${student.rollNumber} • ${student.className}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = {
                                selectedStudentId = student.id
                                studentMenuExpanded = false
                                errorMessage = null
                                successMessage = null
                            }
                        )
                    }
                }
            }

            if (eligibleStudents.isEmpty()) {
                Text(
                    text = "No active students are available. Register a student first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Date
            Text(
                text = "Attendance date",
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = attendanceDate,
                onValueChange = {
                    attendanceDate = it
                    errorMessage = null
                    successMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Date (yyyy-MM-dd)")
                },
                placeholder = {
                    Text("2026-11-23")
                },
                singleLine = true,
                enabled = !isSaving
            )

            Text(
                text = "Enter the date in yyyy-MM-dd format. Future dates are not allowed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Attendance status
            Text(
                text = "Attendance status",
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedStatus == "PRESENT",
                    onClick = {
                        selectedStatus = "PRESENT"
                        successMessage = null
                    },
                    label = {
                        Text("Present")
                    },
                    enabled = !isSaving
                )

                FilterChip(
                    selected = selectedStatus == "ABSENT",
                    onClick = {
                        selectedStatus = "ABSENT"
                        successMessage = null
                    },
                    label = {
                        Text("Absent")
                    },
                    enabled = !isSaving
                )
            }

            // Save
            Button(
                onClick = {

                    if (isSaving) return@Button

                    errorMessage = null
                    successMessage = null

                    val student = selectedStudent

                    if (student == null) {
                        errorMessage = "Please select a student."
                        return@Button
                    }

                    val date = attendanceDate.trim()

                    if (!isValidAttendanceDate(date)) {
                        errorMessage = "Invalid date. Use yyyy-MM-dd and do not select a future date."
                        return@Button
                    }

                    scope.launch {

                        isSaving = true

                        try {

                            val result = withContext(Dispatchers.IO) {

                                val dao = database.attendanceDao()

                                val existing = dao.getAttendanceForStudentAndDate(
                                    studentId = student.id,
                                    date = date
                                )

                                if (existing != null) {

                                    dao.update(
                                        existing.copy(
                                            status = selectedStatus,
                                            markedBy = "MANUAL",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )

                                    "Attendance updated successfully."

                                } else {

                                    val insertedId = dao.insert(
                                        Attendance(
                                            studentId = student.id,
                                            date = date,
                                            status = selectedStatus,
                                            markedBy = "MANUAL"
                                        )
                                    )

                                    if (insertedId == -1L) {

                                        // Handles a simultaneous insert safely.
                                        val latest = dao.getAttendanceForStudentAndDate(
                                            studentId = student.id,
                                            date = date
                                        )

                                        if (latest != null) {
                                            dao.update(
                                                latest.copy(
                                                    status = selectedStatus,
                                                    markedBy = "MANUAL",
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }

                                        "Attendance updated successfully."

                                    } else {
                                        "Attendance saved successfully."
                                    }
                                }
                            }

                            successMessage = "$result ${student.studentName} — $date ($selectedStatus)."

                        } catch (e: Exception) {

                            errorMessage = "Could not save attendance: ${
                                e.localizedMessage ?: "Database error."
                            }"

                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSaving &&
                        selectedStudent != null &&
                        eligibleStudents.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {

                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Manual Attendance")
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (successMessage != null) {
                Text(
                    text = successMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun isValidAttendanceDate(value: String): Boolean {

    return try {

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.isLenient = false

        val parsedDate = formatter.parse(value) ?: return false

        // Reject dates that do not exactly match yyyy-MM-dd.
        if (formatter.format(parsedDate) != value) {
            return false
        }

        val today = formatter.format(Date())

        // ISO date strings can safely be compared lexicographically.
        value <= today

    } catch (e: Exception) {
        false
    }
}