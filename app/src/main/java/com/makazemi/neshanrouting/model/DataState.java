package com.makazemi.neshanrouting.model;

import androidx.annotation.Nullable;

import org.neshan.common.model.LatLng;

import java.util.List;

public class DataState {

    public DataState(List<LatLng> points, String error) {
        this.points = points;
        this.error = error;
    }

    @Nullable
    private List<LatLng> points;
    @Nullable
    private String error;

    public List<LatLng> getPoints() {
        return points;
    }

    public void setPoints(List<LatLng> points) {
        this.points = points;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
