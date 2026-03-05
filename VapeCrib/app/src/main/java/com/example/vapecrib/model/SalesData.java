package com.example.vapecrib.model;

import java.time.LocalDate;

public class SalesData {
    private LocalDate date;
    private float actualSales;
    private float forecastedSales;
    private float revenue;
    private int productsCount;

    public SalesData(LocalDate date, float actualSales, float forecastedSales, float revenue, int productsCount) {
        this.date = date;
        this.actualSales = actualSales;
        this.forecastedSales = forecastedSales;
        this.revenue = revenue;
        this.productsCount = productsCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public float getActualSales() {
        return actualSales;
    }

    public float getForecastedSales() {
        return forecastedSales;
    }

    public float getRevenue() {
        return revenue;
    }

    public int getProductsCount() {
        return productsCount;
    }
}
