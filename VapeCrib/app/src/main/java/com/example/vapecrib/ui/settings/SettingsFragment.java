package com.example.vapecrib.ui.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.R;
import com.example.vapecrib.data.CSVExporter;
import com.example.vapecrib.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private SettingsViewModel settingsViewModel;
    private FragmentSettingsBinding binding;
    private static final int PICK_CSV_REQUEST = 1001;
    private String importType = ""; // "sales" or "inventory"

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textSettings;
        settingsViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        setupCSVButtons();
        setupNavigationButtons();

        // Pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            com.example.vapecrib.data.db.AppDatabase.reloadFromCSV(requireContext());
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void setupNavigationButtons() {
        // Web Dashboard
        binding.btnWebDashboard.setOnClickListener(v -> openWebDashboard());

        // About
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());

        // Terms & Conditions
        binding.btnTerms.setOnClickListener(v -> openWebPage("https://vapecrib.com/terms"));

        // Privacy Policy
        binding.btnPrivacy.setOnClickListener(v -> openWebPage("https://vapecrib.com/privacy"));

        // Help & FAQ
        binding.btnHelp.setOnClickListener(v -> openWebPage("https://vapecrib.com/help"));
    }

    private void openWebDashboard() {
        // Point to your web-based dashboard
        String webDashboardUrl = "https://vapecrib.onrender.com";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(webDashboardUrl));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open web dashboard. Please check your internet connection.", Toast.LENGTH_LONG).show();
        }
    }

    private void openWebPage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open page. Please check your internet connection.", Toast.LENGTH_LONG).show();
        }
    }

    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("About VapeCrib")
            .setMessage("VapeCrib Sales & Restocking Dashboard\n\n" +
                "Version: 1.0.0\n\n" +
                "A comprehensive mobile application for managing sales forecasts, " +
                "inventory levels, and business intelligence for vape retailers.\n\n" +
                "© 2026 VapeCrib. All rights reserved.")
            .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
            .setNeutralButton("Visit Website", (dialog, which) -> 
                openWebPage("https://vapecrib.com"))
            .show();
    }

    @SuppressLint("NewApi")
    private void setupCSVButtons() {
        // Reload from CSV button
        binding.btnReloadCsv.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Reloading data from CSV...", Toast.LENGTH_SHORT).show();
            com.example.vapecrib.data.db.AppDatabase.reloadFromCSV(requireContext());
            Toast.makeText(requireContext(), "Data refreshed!", Toast.LENGTH_SHORT).show();
        });

        // Export Sales CSV
        binding.btnExportSales.setOnClickListener(v -> exportSalesCSV());

        // Export Inventory CSV
        binding.btnExportInventory.setOnClickListener(v -> exportInventoryCSV());

        // Import Sales CSV
        binding.btnImportSales.setOnClickListener(v -> {
            importType = "sales";
            pickCSVFile();
        });

        // Import Inventory CSV
        binding.btnImportInventory.setOnClickListener(v -> {
            importType = "inventory";
            pickCSVFile();
        });
    }

    private void exportSalesCSV() {
        Thread thread = new Thread(() -> {
            try {
                String filePath = CSVExporter.exportSalesCSV(requireContext());
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Sales data exported to:\n" + filePath, 
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Export failed: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show());
            }
        });
        thread.start();
    }

    private void exportInventoryCSV() {
        Thread thread = new Thread(() -> {
            try {
                String filePath = CSVExporter.exportInventoryCSV(requireContext());
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Inventory data exported to:\n" + filePath, 
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Export failed: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show());
            }
        });
        thread.start();
    }

    private void pickCSVFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/plain"});
        startActivityForResult(intent, PICK_CSV_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_REQUEST && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importCSVFromUri(uri);
            }
        }
    }

    private void importCSVFromUri(Uri uri) {
        Thread thread = new Thread(() -> {
            try {
                String filePath = getRealPathFromURI(uri);
                if (filePath == null) {
                    requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), 
                            "Could not access file", 
                            Toast.LENGTH_SHORT).show());
                    return;
                }

                int count = 0;
                if ("sales".equals(importType)) {
                    count = CSVExporter.importSalesCSV(requireContext(), new java.io.File(filePath));
                } else if ("inventory".equals(importType)) {
                    count = CSVExporter.importInventoryCSV(requireContext(), new java.io.File(filePath));
                }

                final int finalCount = count;
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Imported " + finalCount + " rows successfully", 
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), 
                        "Import failed: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show());
            }
        });
        thread.start();
    }

    private String getRealPathFromURI(Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            java.io.File tempFile = new java.io.File(requireContext().getCacheDir(), "temp_import.csv");
            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
                 java.io.BufferedInputStream bis = new java.io.BufferedInputStream(inputStream)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
