package com.example.vapecrib.ui.products;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vapecrib.databinding.FragmentProductsBinding;

public class ProductsFragment extends Fragment {

    private ProductsViewModel productsViewModel;
    private FragmentProductsBinding binding;
    private InventoryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        productsViewModel = new ViewModelProvider(this).get(ProductsViewModel.class);
        binding = FragmentProductsBinding.inflate(inflater, container, false);

        // RecyclerView setup
        adapter = new InventoryAdapter();
        binding.rvInventory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvInventory.setAdapter(adapter);

        // Stock filter dropdown — start empty (shows all), selection-only
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                ProductsViewModel.STOCK_FILTER_OPTIONS);
        binding.actvStockFilter.setAdapter(filterAdapter);
        // No pre-selection — empty = show all
        binding.actvStockFilter.setOnItemClickListener((parent, view, position, id) ->
                productsViewModel.setStockLevelFilter(
                        ProductsViewModel.STOCK_FILTER_OPTIONS[position]));

        // Search bar
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                productsViewModel.setSearchQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Observe filtered list
        productsViewModel.getFilteredInventory().observe(getViewLifecycleOwner(),
                items -> adapter.updateData(items));

        // Observe item count label
        productsViewModel.getItemCountText().observe(getViewLifecycleOwner(),
                binding.tvItemsCount::setText);

        // Pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            binding.etSearch.setText("");
            binding.actvStockFilter.setText("", false);
            productsViewModel.setSearchQuery("");
            productsViewModel.setStockLevelFilter("All");
            productsViewModel.refreshData();
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

