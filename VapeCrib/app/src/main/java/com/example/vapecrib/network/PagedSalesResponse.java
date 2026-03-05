package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Paged wrapper for GET /api/mobile/sales.
 * Flask _ok() nests pagination under a "pagination" key:
 * { "success": true, "data": [...], "pagination": { "page":1, "pages":5, "total":100, ... } }
 */
public class PagedSalesResponse {

    @SerializedName("data")
    private List<SaleApiRecord> data;

    @SerializedName("pagination")
    private Pagination pagination;

    public List<SaleApiRecord> getData() { return data; }
    public Pagination getPagination()    { return pagination; }

    /** Convenience passthrough so callers don't need to null-check pagination. */
    public int getPages() { return pagination != null ? pagination.pages : 0; }
    public int getPage()  { return pagination != null ? pagination.page  : 1; }
    public int getTotal() { return pagination != null ? pagination.total : 0; }

    public static class Pagination {
        @SerializedName("page")     public int     page;
        @SerializedName("pages")    public int     pages;
        @SerializedName("total")    public int     total;
        @SerializedName("limit")    public int     limit;
        @SerializedName("has_next") public boolean hasNext;
        @SerializedName("has_prev") public boolean hasPrev;
    }
}
