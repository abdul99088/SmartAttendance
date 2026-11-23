
package com.example.smartattendance.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.data.entity.Attendance
import com.example.smartattendance.data.entity.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceDashboardSection(database: AttendanceDatabase) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val attendance by database.attendanceDao()
        .getAllAttendance()
        .collectAsState(initial = emptyList())

    val students by database.studentDao()
        .getAllStudents()
        .collectAsState(initial = emptyList())

    var selectedView by remember { mutableStateOf("Daily") }

    var selectedDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    var selectedMonth by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        )
    }

    var classFilter by remember { mutableStateOf("") }
    var studentFilter by remember { mutableStateOf("") }

    var csvToExport by remember { mutableStateOf("") }

    var exportMessage by remember { mutableStateOf("") }
    var exportError by remember { mutableStateOf(false) }

    var pdfMessage by remember { mutableStateOf("") }
    var pdfError by remember { mutableStateOf(false) }

    val studentMap = students.associateBy { it.id }

    // Active students who have given consent,
    // filtered by class and student search.
    val eligibleStudents = students.filter { student ->

        student.isActive &&
                student.consentGiven &&
                (
                        classFilter.isBlank() ||
                                student.className.contains(
                                    classFilter.trim(),
                                    ignoreCase = true
                                )
                        ) &&
                (
                        studentFilter.isBlank() ||
                                student.studentName.contains(
                                    studentFilter.trim(),
                                    ignoreCase = true
                                ) ||
                                student.rollNumber.contains(
                                    studentFilter.trim(),
                                    ignoreCase = true
                                )
                        )
    }

    val eligibleIds = eligibleStudents.map { it.id }.toSet()

    // Apply daily or monthly date filter.
    val periodAttendance = attendance.filter { record ->

        val matchesPeriod = if (selectedView == "Daily") {
            record.date == selectedDate.trim()
        } else {
            record.date.startsWith(selectedMonth.trim())
        }

        matchesPeriod && record.studentId in eligibleIds

    }.sortedByDescending { it.timestamp }

    val presentRecords = periodAttendance.filter {
        it.status.equals("PRESENT", ignoreCase = true)
    }

    val presentStudentIds = presentRecords
        .map { it.studentId }
        .toSet()

    val absentCount = if (selectedView == "Daily") {
        (eligibleIds - presentStudentIds).size
    } else {
        0
    }

    val uniquePresentStudents = presentStudentIds.size
    val totalAttendanceMarks = presentRecords.size

    // CSV Save As launcher.
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->

        if (uri != null) {

            scope.launch {

                try {

                    withContext(Dispatchers.IO) {

                        val outputStream =
                            context.contentResolver.openOutputStream(uri)
                                ?: throw IOException(
                                    "Unable to open selected file."
                                )

                        outputStream.bufferedWriter(Charsets.UTF_8).use {
                            it.write(csvToExport)
                        }
                    }

                    exportError = false
                    exportMessage = "CSV exported successfully!"

                } catch (e: Exception) {

                    exportError = true
                    exportMessage = "CSV export failed: ${e.message}"
                }
            }

        } else {

            exportMessage = "Export cancelled."
            exportError = false
        }
    }

    // PDF Save As launcher.
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->

        if (uri != null) {

            scope.launch {

                try {

                    withContext(Dispatchers.IO) {

                        val outputStream =
                            context.contentResolver.openOutputStream(uri)
                                ?: throw IOException(
                                    "Unable to open PDF file."
                                )

                        outputStream.use {

                            AttendancePdfExporter.export(
                                outputStream = it,
                                records = periodAttendance,
                                studentMap = studentMap,
                                reportType = selectedView,
                                period = if (selectedView == "Daily") {
                                    selectedDate.trim()
                                } else {
                                    selectedMonth.trim()
                                },
                                classFilter = classFilter,
                                studentFilter = studentFilter,
                                totalStudents = eligibleStudents.size,
                                presentStudents = uniquePresentStudents,
                                absentStudents = absentCount
                            )
                        }
                    }

                    pdfError = false
                    pdfMessage = "PDF report exported successfully!"

                } catch (e: Exception) {

                    pdfError = true
                    pdfMessage = "PDF export failed: ${e.message}"
                }
            }

        } else {

            pdfMessage = "PDF export cancelled."
            pdfError = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        HorizontalDivider()

        Text(
            text = "ATTENDANCE DASHBOARD",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Attendance Overview",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Monitor student attendance and review recorded attendance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Daily / Monthly selector.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            FilterChip(
                selected = selectedView == "Daily",
                onClick = {
                    selectedView = "Daily"
                    exportMessage = ""
                    pdfMessage = ""
                },
                label = { Text("Daily") }
            )

            FilterChip(
                selected = selectedView == "Monthly",
                onClick = {
                    selectedView = "Monthly"
                    exportMessage = ""
                    pdfMessage = ""
                },
                label = { Text("Monthly") }
            )
        }

        // Date selector.
        if (selectedView == "Daily") {

            OutlinedTextField(
                value = selectedDate,
                onValueChange = {
                    selectedDate = it
                    exportMessage = ""
                    pdfMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Attendance date") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                supportingText = {
                    Text("Enter date in yyyy-MM-dd format")
                }
            )

        } else {

            OutlinedTextField(
                value = selectedMonth,
                onValueChange = {
                    selectedMonth = it
                    exportMessage = ""
                    pdfMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Attendance month") },
                placeholder = { Text("yyyy-MM") },
                singleLine = true,
                supportingText = {
                    Text("Enter month in yyyy-MM format")
                }
            )
        }

        // Class filter.
        OutlinedTextField(
            value = classFilter,
            onValueChange = {
                classFilter = it
                exportMessage = ""
                pdfMessage = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Filter by class") },
            placeholder = { Text("e.g. CE-5A") },
            singleLine = true
        )

        // Student filter.
        OutlinedTextField(
            value = studentFilter,
            onValueChange = {
                studentFilter = it
                exportMessage = ""
                pdfMessage = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search student") },
            placeholder = { Text("Name or roll number") },
            singleLine = true
        )

        // Daily statistics.
        if (selectedView == "Daily") {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                DashboardStatCard(
                    title = "Total",
                    value = eligibleStudents.size.toString(),
                    modifier = Modifier.weight(1f)
                )

                DashboardStatCard(
                    title = "Present",
                    value = uniquePresentStudents.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )

                DashboardStatCard(
                    title = "Absent",
                    value = absentCount.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }

        } else {

            // Monthly statistics.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                DashboardStatCard(
                    title = "Present Marks",
                    value = totalAttendanceMarks.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )

                DashboardStatCard(
                    title = "Students Present",
                    value = uniquePresentStudents.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider()

        Text(
            text = if (selectedView == "Daily") {
                "Daily Attendance Records"
            } else {
                "Monthly Attendance Records"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "${periodAttendance.size} attendance records found",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // CSV EXPORT BUTTON.
        Button(
            onClick = {

                csvToExport = buildAttendanceCsv(
                    records = periodAttendance,
                    studentMap = studentMap
                )

                val period = if (selectedView == "Daily") {
                    selectedDate.trim()
                } else {
                    selectedMonth.trim()
                }

                val safePeriod = period.replace(
                    Regex("[^0-9-]"),
                    ""
                )

                val filename =
                    "attendance_${selectedView.lowercase(Locale.US)}_$safePeriod.csv"

                exportMessage = ""
                csvLauncher.launch(filename)
            },
            enabled = periodAttendance.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Export Attendance CSV")
        }

        if (exportMessage.isNotBlank()) {

            Text(
                text = exportMessage,
                color = if (exportError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // PDF EXPORT BUTTON.
        Button(
            onClick = {

                val period = if (selectedView == "Daily") {
                    selectedDate.trim()
                } else {
                    selectedMonth.trim()
                }

                val safePeriod = period.replace(
                    Regex("[^0-9-]"),
                    ""
                )

                val filename =
                    "SmartAttendance_${selectedView.lowercase(Locale.US)}_$safePeriod.pdf"

                pdfMessage = ""
                pdfLauncher.launch(filename)
            },
            enabled = periodAttendance.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Export Attendance PDF")
        }

        if (pdfMessage.isNotBlank()) {

            Text(
                text = pdfMessage,
                color = if (pdfError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "Exports use your current date, class and student filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Attendance records.
        if (periodAttendance.isEmpty()) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {

                    Text(
                        text = "No attendance records found.",
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Try another date, month, class or student.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        } else {

            periodAttendance.forEach { record ->

                val student = studentMap[record.studentId]

                if (student != null) {

                    AttendanceRecordCard(
                        record = record,
                        student = student
                    )
                }
            }
        }
    }
}

// CSV GENERATION.
private fun buildAttendanceCsv(
    records: List<Attendance>,
    studentMap: Map<Long, Student>
): String {

    val csv = StringBuilder()

    // UTF-8 BOM helps Excel display text correctly.
    csv.append('\uFEFF')

    csv.appendLine(
        "Student ID,Student Name,Roll Number,Class,Date,Time,Status,Marked By"
    )

    val timeFormatter = SimpleDateFormat(
        "HH:mm:ss",
        Locale.getDefault()
    )

    records.forEach { record ->

        val student = studentMap[record.studentId]
            ?: return@forEach

        val time = timeFormatter.format(Date(record.timestamp))

        val values = listOf(
            student.id.toString(),
            student.studentName,
            student.rollNumber,
            student.className,
            record.date,
            time,
            record.status,
            record.markedBy
        )

        csv.appendLine(
            values.joinToString(",") { csvEscape(it) }
        )
    }

    return csv.toString()
}

// CSV escaping for commas, quotation marks and newlines.
private fun csvEscape(value: String): String {

    return "\"" + value.replace("\"", "\"\"") + "\""
}

// STATISTICS CARD.
@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

// ATTENDANCE RECORD CARD.
@Composable
private fun AttendanceRecordCard(
    record: Attendance,
    student: Student
) {

    val isPresent = record.status.equals(
        "PRESENT",
        ignoreCase = true
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {

                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
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

                Text(
                    text = "Date: ${record.date}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Marked by: ${record.markedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = record.status,
                color = if (isPresent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
