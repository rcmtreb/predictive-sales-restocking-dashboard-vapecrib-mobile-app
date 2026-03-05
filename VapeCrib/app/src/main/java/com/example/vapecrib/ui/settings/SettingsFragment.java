package com.example.vapecrib.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.R;
import com.example.vapecrib.data.CSVExporter;
import com.example.vapecrib.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME        = "vapecrib_settings";
    public static final String KEY_SHOW_ACTUAL   = "show_actual_line";
    public static final String KEY_SHOW_FORECAST = "show_forecast_line";
    public static final String KEY_CHART_ANIM    = "chart_animation";
    public static final String KEY_SHOW_KPI      = "show_kpi_cards";
    public static final String KEY_SHOW_ALERTS   = "show_alert_badges";
    public static final String KEY_LOW_STOCK_N   = "low_stock_notif";
    public static final String KEY_EXPIRY_N      = "expiry_notif";

    private SettingsViewModel settingsViewModel;
    private FragmentSettingsBinding binding;
    private SharedPreferences prefs;

    private static final int PICK_CSV_REQUEST = 1001;
    private String importType = ""; // "sales" or "inventory"

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Remove the stale "This is settings fragment" observer — title is static in XML
        settingsViewModel.getText().observe(getViewLifecycleOwner(), s -> { /* no-op */ });

        loadSwitchStates();
        setupSwitchListeners();
        setupCSVButtons();
        setupNavigationButtons();

        // Pull-to-refresh — reload CSV data
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            com.example.vapecrib.data.db.AppDatabase.reloadFromCSV(requireContext());
            binding.swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(requireContext(), "Data reloaded from CSV", Toast.LENGTH_SHORT).show();
        });

        return root;
    }

    // ── Switches ────────────────────────────────────────────────────────────

    private void loadSwitchStates() {
        binding.switchShowActual.setChecked(prefs.getBoolean(KEY_SHOW_ACTUAL,   true));
        binding.switchShowForecast.setChecked(prefs.getBoolean(KEY_SHOW_FORECAST, true));
        binding.switchChartAnimation.setChecked(prefs.getBoolean(KEY_CHART_ANIM,  true));
        binding.switchShowKpi.setChecked(prefs.getBoolean(KEY_SHOW_KPI,          true));
        binding.switchShowAlerts.setChecked(prefs.getBoolean(KEY_SHOW_ALERTS,    true));
        binding.switchLowStockNotif.setChecked(prefs.getBoolean(KEY_LOW_STOCK_N, true));
        binding.switchExpiryNotif.setChecked(prefs.getBoolean(KEY_EXPIRY_N,      true));
    }

    private void setupSwitchListeners() {
        binding.switchShowActual.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_ACTUAL, checked).apply();
            Toast.makeText(requireContext(),
                "Actual sales line " + (checked ? "shown" : "hidden"), Toast.LENGTH_SHORT).show();
        });

        binding.switchShowForecast.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_FORECAST, checked).apply();
            Toast.makeText(requireContext(),
                "Forecast line " + (checked ? "shown" : "hidden"), Toast.LENGTH_SHORT).show();
        });

        binding.switchChartAnimation.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_CHART_ANIM, checked).apply();
            Toast.makeText(requireContext(),
                "Chart animation " + (checked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        binding.switchShowKpi.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_KPI, checked).apply();
            Toast.makeText(requireContext(),
                "KPI cards " + (checked ? "visible" : "hidden"), Toast.LENGTH_SHORT).show();
        });

        binding.switchShowAlerts.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_ALERTS, checked).apply();
            Toast.makeText(requireContext(),
                "Alert badges " + (checked ? "visible" : "hidden"), Toast.LENGTH_SHORT).show();
        });

        binding.switchLowStockNotif.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_LOW_STOCK_N, checked).apply();
            Toast.makeText(requireContext(),
                "Low-stock notifications " + (checked ? "on" : "off"), Toast.LENGTH_SHORT).show();
        });

        binding.switchExpiryNotif.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_EXPIRY_N, checked).apply();
            Toast.makeText(requireContext(),
                "Expiry alerts " + (checked ? "on" : "off"), Toast.LENGTH_SHORT).show();
        });
    }

    // ── Navigation buttons ───────────────────────────────────────────────────

    private void setupNavigationButtons() {
        binding.btnWebDashboard.setOnClickListener(v -> openWebDashboard());
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());
        binding.btnTerms.setOnClickListener(v -> openWebPage("https://vapecrib.com/terms"));
        binding.btnPrivacy.setOnClickListener(v -> openWebPage("https://vapecrib.com/privacy"));
        binding.btnHelp.setOnClickListener(v -> openWebPage("https://vapecrib.com/help"));
    }

    private void openWebDashboard() {
        openWebPage("https://vapecrib.onrender.com");
    }

    private void openWebPage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                "Unable to open page. Check your internet connection.", Toast.LENGTH_LONG).show();
        }
    }

    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("About VapeCrib")
            .setMessage("VapeCrib Sales & Restocking Dashboard\n\n"
                + "Version: 1.0.0\n\n"
                + "A comprehensive mobile application for managing sales forecasts, "
                + "inventory levels, and business intelligence for vape retailers.\n\n"
                + "© 2026 VapeCrib. All rights reserved.")
            .setPositiveButton("Close", (d, w) -> d.dismiss())
            .setNeutralButton("Visit Website", (d, w) -> openWebPage("https://vapecrib.com"))
            .show();
    }

    // ── CSV buttons ──────────────────────────────────────────────────────────

    @SuppressLint("NewApi")
    private void setupCSVButtons() {
        binding.btnReloadCsv.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Reloading data from CSV…", Toast.LENGTH_SHORT).show();
            com.example.vapecrib.data.db.AppDatabase.reloadFromCSV(requireContext());
            Toast.makeText(requireContext(), "Data refreshed!", Toast.LENGTH_SHORT).show();
        });

        binding.btnExportSales.setOnClickListener(v -> exportSalesCSV());
        binding.btnExportInventory.setOnClickListener(v -> exportInventoryCSV());

        binding.btnImportSales.setOnClickListener(v -> {
            importType = "sales";
            pickCSVFile();
        });
        binding.btnImportInventory.setOnClickListener(v -> {
            importType = "inventory";
            pickCSVFile();
        });
    }

    private void exportSalesCSV() {
        new Thread(() -> {
            try {
                String path = CSVExporter.exportSalesCSV(requireContext());
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Sales exported to:\n" + path, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void exportInventoryCSV() {
        new Thread(() -> {
            try {
                String path = CSVExporter.exportInventoryCSV(requireContext());
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Inventory exported to:\n" + path, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void pickCSVFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/plain"});
        startActivityForResult(intent, PICK_CSV_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_REQUEST && data != null) {
            Uri uri = data.getData();
            if (uri != null) importCSVFromUri(uri);
        }
    }

    private void importCSVFromUri(Uri uri) {
        new Thread(() -> {
            try {
                String filePath = getRealPathFromURI(uri);
                if (filePath == null) {
                    requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Could not access file", Toast.LENGTH_SHORT).show());
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
                        "Imported " + finalCount + " rows successfully", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getRealPathFromURI(Uri uri) {
        try {
            java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return null;
            java.io.File temp = new java.io.File(requireContext().getCacheDir(), "temp_import.csv");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(temp);
                 java.io.BufferedInputStream bis = new java.io.BufferedInputStream(is)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = bis.read(buf)) != -1) fos.write(buf, 0, n);
            }
            return temp.getAbsolutePath();
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
