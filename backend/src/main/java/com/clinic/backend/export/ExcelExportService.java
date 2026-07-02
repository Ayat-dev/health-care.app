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

    /** Une section d'un classeur multi-blocs (titre + en-têtes + lignes). */
    public record Section(String title, List<String> headers, List<List<Object>> rows) {}

    /**
     * Écrit plusieurs sections dans une seule feuille, séparées par une ligne vide :
     * un titre en gras, une ligne d'en-têtes, puis les lignes. Utilisé pour les
     * rapports (KPIs + répartitions) afin que l'Excel contienne toute l'information,
     * pas un seul tableau.
     */
    public byte[] toSectionsXlsx(String sheetName, List<Section> sections) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName);
            for (int i = 0; i < 6; i++) sheet.setColumnWidth(i, 7000);

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int r = 0;
            for (Section section : sections) {
                if (section == null || section.rows() == null) continue;
                Row titleRow = sheet.createRow(r++);
                Cell tc = titleRow.createCell(0);
                tc.setCellValue(section.title());
                tc.setCellStyle(titleStyle);

                Row headerRow = sheet.createRow(r++);
                for (int c = 0; c < section.headers().size(); c++) {
                    Cell hc = headerRow.createCell(c);
                    hc.setCellValue(section.headers().get(c));
                    hc.setCellStyle(headerStyle);
                }
                for (List<Object> row : section.rows()) {
                    Row dataRow = sheet.createRow(r++);
                    writeCells(dataRow, row);
                }
                r++; // ligne vide entre sections
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du fichier Excel : " + e.getMessage(), e);
        }
    }

    private static void writeCells(Row dataRow, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Cell c = dataRow.createCell(i);
            Object v = values.get(i);
            if (v == null) c.setCellValue("");
            else if (v instanceof Number n) c.setCellValue(n.doubleValue());
            else c.setCellValue(v.toString());
        }
    }

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
