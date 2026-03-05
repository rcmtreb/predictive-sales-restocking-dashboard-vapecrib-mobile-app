package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Paged wrapper for GET /api/mobile/products.
 * Flask _ok() nests pagination under a "pagination" key.
 */
public class PagedProductsResponse {

    @SerializedName("data")
    private List<ProductApiRecord> data;

    @SerializedName("pagination")
    private Pagination pagination;

    public List<ProductApiRecord> getData() { return data; }
    public Pagination getPagination()       { return pagination; }

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
