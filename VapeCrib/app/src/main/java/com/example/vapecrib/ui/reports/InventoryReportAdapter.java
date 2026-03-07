package com.example.vapecrib.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vapecrib.R;
import com.example.vapecrib.network.InventoryReportResponse;

import java.util.ArrayList;
import java.util.List;

public class InventoryReportAdapter extends RecyclerView.Adapter<InventoryReportAdapter.VH> {

    private List<InventoryReportResponse.InventoryItem> items = new ArrayList<>();

    public void setItems(List<InventoryReportResponse.InventoryItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        InventoryReportResponse.InventoryItem item = items.get(pos);
        h.product.setText(item.productName);
        h.category.setText(item.category != null ? item.category : "");
        h.stock.setText("Stock: " + item.currentStock);
        h.cost.setText(String.format("Cost: P%,.2f", item.unitCost));
        h.value.setText(String.format("P%,.2f", item.totalValue));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView product, category, stock, cost, value;
        VH(View v) {
            super(v);
            product  = v.findViewById(R.id.tv_inv_product);
            category = v.findViewById(R.id.tv_inv_category);
            stock    = v.findViewById(R.id.tv_inv_stock);
            cost     = v.findViewById(R.id.tv_inv_cost);
            value    = v.findViewById(R.id.tv_inv_value);
        }
    }
}
