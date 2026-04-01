package com.oms.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class ExcelExportUtil {

    public static <T> byte[] exportToExcel(List<T> data, String[] headers, String[] fields) throws IOException {
        return exportToExcel(data, headers, fields, null);
    }

    public static <T> byte[] exportToExcel(List<T> data, String[] headers, String[] fields, Map<Integer, String[]> dropdowns) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            // 文本格式样式 (用于条形码等长数字列)
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            textStyle.setDataFormat(format.getFormat("@"));

            // 创建表头
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            int rowIdx = 1;
            for (T item : data) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < fields.length; i++) {
                    Cell cell = row.createCell(i);
                    // 默认给条形码(第2列)和编码(第1列)设置文本格式
                    if (i == 1 || i == 2) {
                        cell.setCellStyle(textStyle);
                    }
                    try {
                        Field field = getField(item.getClass(), fields[i]);
                        field.setAccessible(true);
                        Object value = field.get(item);
                        if (value != null) {
                            cell.setCellValue(value.toString());
                        }
                    } catch (Exception e) {
                        cell.setCellValue("");
                    }
                }
            }

            // 添加下拉列表
            if (dropdowns != null && !dropdowns.isEmpty()) {
                DataValidationHelper validationHelper = new XSSFDataValidationHelper((XSSFSheet) sheet);
                for (Map.Entry<Integer, String[]> entry : dropdowns.entrySet()) {
                    int colIdx = entry.getKey();
                    String[] options = entry.getValue();
                    CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, colIdx, colIdx);
                    DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(options);
                    DataValidation validation = validationHelper.createValidation(constraint, addressList);
                    validation.setShowErrorBox(true);
                    sheet.addValidationData(validation);
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // 在自适应基础上再加宽一点，避免太挤
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, currentWidth + 1024);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}
