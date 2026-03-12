package com.example.vapecrib.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vapecrib.R;
import com.example.vapecrib.network.SalesReportResponse;

import java.util.ArrayList;
import java.util.List;

public class SalesReportAdapter extends RecyclerView.Adapter<SalesReportAdapter.VH> {

    private List<SalesReportResponse.SaleItem> items = new ArrayList<>();

    public void setItems(List<SalesReportResponse.SaleItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sales_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SalesReportResponse.SaleItem item = items.get(pos);
        h.product.setText(item.productName);
        h.date.setText(item.date);
        h.qty.setText("Qty: " + item.quantity);
        h.price.setText(String.format("Price: P%,.2f", item.price));
        h.revenue.setText(String.format("P%,.2f", item.revenue));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView product, date, qty, price, revenue;
        VH(View v) {
            super(v);
            product = v.findViewById(R.id.tv_sale_product);
            date    = v.findViewById(R.id.tv_sale_date);
            qty     = v.findViewById(R.id.tv_sale_qty);
            price   = v.findViewById(R.id.tv_sale_price);
            revenue = v.findViewById(R.id.tv_sale_revenue);
        }
    }
}
