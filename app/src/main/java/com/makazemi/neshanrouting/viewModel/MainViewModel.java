package com.makazemi.neshanrouting.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.makazemi.neshanrouting.model.DataState;
import com.makazemi.neshanrouting.repository.MainRepository;

import org.neshan.common.model.LatLng;

public class MainViewModel extends ViewModel {

    private MainRepository repository;

    public MainViewModel() {
        repository = new MainRepository();
    }

    public void findRoute(LatLng source, LatLng destination) {
        repository.findRouting(source, destination);
    }

    public LiveData<DataState> getDecodedStepByStepPath() {
        return repository.getDecodedStepByStepPath();
    }
}
