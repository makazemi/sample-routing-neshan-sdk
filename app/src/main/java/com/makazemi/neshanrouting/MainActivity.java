package com.makazemi.neshanrouting;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.carto.graphics.Color;
import com.carto.styles.AnimationStyle;
import com.carto.styles.AnimationStyleBuilder;
import com.carto.styles.AnimationType;
import com.carto.styles.LineStyle;
import com.carto.styles.LineStyleBuilder;
import com.carto.styles.MarkerStyle;
import com.carto.styles.MarkerStyleBuilder;
import com.carto.utils.BitmapUtils;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.single.PermissionListener;
import com.makazemi.neshanrouting.databinding.ActivityMainBinding;
import com.makazemi.neshanrouting.viewModel.MainViewModel;

import org.neshan.common.model.LatLng;
import org.neshan.mapsdk.MapView;
import org.neshan.mapsdk.model.Marker;
import org.neshan.mapsdk.model.Polyline;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivityNeshan";

    final int REQUEST_CODE = 123;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private Location userLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationCallback locationCallback;
    private Marker sourceMarker;
    private Marker destinationMarker;
    MapView map;
    List<LatLng> decodedStepByStepPath;
    private MainViewModel viewModel;
    private LineStyle lineStyle;
    private Polyline onMapPolyline;
    private Marker navigationMarker;
    private MarkerStyle navigationMarkerStyle;
    private MarkerStyle markerStyle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        init();
    }

    private void init() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        decodedStepByStepPath = new ArrayList<>();
        buildMarkerStyle();
        buildLineStyle();
        initNavigationMarkerStyle();
    }

    @Override
    protected void onStart() {
        super.onStart();
        initLayoutReferences();
        initLocation();
        startReceivingLocationUpdates();
        subscribeObserverResultRoute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void initLayoutReferences() {
        initViews();
        initMap();

        binding.btnCurrentLocation.setOnClickListener(v -> {
            focusOnUserLocation();
        });

        map.setOnMapLongClickListener(latLng -> {
            if (sourceMarker == null) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Please select source", Toast.LENGTH_LONG).show();
                    }
                });

            } else {
                addDestinationMarker(latLng);
                neshanRoutingApi();
                Log.d(TAG, "destination=(" + latLng.getLatitude() + "," + latLng.getLongitude() + ")");
            }
        });

        binding.btnRouting.setOnClickListener(v -> {
            if (decodedStepByStepPath.size() > 0) {
                simulateNavigation();
            } else {
                Toast.makeText(this, "path not provided", Toast.LENGTH_LONG).show();
            }
        });

        binding.btnClear.setOnClickListener(v -> {
            clearPath();
        });
    }

    private void initViews() {
        map = binding.map;
    }

    private void initMap() {
        if (userLocation == null) {
            map.moveCamera(new LatLng(35.767234, 51.330743), 0);
        } else {
            map.moveCamera(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()), 0);
        }
        map.setZoom(14, 0);
    }


    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (userLocation == null) {
                    userLocation = locationResult.getLastLocation();
                    Log.d(TAG, "location callback=" + userLocation);
                    onLocationChange();
                    focusOnUserLocation();
                }
            }
        };


        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();

    }

    private void startLocationUpdates() {
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
                        onLocationChange();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                try {
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CODE);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }

                        onLocationChange();
                    }
                });
    }

    public void stopLocationUpdates() {
        fusedLocationClient
                .removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Location updates stopped!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void startReceivingLocationUpdates() {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        startLocationUpdates();
                        Log.d(TAG, "onPermisgra");
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            openSettings();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(com.karumi.dexter.listener.PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }

                }).check();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.e(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e(TAG, "User chose not to make required location settings changes.");
                        break;
                }
                break;
        }
    }

    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package",
                BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void onLocationChange() {
        if (userLocation != null) {
            addUserMarker(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()));
        }
    }

    private void addUserMarker(LatLng loc) {
        if (sourceMarker != null) {
            map.removeMarker(sourceMarker);
        }
        sourceMarker = new Marker(loc, markerStyle);
        map.addMarker(sourceMarker);
    }

    public void focusOnUserLocation() {
        if (userLocation != null) {
            Log.d(TAG, "source=(" + userLocation.getLatitude() + "," + userLocation.getLongitude() + ")");
            map.moveCamera(
                    new LatLng(userLocation.getLatitude(), userLocation.getLongitude()), 0.25f);
            map.setZoom(15, 0.25f);
        }
    }

    private void buildMarkerStyle() {
        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleBuilder markStCr = new MarkerStyleBuilder();
        markStCr.setSize(30f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_marker)));
        markStCr.setAnimationStyle(animSt);
        markerStyle = markStCr.buildStyle();
    }

    private void addDestinationMarker(LatLng loc) {
        if (destinationMarker != null) {
            map.removeMarker(destinationMarker);
        }
        if (navigationMarker != null) {
            map.removeMarker(navigationMarker);
            navigationMarker = null;
        }
        destinationMarker = new Marker(loc, markerStyle);
        map.addMarker(destinationMarker);
    }

    private void subscribeObserverResultRoute() {
        viewModel.getDecodedStepByStepPath().observe(this, value -> {
                    if (value != null) {
                        if (value.getError() != null) {
                            Toast.makeText(this, value.getError(), Toast.LENGTH_SHORT).show();
                        }
                        if (value.getPoints() != null) {
                            decodedStepByStepPath = value.getPoints();
                            if (onMapPolyline != null) {
                                map.removePolyline(onMapPolyline);
                            }
                            onMapPolyline = new Polyline(new ArrayList(decodedStepByStepPath), lineStyle);
                            map.addPolyline(onMapPolyline);
                            mapSetPosition();
                        }
                    }
                }
        );
    }

    private void neshanRoutingApi() {
        viewModel.findRoute(sourceMarker.getLatLng(), destinationMarker.getLatLng());
    }

    private void mapSetPosition() {
        double centerFirstMarkerX = sourceMarker.getLatLng().getLatitude();
        double centerFirstMarkerY = sourceMarker.getLatLng().getLongitude();
        double centerFocalPositionX = (centerFirstMarkerX + destinationMarker.getLatLng().getLatitude()) / 2;
        double centerFocalPositionY = (centerFirstMarkerY + destinationMarker.getLatLng().getLongitude()) / 2;
        map.moveCamera(new LatLng(centerFocalPositionX, centerFocalPositionY), 0.5f);
        map.setZoom(14, 0.5f);
    }

    private void buildLineStyle() {
        LineStyleBuilder lineStCr = new LineStyleBuilder();
        lineStCr.setColor(new Color((short) 2, (short) 119, (short) 189, (short) 190));
        lineStCr.setWidth(10f);
        lineStCr.setStretchFactor(0f);
        lineStyle = lineStCr.buildStyle();
    }

    private void initNavigationMarkerStyle() {
        MarkerStyleBuilder markStCr = new MarkerStyleBuilder();
        markStCr.setSize(30f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.navigation)));
        navigationMarkerStyle = markStCr.buildStyle();
    }

    private void simulateNavigation() {
        Handler handler1 = new Handler();
        for (int i = 0; i < decodedStepByStepPath.size(); i++) {
            int finalI = i;
            handler1.postDelayed(new Runnable() {
                @Override
                public void run() {
                    moveNavigationMarker(decodedStepByStepPath.get(finalI));
                }
            }, 1000L * i);
        }
    }

    private void moveNavigationMarker(LatLng loc) {
        if (navigationMarker != null) {
            map.removeMarker(navigationMarker);
        }
        navigationMarker = new Marker(loc, navigationMarkerStyle);
        map.addMarker(navigationMarker);
        map.moveCamera(loc, 0.5f);
        map.setZoom(18, 0.5f);
    }

    private void clearPath() {
        if (onMapPolyline != null) {
            map.removePolyline(onMapPolyline);
            onMapPolyline = null;
        }
        if (destinationMarker != null) {
            map.removeMarker(destinationMarker);
            destinationMarker = null;
        }
        if (navigationMarker != null) {
            map.removeMarker(navigationMarker);
            navigationMarker = null;
        }
        decodedStepByStepPath.clear();
    }

    //    ArrayList<LatLng> routeOverviewPolylinePoints;
