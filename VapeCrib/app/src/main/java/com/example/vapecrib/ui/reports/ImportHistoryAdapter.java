package com.example.vapecrib.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vapecrib.R;
import com.example.vapecrib.network.ImportHistoryResponse;

import java.util.ArrayList;
import java.util.List;

public class ImportHistoryAdapter extends RecyclerView.Adapter<ImportHistoryAdapter.VH> {

    private List<ImportHistoryResponse.ImportItem> items = new ArrayList<>();

    public void setItems(List<ImportHistoryResponse.ImportItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_import_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ImportHistoryResponse.ImportItem item = items.get(pos);
        h.filename.setText(item.filename);
        h.status.setText(item.status != null ? item.status : "");
        h.date.setText(item.uploadDate);
        h.type.setText(item.dataType != null ? "Type: " + item.dataType : "");
        h.rows.setText(item.rowsProcessed + " rows");
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView filename, status, date, type, rows;
        VH(View v) {
            super(v);
            filename = v.findViewById(R.id.tv_import_filename);
            status   = v.findViewById(R.id.tv_import_status);
            date     = v.findViewById(R.id.tv_import_date);
            type     = v.findViewById(R.id.tv_import_type);
            rows     = v.findViewById(R.id.tv_import_rows);
        }
    }
}
