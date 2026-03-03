package com.example.vapecrib.data.db;

import androidx.room.TypeConverter;

import java.time.LocalDate;

public class DateConverter {

    @TypeConverter
    public static String fromLocalDate(LocalDate date) {
        return date == null ? null : date.toString(); // "yyyy-MM-dd"
    }

    @TypeConverter
    public static LocalDate toLocalDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
