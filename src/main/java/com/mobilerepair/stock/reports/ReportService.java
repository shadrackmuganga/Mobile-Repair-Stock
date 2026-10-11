package com.mobilerepair.stock.reports;

import com.mobilerepair.stock.org.OrgStateService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side port of the browser's report/export logic in app.js
 * ({@code exportInventoryToExcel}, {@code exportInventoryToCsv},
 * {@code downloadReport}, {@code filterTransactions}). Reports are generated
 * over the authoritative {@code orgData/{orgId}} document so the browser no
 * longer needs SheetJS or any report maths.
 */
@Service
public class ReportService {

    private final OrgStateService org;

    public ReportService(OrgStateService org) {
        this.org = org;
    }

    // ---- inventory --------------------------------------------------------

    /** Mirrors {@code getVisibleItems()}: search matches name or id, plus category filter. */
    public List<Map<String, Object>> visibleItems(String orgId, String q, String category) {
        Map<String, Object> state = org.state(orgId);
        String term = q == null ? "" : q.toLowerCase();
        String cat = (category == null || category.isBlank()) ? "all" : category;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : list(state.get("items"))) {
            String name = str(item.get("name")).toLowerCase();
            String id = str(item.get("id")).toLowerCase();
            if (!name.contains(term) && !id.contains(term)) continue;
            if (!"all".equals(cat) && !cat.equals(str(item.get("category")))) continue;
            out.add(item);
        }
        return out;
    }

    public String inventoryCsv(String orgId, String q, String category) {
        StringBuilder sb = new StringBuilder("\ufeff");
        sb.append("Item ID,Name,Category,Unit,Stock,Min Stock,Cost Price,Selling Price,Status");
        for (Map<String, Object> item : visibleItems(orgId, q, category)) {
            sb.append("\r\n").append(String.join(",",
                    csv(item.get("id")),
                    csv(item.get("name")),
                    csv(item.get("category")),
                    csv(item.get("unit")),
                    csv(numText(item.get("stock"))),
                    csv(numText(item.get("minStock"))),
                    csv(numText(item.get("costPrice"))),
                    csv(numText(item.get("sellingPrice"))),
                    csv(isLowStock(item) ? "Low" : "OK")));
        }
        return sb.toString();
    }

    public byte[] inventoryXlsx(String orgId, String q, String category) {
        List<Map<String, Object>> items = visibleItems(orgId, q, category);
        String[] headers = {"Item ID", "Name", "Category", "Unit", "Stock", "Min Stock",
                "Cost Price", "Selling Price", "Status"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inventory");

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Map<String, Object> item : items) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(str(item.get("id")));
                row.createCell(1).setCellValue(str(item.get("name")));
                row.createCell(2).setCellValue(str(item.get("category")));
                row.createCell(3).setCellValue(str(item.get("unit")));
                row.createCell(4).setCellValue(num(item.get("stock")));
                row.createCell(5).setCellValue(num(item.get("minStock")));
                row.createCell(6).setCellValue(num(item.get("costPrice")));
                row.createCell(7).setCellValue(num(item.get("sellingPrice")));
                row.createCell(8).setCellValue(isLowStock(item) ? "Low" : "OK");
            }

            int[] widths = {22, 26, 16, 10, 8, 10, 12, 12, 8};
            for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Excel export", e);
        }
    }

    // ---- transactions -----------------------------------------------------

    public String transactionsCsv(String orgId, String startDate, String endDate, String user, String type) {
        StringBuilder sb = new StringBuilder("\ufeff");
        sb.append("Date,Type,Item,Amount,Customer,Staff,Reason,Revenue,Profit");
        for (Map<String, Object> txn : filterTransactions(orgId, startDate, endDate, user, type)) {
            sb.append("\r\n").append(String.join(",",
                    csv(txn.get("date")),
                    csv(txn.get("type")),
                    csv(txn.get("name")),
                    csv(numText(txn.get("amount"))),
                    csv(txn.get("customer")),
                    csv(txn.get("createdBy")),
                    csv(txn.get("reason")),
                    csv(numText(txn.get("revenue"))),
                    csv(numText(txn.get("profit")))));
        }
        return sb.toString();
    }

    public List<Map<String, Object>> filterTransactions(String orgId, String startDate, String endDate,
                                                        String user, String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> txn : list(org.state(orgId).get("transactions"))) {
            String date = str(txn.get("date"));
            if (startDate != null && !startDate.isBlank() && date != null) {
                try {
                    if (instant(date).isBefore(instant(startDate + "T00:00:00Z"))) continue;
                } catch (Exception ignored) {
                }
            }
            if (endDate != null && !endDate.isBlank() && date != null) {
                try {
                    if (instant(date).isAfter(instant(endDate + "T23:59:59Z"))) continue;
                } catch (Exception ignored) {
                }
            }
            String staff = str(txn.get("createdBy"));
            if (user != null && !user.isBlank() && !"all".equals(user) && !user.equals(staff)) continue;
            if (type != null && !type.isBlank() && !"all".equals(type) && !type.equals(str(txn.get("type")))) continue;
            out.add(txn);
        }
        return out;
    }

    // ---- JSON report ------------------------------------------------------

    public Map<String, Object> reportJson(String orgId, String startDate, String endDate,
                                          String user, String type, String requestedBy) {
        Map<String, Object> state = org.state(orgId);
        List<Map<String, Object>> inventory = list(state.get("items"));
        List<Map<String, Object>> tickets = list(state.get("repairTickets"));
        List<Map<String, Object>> transactions = filterTransactions(orgId, startDate, endDate, user, type);

        double inventoryValue = 0;
        int lowStock = 0;
        for (Map<String, Object> item : inventory) {
            inventoryValue += num(item.get("stock")) * num(item.get("costPrice"));
            if (isLowStock(item)) lowStock++;
        }
        int openTickets = 0;
        for (Map<String, Object> ticket : tickets) {
            if ("Open".equals(str(ticket.get("status")))) openTickets++;
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("startDate", startDate);
        filters.put("endDate", endDate);
        filters.put("selectedUser", (user == null || user.isBlank()) ? "all" : user);
        filters.put("reportType", (type == null || type.isBlank()) ? "all" : type);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalItems", inventory.size());
        summary.put("inventoryValue", number(inventoryValue));
        summary.put("salesRevenue", number(sumByType(transactions, List.of("sale", "phone-sale"), "revenue")));
        summary.put("salesProfit", number(sumByType(transactions, List.of("sale", "phone-sale"), "profit")));
        summary.put("regularSalesRevenue", number(sumByType(transactions, List.of("sale"), "revenue")));
        summary.put("regularSalesProfit", number(sumByType(transactions, List.of("sale"), "profit")));
        summary.put("phoneSalesRevenue", number(sumByType(transactions, List.of("phone-sale"), "revenue")));
        summary.put("phoneSalesProfit", number(sumByType(transactions, List.of("phone-sale"), "profit")));
        summary.put("lowStockItems", lowStock);
        summary.put("openTickets", openTickets);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("user", (requestedBy == null || requestedBy.isBlank()) ? "system" : requestedBy);
        report.put("filters", filters);
        report.put("inventory", inventory);
        report.put("transactions", transactions);
        report.put("repairTickets", tickets);
        report.put("summary", summary);
        return report;
    }

    // ---- helpers ----------------------------------------------------------

    private double sumByType(List<Map<String, Object>> transactions, List<String> types, String field) {
        double total = 0;
        for (Map<String, Object> txn : transactions) {
            if (types.contains(str(txn.get("type")))) total += num(txn.get(field));
        }
        return total;
    }

    private static boolean isLowStock(Map<String, Object> item) {
        return num(item.get("stock")) <= num(item.get("minStock"));
    }

    private static List<Map<String, Object>> list(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> raw) {
            for (Object entry : raw) {
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    out.add(copy);
                }
            }
        }
        return out;
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.indexOf('"') >= 0 || text.indexOf(',') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static String numText(Object value) {
        return fmt(num(value));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double num(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Object number(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value)) {
            return (long) value;
        }
        return value;
    }

    private static String fmt(double value) {
        return String.valueOf(number(value));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }
}
