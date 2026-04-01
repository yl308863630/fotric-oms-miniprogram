package com.oms.service.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Tabula 从 PDF 提取表格数据，适用于有文本层的电子 PDF。
 * 扫描版 PDF 无文本层时效果有限。
 */
public class TabulaTableExtractor {

    /**
     * 从 PDF 提取表格行，每行为一组单元格文本。
     *
     * @param doc   PDF 文档
     * @param pages 页号，1-based，null 或空表示全部
     * @return 所有页的表格行，每行为一个字符串列表
     */
    public static List<List<String>> extractTableRows(PDDocument doc, int[] pages) {
        List<List<String>> allRows = new ArrayList<>();
        if (doc == null) return allRows;
        try (ObjectExtractor oe = new ObjectExtractor(doc)) {
            BasicExtractionAlgorithm basic = new BasicExtractionAlgorithm();
            SpreadsheetExtractionAlgorithm spreadsheet = new SpreadsheetExtractionAlgorithm();
            PageIterator pi = oe.extract();
            int pageNum = 0;
            while (pi.hasNext()) {
                pageNum++;
                Page page = pi.next();
                if (pages != null && pages.length > 0 && !containsPage(pages, pageNum)) continue;
                List<Table> tables = spreadsheet.extract(page);
                if (tables.isEmpty()) {
                    tables = basic.extract(page);
                }
                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (RectangularTextContainer cell : row) {
                            String text = cell.getText().replace("\r", " ").trim();
                            cells.add(text);
                        }
                        if (!cells.isEmpty() && cells.stream().anyMatch(s -> !s.isBlank())) {
                            allRows.add(cells);
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // Tabula 提取失败时静默返回空
        }
        return allRows;
    }

    private static boolean containsPage(int[] pages, int pageNum) {
        for (int p : pages) {
            if (p == pageNum) return true;
        }
        return false;
    }
}
