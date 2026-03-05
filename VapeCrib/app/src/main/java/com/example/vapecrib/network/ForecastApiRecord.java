package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/** Mirrors one row from GET /api/mobile/forecast (Forecast.to_dict()). */
public class ForecastApiRecord {

    @SerializedName("id")
    public int id;

    @SerializedName("product_id")
    public int productId;

    /**
     * ISO datetime string e.g. "2025-11-02T00:00:00" — Flask stores this as DateTime.
     * We parse only the date part (first 10 chars).
     */
    @SerializedName("forecast_date")
    public String forecastDate;

    /** Same as forecast_date but as a plain date string e.g. "2025-11-02" */
    @SerializedName("period_key")
    public String periodKey;

    @SerializedName("predicted_quantity")
    public float predictedQuantity;

    /** 0.0–100.0 percent accuracy for this forecast */
    @SerializedName("accuracy")
    public float accuracy;

    @SerializedName("aggregation_level")
    public String aggregationLevel;
}
