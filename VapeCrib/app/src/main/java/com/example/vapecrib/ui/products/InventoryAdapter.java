package com.example.vapecrib.ui.products;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vapecrib.R;
import com.example.vapecrib.databinding.ItemInventoryBinding;
import com.example.vapecrib.model.InventoryItem;

import java.util.ArrayList;
import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    private List<InventoryItem> items = new ArrayList<>();

    public void updateData(List<InventoryItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInventoryBinding binding = ItemInventoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemInventoryBinding binding;

        ViewHolder(ItemInventoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(InventoryItem item) {
            binding.tvProductName.setText(item.getProductName());
            String categoryText = item.getCategory()
                    + (item.isExpiringSoon() ? "  ⚠ Expiring" : "");
            binding.tvCategory.setText(categoryText);
            binding.tvStockCount.setText(String.valueOf(item.getCurrentStock()));

            switch (item.getStockLevel()) {
                case CRITICAL:
                    binding.tvStockBadge.setText("CRITICAL");
                    binding.tvStockBadge.setBackgroundResource(R.drawable.critical_background);
                    binding.viewLevelIndicator.setBackgroundColor(
                            android.graphics.Color.parseColor("#FF0000"));
                    break;
                case HIGH:
                    binding.tvStockBadge.setText("HIGH");
                    binding.tvStockBadge.setBackgroundResource(R.drawable.high_background);
                    binding.viewLevelIndicator.setBackgroundColor(
                            android.graphics.Color.parseColor("#FFA500"));
                    break;
                case MEDIUM:
                    binding.tvStockBadge.setText("MEDIUM");
                    binding.tvStockBadge.setBackgroundResource(R.drawable.medium_background);
                    binding.viewLevelIndicator.setBackgroundColor(
                            android.graphics.Color.parseColor("#008000"));
                    break;
                case LOW:
                default:
                    binding.tvStockBadge.setText("LOW");
                    binding.tvStockBadge.setBackgroundResource(R.drawable.medium_background);
                    binding.viewLevelIndicator.setBackgroundColor(
                            android.graphics.Color.parseColor("#2196F3"));
                    break;
            }
        }
    }
}
