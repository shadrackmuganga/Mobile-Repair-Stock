package com.mobilerepair.stock.i18n;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side copy of the UI translations that used to live in app.js
 * ({@code UI_TEXT}). Served over {@code /api/i18n} so the browser can fetch
 * labels from the backend instead of bundling them.
 */
@Service
public class UiText {

    public static final String DEFAULT_LANGUAGE = "en";

    private final Map<String, Map<String, String>> languages = new LinkedHashMap<>();

    public UiText() {
        languages.put("en", en());
        languages.put("sw", sw());
        languages.put("es", es());
        languages.put("fr", fr());
    }

    public Map<String, Map<String, String>> all() {
        return languages;
    }

    public Map<String, String> forLanguage(String language) {
        return languages.get(language == null ? null : language.toLowerCase());
    }

    public List<String> languages() {
        return List.copyOf(languages.keySet());
    }

    private static Map<String, String> en() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settingsTitle", "Preferences");
        m.put("currencyLabel", "Currency");
        m.put("languageLabel", "Language");
        m.put("saveSettings", "Save Preferences");
        m.put("addItem", "Add Item");
        m.put("stockActions", "Stock Actions");
        m.put("salesRepair", "Sales & Repair");
        m.put("quickStats", "Quick Stats");
        m.put("salesReportFilters", "Sales Report Filters");
        m.put("inventory", "Inventory");
        m.put("transactions", "Recent Transactions");
        m.put("repairTickets", "Repair Tickets");
        return m;
    }

    private static Map<String, String> sw() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settingsTitle", "Mapendeleo");
        m.put("currencyLabel", "Fedha");
        m.put("languageLabel", "Lugha");
        m.put("saveSettings", "Hifadhi Mapendeleo");
        m.put("addItem", "Ongeza Kitu");
        m.put("stockActions", "Vitendo vya Hisa");
        m.put("salesRepair", "Mauzo na Matengenezo");
        m.put("quickStats", "Takwimu za Haraka");
        m.put("salesReportFilters", "Vichujio vya Ripoti ya Mauzo");
        m.put("inventory", "Hisa");
        m.put("transactions", "Miamala ya Hivi Karibuni");
        m.put("repairTickets", "Tiketi za Matengenezo");
        return m;
    }

    private static Map<String, String> es() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settingsTitle", "Preferencias");
        m.put("currencyLabel", "Moneda");
        m.put("languageLabel", "Idioma");
        m.put("saveSettings", "Guardar Preferencias");
        m.put("addItem", "Agregar Artículo");
        m.put("stockActions", "Acciones de Stock");
        m.put("salesRepair", "Ventas y Reparaciones");
        m.put("quickStats", "Estadísticas Rápidas");
        m.put("salesReportFilters", "Filtros del Informe de Ventas");
        m.put("inventory", "Inventario");
        m.put("transactions", "Transacciones Recientes");
        m.put("repairTickets", "Tickets de Reparación");
        return m;
    }

    private static Map<String, String> fr() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settingsTitle", "Préférences");
        m.put("currencyLabel", "Devise");
        m.put("languageLabel", "Langue");
        m.put("saveSettings", "Enregistrer les préférences");
        m.put("addItem", "Ajouter un article");
        m.put("stockActions", "Actions de stock");
        m.put("salesRepair", "Ventes et réparations");
        m.put("quickStats", "Statistiques rapides");
        m.put("salesReportFilters", "Filtres du rapport de vente");
        m.put("inventory", "Inventaire");
        m.put("transactions", "Transactions récentes");
        m.put("repairTickets", "Tickets de réparation");
        return m;
    }
}
