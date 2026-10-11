package com.mobilerepair.stock.inventory;

import com.mobilerepair.stock.model.StockItem;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final InventoryService inventory;

    public ItemController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<StockItem> list(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.list(orgId, q, category);
    }

    @GetMapping("/{id}")
    public StockItem get(@PathVariable String id,
                         @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.get(orgId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockItem create(@RequestBody StockItem item,
                            @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.create(orgId, item);
    }

    @PutMapping("/{id}")
    public StockItem update(@PathVariable String id,
                            @RequestBody StockItem item,
                            @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.update(orgId, id, item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id,
                       @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        inventory.delete(orgId, id);
    }

    @PostMapping("/{id}/stock-in")
    public StockItem stockIn(@PathVariable String id,
                             @RequestBody StockChange change,
                             @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.adjustStock(orgId, id, Math.abs(change.amount()), "in", change.reason());
    }

    @PostMapping("/{id}/stock-out")
    public StockItem stockOut(@PathVariable String id,
                              @RequestBody StockChange change,
                              @RequestHeader(value = "X-Org-Id", defaultValue = "demo") String orgId) {
        return inventory.adjustStock(orgId, id, -Math.abs(change.amount()), "out", change.reason());
    }

    public record StockChange(int amount, String reason) {
    }
}
