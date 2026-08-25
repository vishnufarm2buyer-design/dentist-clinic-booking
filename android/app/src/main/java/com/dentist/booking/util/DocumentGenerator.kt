package com.dentist.booking.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dentist.booking.data.model.Treatment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentGenerator {

    fun generateTreatmentReport(patientName: String, treatments: List<Treatment>): String {
        val sb = java.lang.StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val generatedDate = dateFormat.format(Date())

        sb.append("==================================================\n")
        sb.append("            TREATMENT HISTORY REPORT              \n")
        sb.append("==================================================\n")
        sb.append("Patient Name: $patientName\n")
        sb.append("Generated On: $generatedDate\n")
        sb.append("Total Records: ${treatments.size}\n")
        sb.append("==================================================\n\n")

        if (treatments.isEmpty()) {
            sb.append("No treatment records found.\n")
        } else {
            treatments.forEachIndexed { index, tr ->
                sb.append("${index + 1}. VISIT RECORD\n")
                sb.append("   Date      : ${tr.visitDate}\n")
                sb.append("   Clinic    : ${tr.clinicName ?: "N/A"}\n")
                sb.append("   Doctor    : ${tr.doctorName ?: "N/A"}\n")
                sb.append("   Notes     : ${tr.notes}\n")
                sb.append("--------------------------------------------------\n")
            }
        }
        sb.append("\n=== End of Report ===\n")
        return sb.toString()
    }

    // Share or download the report by writing to a temporary file and invoking the Android share sheet
    fun shareReport(context: Context, patientName: String, reportText: String) {
        try {
            val fileName = "Treatment_History_${patientName.replace(" ", "_")}.txt"
            val tempDir = File(context.cacheDir, "reports")
            if (!tempDir.exists()) tempDir.mkdirs()

            val file = File(tempDir, fileName)
            val writer = FileWriter(file)
            writer.write(reportText)
            writer.flush()
            writer.close()

            val uri = FileProvider.getUriForFile(
                context,
                "com.dentist.booking.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Treatment History - $patientName")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Treatment History"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
