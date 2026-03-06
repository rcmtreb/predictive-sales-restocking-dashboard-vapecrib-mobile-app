package com.example.vapecrib.ui.forecast;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.databinding.FragmentForecastBinding;
import com.github.mikephil.charting.components.XAxis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ForecastFragment extends Fragment {

    private static final LocalDate CSV_MIN_DATE = LocalDate.of(2022, 1, 1);
    private static final LocalDate CSV_MAX_DATE = LocalDate.of(2026, 3, 31);

    private ForecastViewModel forecastViewModel;
    private FragmentForecastBinding binding;

    private static final String[] MONTHS = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    /** Week options recalculated whenever year or month changes, matching the web's populateWeeks(). */
    private List<String> currentWeekOptions = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        forecastViewModel = new ViewModelProvider(requireActivity()).get(ForecastViewModel.class);
        binding = FragmentForecastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        configureForecastChart();
        populateDropdowns();
        applyDefaultSelections();
        observeViewModel();
        setupLoadButton();

        // Auto-load on first creation using whichever week applyDefaultSelections()
        // already selected. If the ViewModel already has chart data from a previous
        // visit, keep it so filters survive back-navigation.
        if (forecastViewModel.getForecastData().getValue() == null) {
            applyForecastFilter();
        }

        // Pull-to-refresh resets to current week defaults and reloads.
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            applyDefaultSelections();
            applyForecastFilter();
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    //  Chart style 

    private void configureForecastChart() {
        binding.forecastLineChart.getDescription().setEnabled(false);
        binding.forecastLineChart.setTouchEnabled(true);
        binding.forecastLineChart.setDragEnabled(true);
        binding.forecastLineChart.setScaleEnabled(true);
        binding.forecastLineChart.setPinchZoom(true);
        binding.forecastLineChart.getLegend().setEnabled(true);
        binding.forecastLineChart.getLegend().setTextColor(Color.WHITE);
        binding.forecastLineChart.getAxisRight().setEnabled(false);
        binding.forecastLineChart.getAxisLeft().setTextColor(Color.WHITE);
        binding.forecastLineChart.getAxisLeft().setAxisMinimum(0f);
        binding.forecastLineChart.getXAxis().setTextColor(Color.WHITE);
        binding.forecastLineChart.getXAxis().setDrawGridLines(false);
        binding.forecastLineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    //  Dropdowns 

    /** Creates a no-op Filter so AutoCompleteTextView always shows all options
     *  regardless of the current text value — prevents the "only current year
     *  shows up" bug when navigating back to the fragment. */
    private <T> ArrayAdapter<T> noFilterAdapter(List<T> items) {
        return new ArrayAdapter<T>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, items) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = items;
                        r.count  = items.size();
                        return r;
                    }
                    @Override
                    protected void publishResults(CharSequence c, FilterResults r) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }

    private void populateDropdowns() {
        // Products — seeded initially with "All Products"; updated when ViewModel loads from API
        List<String> initial = new ArrayList<>();
        initial.add("All Products");
        binding.actvProduct.setAdapter(noFilterAdapter(initial));
        binding.actvProduct.setSaveEnabled(false);

        // Years: 2022 to currentYear+1 — no-op filter so all years always appear
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        ArrayList<String> years = new ArrayList<>();
        for (int y = 2022; y <= currentYear + 1; y++) years.add(String.valueOf(y));
        binding.actvYear.setAdapter(noFilterAdapter(years));
        binding.actvYear.setSaveEnabled(false);

        // Months — no-op filter
        List<String> monthList = new ArrayList<>();
        for (String m : MONTHS) monthList.add(m);
        binding.actvMonth.setAdapter(noFilterAdapter(monthList));
        binding.actvMonth.setSaveEnabled(false);

        // Weeks — dynamic: populated by populateWeeks() based on selected year/month.
        // Initial population uses the current year/month.
        int initYear  = Calendar.getInstance().get(Calendar.YEAR);
        int initMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
        populateWeeks(initYear, initMonth);

        // Year change → repopulate weeks (auto-selects week) + auto-load.
        binding.actvYear.setOnItemClickListener((parent, view, pos, id) -> {
            String selYear  = binding.actvYear.getText().toString().trim();
            String selMonth = binding.actvMonth.getText().toString().trim();
            if (!selYear.isEmpty() && !selMonth.isEmpty()) {
                try {
                    int mo = getMonthNumber(selMonth);
                    if (mo != -1) {
                        populateWeeks(Integer.parseInt(selYear), mo);
                        applyForecastFilter();
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Month change → repopulate weeks (auto-selects week) + auto-load.
        binding.actvMonth.setOnItemClickListener((parent, view, pos, id) -> {
            String selYear  = binding.actvYear.getText().toString().trim();
            String selMonth = binding.actvMonth.getText().toString().trim();
            if (!selYear.isEmpty() && !selMonth.isEmpty()) {
                try {
                    int mo = getMonthNumber(selMonth);
                    if (mo != -1) {
                        populateWeeks(Integer.parseInt(selYear), mo);
                        applyForecastFilter();
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Week change → auto-load immediately (matches web's wEl change listener).
        binding.actvWeek.setOnItemClickListener((parent, view, pos, id) -> applyForecastFilter());

        // Product change → auto-load immediately.
        binding.actvProduct.setOnItemClickListener((parent, view, pos, id) -> applyForecastFilter());
    }

    /**
     * Recalculates week options based on the actual number of days in the given month,
     * matching the web app's populateWeeks() function. Each option shows its exact
     * day range, e.g. "Week 3 (15-21)".
     * Matches the web: no "All Weeks" blank option; auto-selects current week for
     * the current month/year, otherwise defaults to Week 1.
     */
    private void populateWeeks(int year, int month) {
        int numDays  = LocalDate.of(year, month, 1).lengthOfMonth();
        int numWeeks = (int) Math.ceil(numDays / 7.0);

        currentWeekOptions = new ArrayList<>();
        // No blank "All Weeks" option — start directly with Week 1, matching the web.
        for (int w = 1; w <= numWeeks; w++) {
            int dayStart = (w - 1) * 7 + 1;
            int dayEnd   = Math.min(w * 7, numDays);
            currentWeekOptions.add("Week " + w + " (" + dayStart + "-" + dayEnd + ")");
        }

        binding.actvWeek.setAdapter(noFilterAdapter(new ArrayList<>(currentWeekOptions)));
        binding.actvWeek.setSaveEnabled(false);

        // Auto-select the current week when viewing the current month/year (mirrors
        // the web's auto-select). For any other month/year, default to Week 1.
        LocalDate today = LocalDate.now();
        if (year == today.getYear() && month == today.getMonthValue()) {
            int weekIdx = (today.getDayOfMonth() - 1) / 7; // 0-based
            binding.actvWeek.setText(
                    currentWeekOptions.get(Math.min(weekIdx, currentWeekOptions.size() - 1)), false);
        } else {
            binding.actvWeek.setText(currentWeekOptions.get(0), false);
        }
    }

    /**
     * Resets all dropdowns to their defaults: blank product (= All Products), current
     * year and month, and the current week auto-selected by populateWeeks().
     * Mirrors the web page's DOMContentLoaded initialization.
     */
    private void applyDefaultSelections() {
        LocalDate today = LocalDate.now();
        int  currentYear  = today.getYear();
        int  currentMonth = today.getMonthValue();

        binding.actvProduct.setText("", false);
        binding.actvYear.setText(String.valueOf(currentYear), false);
        binding.actvMonth.setText(MONTHS[currentMonth - 1], false);
        // populateWeeks auto-selects the current week for today's month/year.
        populateWeeks(currentYear, currentMonth);
    }

    //  Observers 

    private void observeViewModel() {
        // Show/hide the SwipeRefreshLayout spinner while a background fetch is running.
        // This gives the user clear feedback that Load Forecast is working.
        forecastViewModel.getIsLoading().observe(getViewLifecycleOwner(),
                loading -> binding.swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading)));

        // Update the chart card subtitle with forecast status (e.g. "no forecast data" hint).
        forecastViewModel.getForecastSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null && !summary.isEmpty()) {
                binding.tvForecastHint.setText(summary);
            }
        });

        forecastViewModel.getForecastData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null && lineData.getDataSetCount() > 0) {
                // Clear old chart data first — mirrors web's chart.destroy() + new Chart()
                // pattern, preventing stale dataset state when navigating back to this page.
                binding.forecastLineChart.clear();
                binding.forecastLineChart.setData(lineData);
                lineData.notifyDataChanged();
                binding.forecastLineChart.notifyDataSetChanged();
                binding.forecastLineChart.getXAxis().setAxisMinimum(0);
                // Only set axis maximum when there is real data; getXMax() returns
                // Float.NaN on an empty LineData which crashes setAxisMaximum().
                float xMax = lineData.getXMax();
                if (!Float.isNaN(xMax) && xMax >= 0) {
                    binding.forecastLineChart.getXAxis().setAxisMaximum(xMax);
                }
                binding.forecastLineChart.animateX(500);
            } else {
                // null  = truly unloaded (first open guard)
                // empty LineData = period has no data; keep the chart blank so the
                //   user sees empty axes rather than a stale previous period's data.
                binding.forecastLineChart.clear();
                binding.forecastLineChart.invalidate();
            }
        });

        // When the ViewModel finishes loading real product names, refresh the dropdown
        // using the same no-op filter so all products are always visible in the list
        forecastViewModel.getProductNames().observe(getViewLifecycleOwner(), names -> {
            if (names == null || names.isEmpty()) return;
            List<String> namesCopy = new ArrayList<>(names);
            binding.actvProduct.setAdapter(noFilterAdapter(namesCopy));
            binding.actvProduct.setSaveEnabled(false);
        });
    }

    //  Load button 

    private void setupLoadButton() {
        binding.btnLoadForecast.setOnClickListener(v -> applyForecastFilter());
    }

    private void applyForecastFilter() {
        String productText = binding.actvProduct.getText().toString().trim();
        String yearText    = binding.actvYear.getText().toString().trim();
        String monthText   = binding.actvMonth.getText().toString().trim();
        String weekText    = binding.actvWeek.getText().toString().trim();

        if (yearText.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a year", Toast.LENGTH_SHORT).show();
            return;
        }
        if (monthText.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a month", Toast.LENGTH_SHORT).show();
            return;
        }

        int year;
        try { year = Integer.parseInt(yearText); }
        catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid year", Toast.LENGTH_SHORT).show();
            return;
        }

        int month = getMonthNumber(monthText);
        if (month == -1) {
            Toast.makeText(requireContext(), "Invalid month", Toast.LENGTH_SHORT).show();
            return;
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = null;

        // Narrow to the selected week. Parse "Week N (D1-D2)" produced by populateWeeks().
        if (!weekText.isEmpty()) {
            int parenStart = weekText.indexOf('(');
            int parenEnd   = weekText.indexOf(')');
            if (parenStart >= 0 && parenEnd > parenStart) {
                try {
                    String[] parts = weekText.substring(parenStart + 1, parenEnd).split("-");
                    int dayStart = Integer.parseInt(parts[0].trim());
                    int dayEnd   = Integer.parseInt(parts[1].trim());
                    start = LocalDate.of(year, month, dayStart);
                    end   = LocalDate.of(year, month, dayEnd);
                } catch (Exception ignored) {}
            }
        }
        // Fallback: full month if week was blank or parsing failed
        if (end == null) {
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        // Clamp to sample data range Jan-2025..Feb-2026
        LocalDate dataMin = CSV_MIN_DATE;
        LocalDate dataMax = CSV_MAX_DATE;
        if (end.isBefore(dataMin) || start.isAfter(dataMax)) {
            Toast.makeText(requireContext(),
                "No data for that period. Data covers Jan 2022 to Mar 2026.",
                Toast.LENGTH_LONG).show();
            return;
        }
        start = start.isBefore(dataMin) ? dataMin : start;
        end   = end.isAfter(dataMax)    ? dataMax : end;

        // Update product selection and fetch — no toast needed (spinner provides feedback).
        forecastViewModel.setSelectedProduct(productText.isEmpty() ? "All Products" : productText);
        forecastViewModel.filterByDateRange(start, end);
    }

    private int getMonthNumber(String name) {
        for (int i = 0; i < MONTHS.length; i++)
            if (MONTHS[i].equalsIgnoreCase(name)) return i + 1;
        return -1;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
