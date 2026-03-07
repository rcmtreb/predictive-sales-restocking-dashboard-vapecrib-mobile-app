package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ImportHistoryResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("imports")
        public List<ImportItem> imports;

        @SerializedName("total")
        public int total;
    }

    public static class ImportItem {
        @SerializedName("id")
        public int id;

        @SerializedName("filename")
        public String filename;

        @SerializedName("upload_date")
        public String uploadDate;

        @SerializedName("rows_processed")
        public int rowsProcessed;

        @SerializedName("rows_failed")
        public int rowsFailed;

        @SerializedName("rows_skipped")
        public int rowsSkipped;

        @SerializedName("status")
        public String status;

        @SerializedName("data_type")
        public String dataType;

        @SerializedName("username")
        public String username;
    }
}
