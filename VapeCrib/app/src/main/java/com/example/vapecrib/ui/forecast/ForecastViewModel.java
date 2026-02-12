package com.example.vapecrib.ui.forecast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.LineData;

public class ForecastViewModel extends ViewModel {

    private final MutableLiveData<LineData> forecastData = new MutableLiveData<>();

    public LiveData<LineData> getForecastData() {
        return forecastData;
    }
}
