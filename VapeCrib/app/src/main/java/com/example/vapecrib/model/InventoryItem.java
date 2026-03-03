package com.example.vapecrib.model;

public class InventoryItem {
    private String productName;
    private int currentStock;
    private int minStockLevel;
    private int maxStockLevel;
    private String category;
    private StockLevel stockLevel;
    private boolean isExpiringSoon;

    public enum StockLevel {
        CRITICAL("Critical"), HIGH("High"), MEDIUM("Medium"), LOW("Low");
        
        private String displayName;
        
        StockLevel(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    public InventoryItem(String productName, int currentStock, int minStockLevel, int maxStockLevel, 
                        String category, StockLevel stockLevel, boolean isExpiringSoon) {
        this.productName = productName;
        this.currentStock = currentStock;
        this.minStockLevel = minStockLevel;
        this.maxStockLevel = maxStockLevel;
        this.category = category;
        this.stockLevel = stockLevel;
        this.isExpiringSoon = isExpiringSoon;
    }

    // Getters
    public String getProductName() { return productName; }
    public int getCurrentStock() { return currentStock; }
    public int getMinStockLevel() { return minStockLevel; }
    public int getMaxStockLevel() { return maxStockLevel; }
    public String getCategory() { return category; }
    public StockLevel getStockLevel() { return stockLevel; }
    public boolean isExpiringSoon() { return isExpiringSoon; }
    
    // Setters
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public void setStockLevel(StockLevel stockLevel) { this.stockLevel = stockLevel; }
    public void setExpiringSoon(boolean expiringSoon) { isExpiringSoon = expiringSoon; }
}