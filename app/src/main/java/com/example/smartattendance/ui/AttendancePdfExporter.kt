
package com.example.smartattendance.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.smartattendance.data.entity.Attendance
import com.example.smartattendance.data.entity.Student
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AttendancePdfExporter {

    fun export(
        outputStream: OutputStream,
        records: List<Attendance>,
        studentMap: Map<Long, Student>,
        reportType: String,
        period: String,
        classFilter: String,
        studentFilter: String,
        totalStudents: Int,
        presentStudents: Int,
        absentStudents: Int
    ) {

        val document = PdfDocument()

        try {

            val pageWidth = 595
            val pageHeight = 842

            val margin = 40f
            val rightEdge = 555f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = android.graphics.Color.DKGRAY
            }

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8f
                color = android.graphics.Color.BLACK
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 0.7f
                color = android.graphics.Color.LTGRAY
            }

            val columns = listOf(
                "Student" to 105f,
                "Roll No." to 60f,
                "Class" to 50f,
                "Date" to 75f,
                "Time" to 55f,
                "Status" to 55f,
                "Marked By" to 115f
            )

            val timeFormatter = SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )

            var pageNumber = 0
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = 0f

            fun drawFittedText(
                target: Canvas,
                value: String,
                x: Float,
                baseline: Float,
                maxWidth: Float,
                paint: Paint
            ) {

                var text = value

                while (
                    text.isNotEmpty() &&
                    paint.measureText(text) > maxWidth
                ) {
                    text = text.dropLast(1)
                }

                if (text != value && text.isNotEmpty()) {
                    while (
                        text.isNotEmpty() &&
                        paint.measureText("$text...") > maxWidth
                    ) {
                        text = text.dropLast(1)
                    }

                    text += "..."
                }

                target.drawText(text, x, baseline, paint)
            }

            fun finishCurrentPage() {

                val activePage = currentPage ?: return
                val activeCanvas = canvas ?: return

                activeCanvas.drawLine(
                    margin,
                    800f,
                    rightEdge,
                    800f,
                    linePaint
                )

                activeCanvas.drawText(
                    "SmartAttendance",
                    margin,
                    820f,
                    subtitlePaint
                )

                activeCanvas.drawText(
                    "Page $pageNumber",
                    500f,
                    820f,
                    subtitlePaint
                )

                document.finishPage(activePage)

                currentPage = null
                canvas = null
            }

            fun startNewPage() {

                pageNumber++

                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                ).create()

                val page = document.startPage(pageInfo)

                currentPage = page
                canvas = page.canvas

                val target = page.canvas

                target.drawText(
                    "SmartAttendance",
                    margin,
                    55f,
                    titlePaint
                )

                target.drawText(
                    "Attendance Report",
                    margin,
                    78f,
                    subtitlePaint
                )

                target.drawLine(
                    margin,
                    90f,
                    rightEdge,
                    90f,
                    linePaint
                )

                target.drawText(
                    "Report Type: $reportType",
                    margin,
                    112f,
                    subtitlePaint
                )

                target.drawText(
                    "Period: $period",
                    margin,
                    130f,
                    subtitlePaint
                )

                target.drawText(
                    "Class Filter: ${classFilter.ifBlank { "All" }}",
                    margin,
                    148f,
                    subtitlePaint
                )

                target.drawText(
                    "Student Filter: ${studentFilter.ifBlank { "All" }}",
                    margin,
                    166f,
                    subtitlePaint
                )

                target.drawText(
                    "Total Students: $totalStudents",
                    margin,
                    190f,
                    headerPaint
                )

                target.drawText(
                    "Present: $presentStudents",
                    205f,
                    190f,
                    headerPaint
                )

                if (reportType == "Daily") {
                    target.drawText(
                        "Absent: $absentStudents",
                        330f,
                        190f,
                        headerPaint
                    )
                }

                var x = margin

                columns.forEach { (heading, width) ->

                    drawFittedText(
                        target,
                        heading,
                        x,
                        222f,
                        width - 5f,
                        headerPaint
                    )

                    x += width
                }

                target.drawLine(
                    margin,
                    230f,
                    rightEdge,
                    230f,
                    linePaint
                )

                y = 250f
            }

            startNewPage()

            records.forEach { record ->

                val student = studentMap[record.studentId]
                    ?: return@forEach

                if (y + 35f > 790f) {
                    finishCurrentPage()
                    startNewPage()
                }

                val values = listOf(
                    student.studentName,
                    student.rollNumber,
                    student.className,
                    record.date,
                    timeFormatter.format(Date(record.timestamp)),
                    record.status,
                    record.markedBy
                )

                var x = margin

                values.forEachIndexed { index, value ->

                    val width = columns[index].second

                    drawFittedText(
                        canvas!!,
                        value,
                        x,
                        y,
                        width - 5f,
                        bodyPaint
                    )

                    x += width
                }

                canvas!!.drawLine(
                    margin,
                    y + 10f,
                    rightEdge,
                    y + 10f,
                    linePaint
                )

                y += 34f
            }

            finishCurrentPage()

            document.writeTo(outputStream)

        } finally {

            document.close()
        }
    }
}
