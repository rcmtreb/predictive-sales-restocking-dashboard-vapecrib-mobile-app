package com.example.vapecrib.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.vapecrib.R;
import com.example.vapecrib.databinding.FragmentDashboardBinding;
import com.example.vapecrib.network.TopProduct;
import com.example.vapecrib.ui.settings.SettingsFragment;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

@SuppressLint("NewApi")
public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private FragmentDashboardBinding binding;
    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;
    /** Skip the very first onResume — the ViewModel constructor already fetches on creation. */
    private boolean skipFirstResume = true;

    @SuppressLint("NewApi")
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize dates — Last 7 Days shows the most recent activity on first open.
        // Matches the web's period=7d: filter_start = today - 6 days (7 days incl. today).
        selectedStartDate = LocalDate.now().minusDays(6);
        selectedEndDate   = LocalDate.now();

        // Apply saved display preferences
        applyDisplayPreferences();

        // Configure charts
        configureLineChart();
        configureBarChart();

        // KPI Observers
        dashboardViewModel.getTotalSalesProducts().observe(getViewLifecycleOwner(), binding.tvTotalSalesProducts::setText);
        dashboardViewModel.getTotalSalesRevenue().observe(getViewLifecycleOwner(), binding.tvTotalSalesRevenue::setText);
        dashboardViewModel.getTotalInventoryValue().observe(getViewLifecycleOwner(), binding.tvTotalInventoryValue::setText);
        dashboardViewModel.getForecastAccuracy().observe(getViewLifecycleOwner(), binding.tvForecastAccuracy::setText);
        dashboardViewModel.getActiveAlerts().observe(getViewLifecycleOwner(), binding.tvActiveAlerts::setText);
        dashboardViewModel.getExpiringStocks().observe(getViewLifecycleOwner(), binding.tvExpiringStocks::setText);
        dashboardViewModel.getCriticalCount().observe(getViewLifecycleOwner(), binding.tvCriticalCount::setText);
        dashboardViewModel.getHighCount().observe(getViewLifecycleOwner(), binding.tvHighCount::setText);
        dashboardViewModel.getMediumCount().observe(getViewLifecycleOwner(), binding.tvMediumCount::setText);
        // Top 5 Products — populated from /dashboard API response
        dashboardViewModel.getTopProducts().observe(getViewLifecycleOwner(), this::populateTopProducts);
        // Swipe-to-refresh — lets the user manually retry after a server timeout
        SwipeRefreshLayout swipeRefresh = binding.swipeRefreshLayout;
        swipeRefresh.setOnRefreshListener(() ->
                dashboardViewModel.filterByDateRange(selectedStartDate, selectedEndDate));

        // Stop the spinner once new chart data or an error arrives
        dashboardViewModel.getSalesTrendData().observe(getViewLifecycleOwner(), d -> swipeRefresh.setRefreshing(false));

        // Show a brief toast when the API can't be reached (cached data is still shown).
        // clearApiError() is called after display to prevent the sticky-LiveData problem
        // where re-subscribing on navigation-back would re-fire an old error.
        dashboardViewModel.getApiError().observe(getViewLifecycleOwner(), err -> {
            swipeRefresh.setRefreshing(false);
            if (err != null && !err.isEmpty() && isAdded()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                dashboardViewModel.clearApiError();
            }
        });
        dashboardViewModel.getSalesTrendData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null) {
                binding.lineChart.setData(lineData);
                binding.lineChart.getXAxis().setAxisMinimum(0);
                int entryCount = lineData.getDataSetByIndex(0).getEntryCount();
                binding.lineChart.getXAxis().setAxisMaximum(entryCount - 1);
                updateChartVisibility();
                SharedPreferences p = requireContext()
                    .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                if (p.getBoolean(SettingsFragment.KEY_CHART_ANIM, true)) {
                    binding.lineChart.animateX(300);
                } else {
                    binding.lineChart.invalidate();
                }
            }
        });
        dashboardViewModel.getMonthlyPerformanceData().observe(getViewLifecycleOwner(), barData -> {
            if (barData != null) {
                binding.barChart.setData(barData);
                binding.barChart.getXAxis().setAxisMinimum(0);
                int entryCount = barData.getDataSetByIndex(0).getEntryCount();
                binding.barChart.getXAxis().setAxisMaximum(entryCount - 1);
                SharedPreferences p = requireContext()
                    .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                if (p.getBoolean(SettingsFragment.KEY_CHART_ANIM, true)) {
                    binding.barChart.animateY(300);
                } else {
                    binding.barChart.invalidate();
                }
            }
        });

        // Use a no-op filter so the dropdown always shows all options regardless of
        // current text — without this, AutoCompleteTextView filters the list to show
        // only the item matching the currently-set text (e.g. only "Last 3 Months").
        String[] timePeriods = {"All Time", "Last 7 Days", "Last 30 Days", "Last 3 Months", "Last 6 Months", "Last Year", "Custom"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_dropdown_item_1line, timePeriods) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = timePeriods;
                        r.count  = timePeriods.length;
                        return r;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        binding.actvTimePeriod.setAdapter(adapter);
        // Pre-select "Last 7 Days" so the dropdown label matches the default data range
        binding.actvTimePeriod.setText("Last 7 Days", false);
        updateDateDisplay();
        binding.actvTimePeriod.setOnItemClickListener((parent, view, position, id) -> {
            handleTimePeriodSelection(position);
        });

        // Date Click Listeners
        binding.etStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        binding.etEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Checkbox Listeners
        binding.cbActual.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());
        binding.cbForecast.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());

        return root;
    }

    private void handleTimePeriodSelection(int position) {
        LocalDate today = LocalDate.now();
        // Date ranges match the web server exactly:
        //   web period=Nd  uses  filter_start = today - timedelta(days=N-1)
        //   so  "Last 30 Days" = today - 29 days  (30 days incl. today)
        switch (position) {
            case 0: // All Time
                selectedStartDate = LocalDate.of(2022, 1, 1);
                selectedEndDate = today;
                break;
            case 1: // Last 7 Days  (= web 7d: today - 6 days)
                selectedStartDate = today.minusDays(6);
                selectedEndDate = today;
                break;
            case 2: // Last 30 Days (= web 30d: today - 29 days)
                selectedStartDate = today.minusDays(29);
                selectedEndDate = today;
                break;
            case 3: // Last 3 Months (= web 3m: 90 days, today - 89 days)
                selectedStartDate = today.minusDays(89);
                selectedEndDate = today;
                break;
            case 4: // Last 6 Months (= web 6m: 180 days, today - 179 days)
                selectedStartDate = today.minusDays(179);
                selectedEndDate = today;
                break;
            case 5: // Last Year (= web 1y: 365 days, today - 364 days)
                selectedStartDate = today.minusDays(364);
                selectedEndDate = today;
                break;
            case 6: // Custom
                return; // User will select dates manually
        }
        
        updateDateDisplay();
        applyFilter();
    }

    private void updateDateDisplay() {
        if (binding == null) return;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        if (selectedStartDate != null) binding.etStartDate.setText(selectedStartDate.format(formatter));
        if (selectedEndDate   != null) binding.etEndDate.setText(selectedEndDate.format(formatter));
    }

    private void showDatePickerDialog(boolean isStartDate) {
        if (!isAdded() || getContext() == null) return;
        // Seed the picker from the currently selected date so the calendar opens at the right month
        LocalDate seed = isStartDate ? selectedStartDate : selectedEndDate;
        Calendar calendar = Calendar.getInstance();
        calendar.set(seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth());
        int year  = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day   = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view, year1, month1, dayOfMonth) -> {
            if (binding == null) return;   // fragment already destroyed — ignore
            LocalDate selectedDate = LocalDate.of(year1, month1 + 1, dayOfMonth);
            if (isStartDate) {
                selectedStartDate = selectedDate;
            } else {
                selectedEndDate = selectedDate;
            }
            updateDateDisplay();
            applyFilter();
        }, year, month, day);
        datePickerDialog.show();
    }

    private void applyFilter() {
        if (selectedStartDate == null || selectedEndDate == null) return;
        if (selectedEndDate.isBefore(selectedStartDate)) {
            // Swap dates if end date is before start date
            LocalDate temp    = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate   = temp;
            updateDateDisplay();
        }
        dashboardViewModel.filterByDateRange(selectedStartDate, selectedEndDate);
    }

    /** Called when a checkbox changes OR when settings prefs change visibility. */
    private void updateChartVisibility() {
        LineData lineData = binding.lineChart.getLineData();
        if (lineData != null) {
            boolean isActualChecked   = binding.cbActual.isChecked();
            boolean isForecastChecked = binding.cbForecast.isChecked();
            if (lineData.getDataSetCount() > 0)
                lineData.getDataSetByIndex(0).setVisible(isActualChecked);
            if (lineData.getDataSetCount() > 1)
                lineData.getDataSetByIndex(1).setVisible(isForecastChecked);
            binding.lineChart.invalidate();
        }
    }

    /** Read SharedPreferences and apply visibility to KPI cards and alert section. */
    private void applyDisplayPreferences() {
        if (!isAdded() || getContext() == null || binding == null) return;
        SharedPreferences p = requireContext()
            .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);

        // Sync chart checkboxes with saved prefs (only on first load — don't override user in-session)
        boolean savedActual   = p.getBoolean(SettingsFragment.KEY_SHOW_ACTUAL,   true);
        boolean savedForecast = p.getBoolean(SettingsFragment.KEY_SHOW_FORECAST, true);
        // Temporarily remove listeners to avoid firing applyFilter during init
        binding.cbActual.setOnCheckedChangeListener(null);
        binding.cbForecast.setOnCheckedChangeListener(null);
        binding.cbActual.setChecked(savedActual);
        binding.cbForecast.setChecked(savedForecast);
        binding.cbActual.setOnCheckedChangeListener((btn, c) -> updateChartVisibility());
        binding.cbForecast.setOnCheckedChangeListener((btn, c) -> updateChartVisibility());

        // KPI summary card group visibility
        boolean showKpi = p.getBoolean(SettingsFragment.KEY_SHOW_KPI, true);
        if (binding.getRoot().findViewWithTag("kpi_group") != null) {
            binding.getRoot().findViewWithTag("kpi_group")
                .setVisibility(showKpi ? View.VISIBLE : View.GONE);
        }
        // Try direct card IDs if they exist
        try {
            View kpiCard = binding.getRoot().findViewById(
                com.example.vapecrib.R.id.cardKpiSummary);
            if (kpiCard != null) kpiCard.setVisibility(showKpi ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {}

        // Alert badge section visibility
        boolean showAlerts = p.getBoolean(SettingsFragment.KEY_SHOW_ALERTS, true);
        try {
            View alertCard = binding.getRoot().findViewById(
                com.example.vapecrib.R.id.cardAlerts);
            if (alertCard != null) alertCard.setVisibility(showAlerts ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {}
    }

    private void configureLineChart() {
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.setTouchEnabled(true);
        binding.lineChart.setDragEnabled(true);
        binding.lineChart.setScaleEnabled(true);
        binding.lineChart.setPinchZoom(true);
        binding.lineChart.getLegend().setEnabled(true);
        binding.lineChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getAxisRight().setEnabled(false);
        
        binding.lineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getAxisLeft().setAxisMinimum(0f);
        
        binding.lineChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getXAxis().setDrawGridLines(false);
        binding.lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    private void configureBarChart() {
        binding.barChart.getDescription().setEnabled(false);
        binding.barChart.setTouchEnabled(true);
        binding.barChart.setDragEnabled(true);
        binding.barChart.setScaleEnabled(true);
        binding.barChart.setPinchZoom(true);
        binding.barChart.getLegend().setEnabled(true);
        binding.barChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getAxisRight().setEnabled(false);
        
        binding.barChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getAxisLeft().setAxisMinimum(0f);
        
        binding.barChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getXAxis().setDrawGridLines(false);
        binding.barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    /** Dynamically builds the Top 5 Products rows inside the card container. */
    private void populateTopProducts(List<TopProduct> products) {
        if (binding == null || !isAdded()) return;
        LinearLayout container = binding.getRoot().findViewById(R.id.ll_top_products_container);
        if (container == null) return;
        container.removeAllViews();

        if (products == null || products.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No data available");
            empty.setTextColor(Color.parseColor("#616161"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            container.addView(empty);
            return;
        }

        int dp4  = (int)(4  * getResources().getDisplayMetrics().density);
        int dp24 = (int)(24 * getResources().getDisplayMetrics().density);

        int rank = 1;
        for (TopProduct p : products) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp4, 0, dp4);
            row.setLayoutParams(rowLp);

            // Rank badge
            TextView tvRank = new TextView(requireContext());
            tvRank.setText(rank + ".");
            tvRank.setTextColor(Color.parseColor("#616161"));
            tvRank.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams rankLp = new LinearLayout.LayoutParams(dp24,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tvRank.setLayoutParams(rankLp);

            // Product name
            TextView tvName = new TextView(requireContext());
            tvName.setText(p.productName != null ? p.productName : "—");
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameLp);

            // Stats: units · revenue
            TextView tvStats = new TextView(requireContext());
            tvStats.setText(p.unitsSold + " units  ₱" + String.format("%,.0f", p.revenue));
            tvStats.setTextColor(Color.parseColor("#616161"));
            tvStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            tvStats.setGravity(Gravity.END);
            tvStats.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            row.addView(tvRank);
            row.addView(tvName);
            row.addView(tvStats);
            container.addView(row);
            rank++;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skipFirstResume) {
            skipFirstResume = false;
            return; // ViewModel constructor already kicked off the initial fetch
        }
        // Refresh data every time the user navigates back to the dashboard so it always
        // shows the latest figures without requiring a manual swipe-to-refresh.
        if (dashboardViewModel != null && binding != null) {
            binding.swipeRefreshLayout.setRefreshing(true);
            dashboardViewModel.refresh();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
