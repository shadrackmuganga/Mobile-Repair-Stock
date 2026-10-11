package com.mobilerepair.stock.reports;

import com.mobilerepair.stock.org.OrgStateService;
import com.mobilerepair.stock.realtime.DocStreamService;
import com.mobilerepair.stock.store.InMemoryDocStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private static final String ORG = "report-org";

    private final OrgStateService org = new OrgStateService(new InMemoryDocStore(), new DocStreamService());
    private final ReportService reports = new ReportService(org);

    @Test
    void inventoryCsvHasBomHeaderAndRows() {
        String csv = reports.inventoryCsv(ORG, "", "all");
        assertTrue(csv.startsWith("\ufeff"));
        assertTrue(csv.contains("Item ID,Name,Category,Unit,Stock,Min Stock,Cost Price,Selling Price,Status"));
        assertTrue(csv.contains("lcd-iphone-12,iPhone 12 LCD,Display,piece,8,5,35,60,OK"));
        long rows = csv.split("\r\n", -1).length;
        assertEquals(4, rows);
    }

    @Test
    void inventoryCsvFiltersBySearchAndCategory() {
        assertEquals(1, reports.visibleItems(ORG, "samsung", "all").size());
        assertEquals(1, reports.visibleItems(ORG, "", "Battery").size());
        assertEquals(0, reports.visibleItems(ORG, "samsung", "Display").size());
        assertEquals(1, reports.visibleItems(ORG, "lcd", "all").size());
    }

    @Test
    void lowStockItemIsFlagged() {
        String csv = reports.inventoryCsv(ORG, "battery", "all");
        assertTrue(csv.contains("battery-samsung-a52,Samsung A52 Battery,Battery,piece,3,4,18,32,Low"));
    }

    @Test
    void transactionsCsvReflectsSalesAndTypeFilter() {
        Map<String, Object> sale = new LinkedHashMap<>();
        sale.put("amount", 2);
        sale.put("customer", "Walk-in, Ltd");
        org.sale(ORG, "lcd-iphone-12", 2, "Walk-in, Ltd", "Sold to customer", "admin");

        String all = reports.transactionsCsv(ORG, null, null, "all", "all");
        assertTrue(all.contains("sale,iPhone 12 LCD,2,\"Walk-in, Ltd\",admin,Sold to customer,120,50"));

        String none = reports.transactionsCsv(ORG, null, null, "all", "phone-sale");
        assertEquals(1, none.split("\r\n", -1).length);
    }

    @Test
    void reportJsonSummarisesInventoryAndSales() {
        org.sale(ORG, "lcd-iphone-12", 2, "Walk-in", "Sold", "admin");
        Map<String, Object> report = reports.reportJson(ORG, null, null, "all", "all", "admin");

        assertNotNull(report.get("generatedAt"));
        assertEquals("admin", report.get("user"));
        assertEquals(3, ((List<?>) report.get("inventory")).size());

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals(3, ((Number) summary.get("totalItems")).intValue());
        assertEquals(120, ((Number) summary.get("salesRevenue")).intValue());
        assertEquals(50, ((Number) summary.get("salesProfit")).intValue());
        assertEquals(1, ((Number) summary.get("lowStockItems")).intValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) report.get("filters");
        assertEquals("all", filters.get("selectedUser"));
        assertEquals("all", filters.get("reportType"));
    }

    @Test
    void excelExportProducesXlsxZip() throws Exception {
        byte[] xlsx = reports.inventoryXlsx(ORG, "", "all");
        assertTrue(xlsx.length > 0);
        // .xlsx files are zip archives, so they start with the PK magic bytes.
        assertEquals('P', xlsx[0]);
        assertEquals('K', xlsx[1]);
    }
}

