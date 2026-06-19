package com.clinic.backend.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Génère un classeur Excel (.xlsx) à partir d'en-têtes + lignes de valeurs (P2.3).
 * Largeurs de colonnes fixes (pas d'{@code autoSizeColumn} pour éviter les
 * dépendances AWT en environnement headless).
 */
@Service
public class ExcelExportService {

    public byte[] toXlsx(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName);

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            int r = 1;
            for (List<Object> row : rows) {
                Row dataRow = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Cell c = dataRow.createCell(i);
                    Object v = row.get(i);
                    if (v == null) {
                        c.setCellValue("");
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du fichier Excel : " + e.getMessage(), e);
        }
    }
}
