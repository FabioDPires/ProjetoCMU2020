package com.click2eat.app.ui.live;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.click2eat.app.R;
import com.click2eat.app.api.RetrofitZomato;
import com.click2eat.app.ui.OnRestaurantClickedListener;
import com.click2eat.app.ui.RestaurantAdapter;
import com.click2eat.app.models.Restaurant;
import com.click2eat.app.models.Restaurant_;
import com.click2eat.app.models.SearchResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LiveFragment extends Fragment {

    private RestaurantAdapter mAdapter;
    private RecyclerView mRecyclerView;
    protected static List<Restaurant_> restaurantsList;
    private Context context;
    private static OnRestaurantClickedListener listener;
    private FirebaseAuth mAuth;
    private static final int REQUEST_FINE_LOCATION = 100;
    private FusedLocationProviderClient mFusedLocationClient;

    public LiveFragment() {
    }

    public static OnRestaurantClickedListener getListener() {
        return listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        mAuth = FirebaseAuth.getInstance();
        restaurantsList = new ArrayList<>();
        getLastLocation();
        mAdapter = new RestaurantAdapter(restaurantsList, getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mContentView = inflater.inflate(R.layout.fragment_live, container, false);
        mRecyclerView = mContentView.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContentView.getContext()));
        mRecyclerView.setAdapter(mAdapter);
        return mContentView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (OnRestaurantClickedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnButtonClicked");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getRestaurants();
            }
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions( //Method of Fragment
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION
            );
        } else {
            getRestaurants();
        }
    }

    @SuppressLint("MissingPermission")
    private void getRestaurants() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());
        mFusedLocationClient.getLastLocation().addOnSuccessListener(getActivity(), new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(final Location location) {
                if (location != null) {
                    SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
                    String sort = mSettings.getString("sort", "rating");
                    String order = mSettings.getString("order", "desc");
                    int radius = Integer.parseInt(mSettings.getString("radius", "10"));
                    radius = radius * 1000;

                    RetrofitZomato.getApi().getNearbyRestaurants(location.getLatitude(), location.getLongitude(), 20, radius, sort, order,
                            getResources().getString(R.string.user_key)).enqueue(new Callback<SearchResponse>() {
                        @Override
                        public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                            List<Restaurant> restaurants = response.body().getRestaurants();
                            for (int i = 0; i < restaurants.size(); i++) {
                                double distance = calculateDistance(Double.parseDouble(restaurants.get(i).getRestaurant().getLocation().getLatitude()),
                                        Double.parseDouble(restaurants.get(i).getRestaurant().getLocation().getLongitude()), location.getLatitude(), location.getLongitude());
                                distance = (double) Math.round(distance * 100d) / 100d;
                                restaurants.get(i).getRestaurant().setDistance(distance);
                                restaurantsList.add(restaurants.get(i).getRestaurant());
                                mAdapter.notifyItemInserted(i);
                            }
                        }

                        @Override
                        public void onFailure(Call<SearchResponse> call, Throwable t) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setMessage("Couldn't find any nearby restaurants");
                            AlertDialog mDialog = builder.create();
                            mDialog.show();
                        }
                    });
                }
            }
        }).addOnFailureListener(getActivity(), new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getActivity(), "It wasn't possible to determine your location", Toast.LENGTH_LONG).show();
            }
        });
    }

    private double calculateDistance(double latRestaurant, double lonRestaurant, double myLat, double myLon) {
        if ((myLat == latRestaurant) && (myLon == lonRestaurant)) {
            return 0;
        } else {
            double theta = myLon - lonRestaurant;
            double dist = Math.sin(Math.toRadians(myLat)) * Math.sin(Math.toRadians(latRestaurant))
                    + Math.cos(Math.toRadians(myLat)) * Math.cos(Math.toRadians(latRestaurant)) * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1.609344;
            return dist;
        }
    }

}