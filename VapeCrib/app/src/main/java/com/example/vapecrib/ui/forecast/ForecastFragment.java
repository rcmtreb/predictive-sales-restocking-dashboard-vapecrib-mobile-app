package com.example.vapecrib.ui.forecast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.R;
import com.example.vapecrib.databinding.FragmentForecastBinding;

import java.util.ArrayList;
import java.util.Calendar;

public class ForecastFragment extends Fragment {

    private ForecastViewModel forecastViewModel;
    private FragmentForecastBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        forecastViewModel = new ViewModelProvider(this).get(ForecastViewModel.class);

        binding = FragmentForecastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        forecastViewModel.getForecastData().observe(getViewLifecycleOwner(), lineData -> {
            binding.forecastLineChart.setData(lineData);
            binding.forecastLineChart.invalidate(); // refresh
        });

        // Populate Dropdowns
        populateProductDropdown();
        populateYearDropdown();
        populateMonthDropdown();
        populateWeekDropdown();

        // Set Button OnClickListener
        binding.btnLoadForecast.setOnClickListener(v -> {
            // Handle button click
            Toast.makeText(getContext(), "Loading forecast...", Toast.LENGTH_SHORT).show();
        });

        return root;
    }

    private void populateProductDropdown() {
        String[] products = {"All Products (Aggregated)", "Product A", "Product B"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, products);
        binding.actvProduct.setAdapter(adapter);
    }

    private void populateYearDropdown() {
        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear - 5; i <= currentYear + 5; i++) {
            years.add(String.valueOf(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, years);
        binding.actvYear.setAdapter(adapter);
        binding.actvYear.setText(String.valueOf(currentYear), false);
    }

    private void populateMonthDropdown() {
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, months);
        binding.actvMonth.setAdapter(adapter);
    }

    private void populateWeekDropdown() {
        String[] weeks = {"Week 1 (1-7)", "Week 2 (8-14)", "Week 3 (15-21)", "Week 4 (22-28)", "Week 5 (29-31)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, weeks);
        binding.actvWeek.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
