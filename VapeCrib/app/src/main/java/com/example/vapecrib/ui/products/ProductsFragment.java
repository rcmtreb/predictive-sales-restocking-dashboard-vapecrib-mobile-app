package com.example.vapecrib.ui.products;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;
import com.example.vapecrib.R;

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
        binding.actvStockFilter.setSaveEnabled(false);
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

        // FAB — open "Add New Product" bottom sheet
        binding.fabAddProduct.setOnClickListener(v -> showAddProductSheet());

        return binding.getRoot();
    }

    // ── Add Product bottom sheet ──────────────────────────────────────────────

    private void showAddProductSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_product, null);
        sheet.setContentView(sheetView);

        TextInputLayout  tilName    = sheetView.findViewById(R.id.til_product_name);
        TextInputEditText etName   = sheetView.findViewById(R.id.et_product_name);
        TextInputEditText etCat    = sheetView.findViewById(R.id.et_category);
        TextInputEditText etCost   = sheetView.findViewById(R.id.et_unit_cost);
        TextInputEditText etStock  = sheetView.findViewById(R.id.et_current_stock);
        MaterialButton    btnSave  = sheetView.findViewById(R.id.btn_save_product);
        MaterialButton    btnCancel = sheetView.findViewById(R.id.btn_cancel);

        btnCancel.setOnClickListener(v -> sheet.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                tilName.setError("Product name is required");
                return;
            }
            tilName.setError(null);

            String category = etCat.getText() != null ? etCat.getText().toString().trim() : "";
            float unitCost = 0f;
            int stock = 0;
            try {
                String costStr = etCost.getText() != null ? etCost.getText().toString().trim() : "";
                if (!costStr.isEmpty()) unitCost = Float.parseFloat(costStr);
            } catch (NumberFormatException ignored) {}
            try {
                String stockStr = etStock.getText() != null ? etStock.getText().toString().trim() : "";
                if (!stockStr.isEmpty()) stock = Integer.parseInt(stockStr);
            } catch (NumberFormatException ignored) {}

            btnSave.setEnabled(false);
            btnSave.setText("Saving…");

            float finalUnitCost = unitCost;
            int finalStock = stock;
            productsViewModel.createProduct(name, category, finalUnitCost, finalStock,
                    new ProductsViewModel.CreateProductCallback() {
                        @Override
                        public void onSuccess(String productName) {
                            sheet.dismiss();
                            if (binding != null) {
                                Snackbar.make(binding.getRoot(),
                                        "'" + productName + "' added successfully",
                                        Snackbar.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (btnSave != null) {
                                btnSave.setEnabled(true);
                                btnSave.setText("Save Product");
                            }
                            if (binding != null) {
                                Snackbar.make(binding.getRoot(), message,
                                        Snackbar.LENGTH_LONG).show();
                            }
                        }
                    });
        });

        sheet.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

