package com.makazemi.neshanrouting.repository;


import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.makazemi.neshanrouting.model.DataState;

import org.neshan.common.model.Distance;
import org.neshan.common.model.LatLng;
import org.neshan.common.utils.PolylineEncoding;
import org.neshan.servicessdk.direction.NeshanDirection;
import org.neshan.servicessdk.direction.model.DirectionResultLeg;
import org.neshan.servicessdk.direction.model.DirectionStep;
import org.neshan.servicessdk.direction.model.NeshanDirectionResult;
import org.neshan.servicessdk.direction.model.Route;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainRepository {

    private final static String apiKey = "service.GKDubeKFho40B05yVLugN5otDokfIq7Ob9IQqHPo";

    private MutableLiveData<DataState> _decodedStepByStepPath;

    public MainRepository() {
        _decodedStepByStepPath = new MutableLiveData<>();
    }

    public void findRouting(LatLng source, LatLng destination) {
        new NeshanDirection.Builder(apiKey, source, destination)
                .build().call(new Callback<NeshanDirectionResult>() {
            @Override
            public void onResponse(Call<NeshanDirectionResult> call, Response<NeshanDirectionResult> response) {
                List<Route> routes = response.body().getRoutes();
                ArrayList<LatLng> result = new ArrayList<>();
                ArrayList<Distance> distances = new ArrayList<>();
                if (routes.size() > 0) {
                    List<DirectionResultLeg> legs = routes.get(0).getLegs();
                    if (legs.size() > 0) {
                        List<DirectionStep> steps = legs.get(0).getDirectionSteps();
                        for (DirectionStep item : steps) {
                            result.addAll(PolylineEncoding.decode(item.getEncodedPolyline()));
                            distances.add(item.getDistance());
                        }
                    }
                }
                _decodedStepByStepPath.postValue(new DataState(result, null));

            }

            @Override
            public void onFailure(Call<NeshanDirectionResult> call, Throwable t) {
                _decodedStepByStepPath.postValue(new DataState(null, t.getMessage()));
            }
        });
    }

    public LiveData<DataState> getDecodedStepByStepPath() {
        return _decodedStepByStepPath;
    }
}

