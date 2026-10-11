package com.mobilerepair.stock.i18n;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Serves the UI translations that used to be bundled in app.js. When the Java
 * backend is active the browser fetches its label set from here.
 */
@RestController
@RequestMapping("/api/i18n")
public class I18nController {

    private final UiText uiText;

    public I18nController(UiText uiText) {
        this.uiText = uiText;
    }

    @GetMapping(produces = "application/json")
    public Map<String, Map<String, String>> all() {
        return uiText.all();
    }

    @GetMapping(value = "/{language}", produces = "application/json")
    public Map<String, String> one(@PathVariable String language) {
        Map<String, String> strings = uiText.forLanguage(language);
        if (strings == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported language: " + language);
        }
        return strings;
    }
}
