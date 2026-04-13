package com.example.diariosync.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.example.diariosync.domain.model.Operacion
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    fun exportar(context: Context, operaciones: List<Operacion>, nombreSugerido: String = "operaciones"): android.net.Uri {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Operaciones")

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val header = sheet.createRow(0)
        listOf("Tipo", "Producto", "Cantidad", "Precio", "Total", "Fecha", "Dispositivo")
            .forEachIndexed { i, titulo ->
                header.createCell(i).apply {
                    setCellValue(titulo)
                    cellStyle = headerStyle
                }
            }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        operaciones.forEachIndexed { index, op ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(op.tipo.name)
            row.createCell(1).setCellValue(op.producto)
            row.createCell(2).setCellValue(op.cantidad)
            row.createCell(3).setCellValue(op.precio)
            row.createCell(4).setCellValue(op.total)
            row.createCell(5).setCellValue(sdf.format(Date(op.timestamp)))
            row.createCell(6).setCellValue(op.deviceId.take(8))
        }

        for (i in 0..6) {
            sheet.setColumnWidth(i, 20 * 256)
        }

        val filename = "${nombreSugerido}.xlsx"
        val cachePath = File(context.cacheDir, "exports")
        if (!cachePath.exists()) cachePath.mkdirs() // Crear carpeta si no existe

        val file = File(cachePath, filename)
        // Si el archivo ya existe en la cache, lo borramos para que el FileProvider no mande basura
        if (file.exists()) file.delete()

        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun compartir(context: Context, uri: Uri) {
        // ShareCompat se encarga de grantUriPermission por vos
        // a través de addStream() y startChooser()
        ShareCompat.IntentBuilder(context)
            .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .setSubject("Reporte de Operaciones")
            .setChooserTitle("Compartir Excel")
            .addStream(uri) // Esto otorga el FLAG_GRANT_READ_URI_PERMISSION automáticamente
            .startChooser()
    }
}