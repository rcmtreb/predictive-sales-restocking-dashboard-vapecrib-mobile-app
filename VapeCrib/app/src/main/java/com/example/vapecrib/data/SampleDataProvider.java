package com.example.vapecrib.data;

import android.annotation.SuppressLint;
import com.example.vapecrib.model.SalesData;
import com.example.vapecrib.model.InventoryItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("NewApi")
public class SampleDataProvider {
    
    // Sample data from January 2022 to March 2026 (4+ years)
    public static List<SalesData> generateSampleSalesData() {
        List<SalesData> data = new ArrayList<>();
        
        LocalDate startDate = LocalDate.of(2022, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        
        // Generate daily sample data for 4+ years (~1,551 days)
        LocalDate currentDate = startDate;
        int dayCount = 0;
        while (!currentDate.isAfter(endDate)) {
            // Generate realistic sales data with trends and seasonality
            float baseSales = 150f + (dayCount * 0.3f); // Gradual upward trend
            float seasonality = (float) (Math.sin(dayCount / 90.0) * 80); // Quarterly pattern
            float monthlyPattern = (float) (Math.cos(dayCount / 30.0) * 50); // Monthly cyclical pattern
            float randomVariation = (float) (Math.random() * 100 - 50); // Random daily variation
            
            float actualSales = Math.max(50, baseSales + seasonality + monthlyPattern + randomVariation);
            float forecastedSales = Math.max(50, baseSales + seasonality + monthlyPattern); // Slightly smoother
            float revenue = actualSales * 450f; // Average price per unit
            int productsCount = Math.max(10, (int) (actualSales / 10));
            
            data.add(new SalesData(currentDate, actualSales, forecastedSales, revenue, productsCount));
            
            currentDate = currentDate.plusDays(1);
            dayCount++;
        }
        
        return data;
    }
    
    // Get date range of sample data
    public static LocalDate[] getDataDateRange() {
        return new LocalDate[]{
            LocalDate.of(2022, 1, 1),
            LocalDate.of(2026, 3, 31)
        };
    }
    
    // Generate consistent inventory data for all pages
    public static List<InventoryItem> generateSampleInventoryData() {
        List<InventoryItem> inventory = new ArrayList<>();
        
        // Critical stock items (41 items)
        String[] criticalItems = {
            "Vape Pen V1", "E-Liquid Berry", "Coil Pack A", "Battery 18650", "Charger USB-C",
            "Tank Glass", "Drip Tip Metal", "Cotton Organic", "Wire Kanthal", "Tool Kit",
            "Case Leather", "Mod Box V2", "Atomizer RDA", "E-Liquid Mint", "Coil Pack B",
            "Battery 21700", "Charger Dual", "Tank Plastic", "Drip Tip Ceramic", "Cotton Premium",
            "Wire Stainless", "Tool Screwdriver", "Case Silicone", "Mod Tube", "Atomizer RTA",
            "E-Liquid Vanilla", "Coil Pack C", "Battery Wrap", "Charger Car", "Tank Hybrid",
            "Drip Tip Resin", "Cotton Muji", "Wire Nichrome", "Tool Tweezers", "Case Hard",
            "Mod Squonk", "Atomizer RDTA", "E-Liquid Tobacco", "Coil Mesh", "Battery Case", "Ohm Reader"
        };
        
        for (String item : criticalItems) {
            inventory.add(new InventoryItem(
                item, 
                (int)(Math.random() * 10) + 1,  // 1-10 stock
                50, 100, 
                "Vaping", 
                InventoryItem.StockLevel.CRITICAL,
                Math.random() < 0.3  // 30% chance expiring soon
            ));
        }
        
        // High stock items (23 items)
        String[] highItems = {
            "Premium Juice A", "Premium Juice B", "Premium Juice C", "Starter Kit Pro",
            "Advanced Mod X", "Tank Premium", "Coil Premium", "Battery Premium",
            "Charger Fast", "Tool Premium", "Case Premium", "Cotton Premium Plus",
            "Wire Premium", "Atomizer Premium", "Drip Premium", "Mod Elite",
            "Kit Beginner", "Juice Collection", "Battery Set", "Charger Station",
            "Tool Set Pro", "Cotton Bundle", "Wire Set"
        };
        
        for (String item : highItems) {
            inventory.add(new InventoryItem(
                item, 
                (int)(Math.random() * 30) + 11,  // 11-40 stock
                50, 100, 
                "Premium", 
                InventoryItem.StockLevel.HIGH,
                Math.random() < 0.2  // 20% chance expiring soon
            ));
        }
        
        // Medium stock items (18 items)
        String[] mediumItems = {
            "Standard Kit A", "Standard Kit B", "Standard Juice", "Basic Mod",
            "Regular Tank", "Standard Coil", "Basic Battery", "Simple Charger",
            "Basic Tool", "Standard Case", "Regular Cotton", "Basic Wire",
            "Standard Atomizer", "Regular Drip", "Basic Mod V2", "Kit Standard",
            "Juice Regular", "Battery Standard"
        };
        
        for (String item : mediumItems) {
            inventory.add(new InventoryItem(
                item, 
                (int)(Math.random() * 20) + 41,  // 41-60 stock
                50, 100, 
                "Standard", 
                InventoryItem.StockLevel.MEDIUM,
                Math.random() < 0.15  // 15% chance expiring soon
            ));
        }
        
        // Low stock items (7 items) - for reports consistency
        String[] lowItems = {
            "Budget Kit", "Economy Juice", "Basic Starter", "Simple Mod",
            "Entry Tank", "Starter Coil", "Budget Battery"
        };
        
        for (String item : lowItems) {
            inventory.add(new InventoryItem(
                item, 
                (int)(Math.random() * 20) + 61,  // 61-80 stock
                50, 100, 
                "Budget", 
                InventoryItem.StockLevel.LOW,
                Math.random() < 0.1  // 10% chance expiring soon
            ));
        }
        
        return inventory;
    }
    
    // Get inventory counts by stock level
    public static int getCriticalCount() { return 41; }
    public static int getHighCount() { return 23; }
    public static int getMediumCount() { return 18; }
    public static int getLowCount() { return 7; }
    public static int getExpiringCount() { return 15; }  // Items expiring soon
    public static int getActiveAlertsCount() { return 3; }  // Critical alerts
}
