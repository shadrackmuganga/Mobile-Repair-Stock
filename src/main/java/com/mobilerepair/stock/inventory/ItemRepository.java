package com.mobilerepair.stock.inventory;

import com.mobilerepair.stock.model.StockItem;

import java.util.List;
import java.util.Optional;

public interface ItemRepository {

    List<StockItem> findByOrg(String orgId);

    Optional<StockItem> find(String orgId, String itemId);

    StockItem save(String orgId, StockItem item);

    void delete(String orgId, String itemId);
}