//    private ArrayList<Double> durations = new ArrayList<>();
//    private List<LatLng> pathPoints = new ArrayList<>();
//    private double sumDuration = 0;
//    List<Double> distances = new ArrayList();
//       private void simulateWithConstantSpeed(){
//            new NeshanDirection.Builder(apiKey, sourceMarker.getLatLng(), destinationMarker.getLatLng())
//                .build().call(new Callback<NeshanDirectionResult>() {
//            @Override
//            public void onResponse(Call<NeshanDirectionResult> call, Response<NeshanDirectionResult> response) {
//
//                // two type of routing
//                Route route = response.body().getRoutes().get(0);
//                DirectionResultLeg leg = route.getLegs().get(0);
//
//                List<DirectionStep> steps = leg.getDirectionSteps();
//
//                steps.get(0).getDistance()
//
//
//                routeOverviewPolylinePoints = new ArrayList<>(PolylineEncoding.decode(route.getOverviewPolyline().getEncodedPolyline()));
//                decodedStepByStepPath = new ArrayList<>();
//
//                pathPoints.clear();
//                distances.clear();
//
//                // decoding each segment of steps and putting to an array
//                for (DirectionStep step : route.getLegs().get(0).getDirectionSteps()) {
//                    decodedStepByStepPath.addAll(PolylineEncoding.decode(step.getEncodedPolyline()));
//                    pathPoints.addAll(PolylineEncoding.decode(step.getEncodedPolyline()));
//                    distances.add(step.getDistance());
//                }
//
//
//                durations.clear();
//                durations.add(3000d);
//
//
//                List<LatLng> indexes = new ArrayList<>();
//                for (int i = 0; i < distances.size(); i++) {
//                    if (distances.get(i) == 0) {
//                        indexes.add(pathPoints.get(i));
//                    }
//                }
//                pathPoints.removeAll(indexes);
//
//                distances.clear();
//
//                for (int i = 1; i < pathPoints.size(); i++) {
//                    distances.add(distance(pathPoints.get(i), pathPoints.get(i - 1)));
//                }
//
//
//                for (int i = 1; i < distances.size(); i++) {
//                    double t = (distances.get(i) * durations.get(i - 1)) / distances.get(i - 1);
//                    durations.add(t);
//                    sumDuration += t;
//                }
//            }
//
//            @Override
//            public void onFailure(Call<NeshanDirectionResult> call, Throwable t) {
//                Log.d(TAG, "onFailure=" + t.getMessage());
//            }
//        });
//      }

//    private double distance(LatLng loc1, LatLng loc2) {
//        double lat1 = loc1.getLatitude();
//        double lon1 = loc1.getLongitude();
//        double lat2 = loc2.getLatitude();
//        double lon2 = loc2.getLongitude();
//        lon1 = Math.toRadians(lon1);
//        lon2 = Math.toRadians(lon2);
//        lat1 = Math.toRadians(lat1);
//        lat2 = Math.toRadians(lat2);
//
//        double dlon = lon2 - lon1;
//        double dlat = lat2 - lat1;
//        double a = Math.pow(Math.sin(dlat / 2), 2)
//                + Math.cos(lat1) * Math.cos(lat2)
//                * Math.pow(Math.sin(dlon / 2), 2);
//        double c = 2 * Math.asin(Math.sqrt(a));
//        double r = 6371;
//        return (c * r) * 1000;
//    }
}