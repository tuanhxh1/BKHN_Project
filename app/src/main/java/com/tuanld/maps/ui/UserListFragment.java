package com.tuanld.maps.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;


import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.tuanld.maps.R;
import com.tuanld.maps.adapters.UserRecyclerAdapter;
import com.tuanld.maps.models.ClusterMarker;
import com.tuanld.maps.models.PolylineData;
import com.tuanld.maps.models.User;
import com.tuanld.maps.models.UserLocation;
import com.tuanld.maps.util.MyClusterManagerRenderer;
import com.tuanld.maps.util.ViewWeightAnimationWrapper;

import java.util.ArrayList;
import java.util.List;

import static com.tuanld.maps.Constants.MAPVIEW_BUNDLE_KEY;

public class UserListFragment extends Fragment implements OnMapReadyCallback,
        View.OnClickListener, GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnPolylineClickListener, UserRecyclerAdapter.UserListRecylerClick {

    private static final String TAG = "UserListFragment";
    private static final int LOCATION_UPDATE_INTERVAL = 3000;
    private static final int MAP_LAYOUT_STATE_CONTRACTED = 0;
    private static final int MAP_LAYOUT_STATE_EXPANDED = 1;
    private int mMapLayoutState = 0;

    //widgets
    private RecyclerView mUserListRecyclerView;
    private MapView mMapView;
    private RelativeLayout mMapContainer;

    //vars
    private ArrayList<User> mUserList = new ArrayList<>();
    private ArrayList<UserLocation> mUserLocation = new ArrayList<>();
    private UserRecyclerAdapter mUserRecyclerAdapter;
    private GoogleMap mGoogleMap;
    private LatLngBounds mMapBounds;
    private UserLocation mUserPosition;
    private ClusterManager mClusterManager;
    private MyClusterManagerRenderer mClusterManagerRenderer;
    private ArrayList<ClusterMarker> mClusterMarkers = new ArrayList<>();
    private ArrayList<PolylineData> mPolylineData = new ArrayList<>();
    private ArrayList<Marker> mTripMarkers = new ArrayList<>();

    private Handler mHandler = new Handler();
    private Runnable mRunnable;

    private GeoApiContext mGeoApiContext = null;
    private Marker mSelectedMarker = null;

    public static UserListFragment newInstance() {
        return new UserListFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUserList = getArguments().getParcelableArrayList(getString(R.string.intent_user_list));
            mUserLocation = getArguments().getParcelableArrayList(getString(R.string.intent_user_locations));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_list, container, false);
        mUserListRecyclerView = view.findViewById(R.id.user_list_recycler_view);
        mMapView = view.findViewById(R.id.user_list_map);
        mMapContainer = view.findViewById(R.id.map_container);

        view.findViewById(R.id.btn_full_screen_map).setOnClickListener(this);
        view.findViewById(R.id.btn_reset_map).setOnClickListener(this);

        initMap(savedInstanceState);
        initUserListRecyclerView();

        setUserPosition();
        return view;
    }

    private void removeTripMarkers() {
        for (Marker marker : mTripMarkers) {
            marker.remove();
        }
    }

    private void resetSelectedMarker() {
        if (mSelectedMarker != null) {
            mSelectedMarker.setVisible(true);
            mSelectedMarker = null;
            removeTripMarkers();
        }
    }

    public void zoomRoute(List<LatLng> lstLatLngRoute) {

        if (mGoogleMap == null || lstLatLngRoute == null || lstLatLngRoute.isEmpty()) return;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng latLngPoint : lstLatLngRoute)
            boundsBuilder.include(latLngPoint);

        int routePadding = 120;
        LatLngBounds latLngBounds = boundsBuilder.build();

        mGoogleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, routePadding),
                600,
                null
        );
    }

    //Tính toán đường đi
    private void calculateDirections(Marker marker) {
        Log.d(TAG, "calculateDirections: calculating directions.");
        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                marker.getPosition().latitude,
                marker.getPosition().longitude
        );
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);
        directions.alternatives(true);
        directions.origin(
                new com.google.maps.model.LatLng(
                        mUserPosition.getmGeoPoint().getLatitude(),
                        mUserPosition.getmGeoPoint().getLongitude()
                )
        );
        Log.d(TAG, "calculateDirections: destination: " + destination.toString());
        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: routes: " + result.routes[0].toString());
                Log.d(TAG, "calculateDirections: duration: " + result.routes[0].legs[0].duration);
                Log.d(TAG, "calculateDirections: distance: " + result.routes[0].legs[0].distance);
                Log.d(TAG, "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());
                addPolylinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.getMessage());

            }
        });
    }

    //Add lines to Map
    private void addPolylinesToMap(final DirectionsResult result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: result routes: " + result.routes.length);
                if (mPolylineData.size() > 0) {
                    for (PolylineData polylineData : mPolylineData) {
                        polylineData.getmPolyline().remove();
                    }
                    mPolylineData.clear();
                    mPolylineData = new ArrayList<>();
                }
                double duration = 999999999;
                for (DirectionsRoute route : result.routes) {
                    Log.d(TAG, "run: leg: " + route.legs[0].toString());
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());
                    List<LatLng> newDecodedPath = new ArrayList<>();
                    // This loops through all the LatLng coordinates of ONE polyline.
                    for (com.google.maps.model.LatLng latLng : decodedPath) {
//                        Log.d(TAG, "run: latlng: " + latLng.toString());
                        newDecodedPath.add(new LatLng(
                                latLng.lat,
                                latLng.lng
                        ));
                    }
                    Polyline polyline = mGoogleMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                    polyline.setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                    polyline.setClickable(true);
                    mPolylineData.add(new PolylineData(polyline, route.legs[0]));
                    //onPolylineClick(polyline);
                    double tempDuration = route.legs[0].duration.inSeconds;
                    if (tempDuration < duration) {
                        duration = tempDuration;
                        onPolylineClick(polyline);
                        zoomRoute(polyline.getPoints());
                    }
                    mSelectedMarker.setVisible(false);
                }
            }
        });
    }

    //Sau 3s cập nhật vị trí user 1 lần
    private void startUserLocationsRunnable() {
        Log.d(TAG, "startUserLocationsRunnable: starting runnable for retrieving updated locations.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                retrieveUserLocations();
                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates() {
        mHandler.removeCallbacks(mRunnable);
    }

    private void retrieveUserLocations() {
        Log.d(TAG, "retrieveUserLocations: retrieving location of all users in the chatroom.");
        try {
            for (final ClusterMarker clusterMarker : mClusterMarkers) {

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection(getString(R.string.collection_user_locations))
                        .document(clusterMarker.getmUser().getUser_id());

                userLocationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            final UserLocation updatedUserLocation = task.getResult().toObject(UserLocation.class);
                            // update the location
                            for (int i = 0; i < mClusterMarkers.size(); i++) {
                                try {
                                    if (mClusterMarkers.get(i).getmUser().getUser_id().equals(updatedUserLocation.getUser().getUser_id())) {
                                        LatLng updatedLatLng = new LatLng(
                                                updatedUserLocation.getmGeoPoint().getLatitude(),
                                                updatedUserLocation.getmGeoPoint().getLongitude()
                                        );
                                        mClusterMarkers.get(i).setmPosition(updatedLatLng);
                                        mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));
                                    }
                                } catch (NullPointerException e) {
                                    Log.e(TAG, "retrieveUserLocations: NullPointerException: " + e.getMessage());
                                }
                            }
                        }
                    }
                });
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query. Ending query." + e.getMessage());
        }

    }

    //Add Marker to Map
    private void addMapMarkers() {
        if (mGoogleMap != null) {
            resetMap();
            if (mClusterManager == null) {
                mClusterManager = new ClusterManager<ClusterMarker>(getActivity().getApplicationContext(), mGoogleMap);
            }
            if (mClusterManagerRenderer == null) {
                mClusterManagerRenderer = new MyClusterManagerRenderer(
                        getActivity(),
                        mGoogleMap,
                        mClusterManager
                );
                mClusterManager.setRenderer(mClusterManagerRenderer);
            }
            for (UserLocation userLocation : mUserLocation) {
                Log.d(TAG, "addMapMarkers: location: " + userLocation.getmGeoPoint().toString());
                try {
                    String snippet = "";
                    if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                        snippet = "This is you";
                    } else {
                        snippet = "Determine route to " + userLocation.getUser().getUsername() + "?";
                    }
                    int avatar = R.drawable.cartman_cop; // set the default avatar
                    try {
                        avatar = Integer.parseInt(userLocation.getUser().getAvatar());
                    } catch (NumberFormatException e) {
                        Log.d(TAG, "addMapMarkers: no avatar for " + userLocation.getUser().getUsername() + ", setting default.");
                    }
                    ClusterMarker newClusterMarker = new ClusterMarker(
                            new LatLng(userLocation.getmGeoPoint().getLatitude(), userLocation.getmGeoPoint().getLongitude()),
                            userLocation.getUser().getUsername(),
                            snippet,
                            avatar,
                            userLocation.getUser()
                    );
                    mClusterManager.addItem(newClusterMarker);
                    mClusterMarkers.add(newClusterMarker);

                } catch (NullPointerException e) {
                    Log.e(TAG, "addMapMarkers: NullPointerException: " + e.getMessage());
                }
            }
            mClusterManager.cluster();
            setCameraView();
        }
    }

    //Reset Map
    private void resetMap() {
        if (mGoogleMap != null) {
            mGoogleMap.clear();

            if (mClusterManager != null) {
                mClusterManager.clearItems();
            }

            if (mClusterMarkers.size() > 0) {
                mClusterMarkers.clear();
                mClusterMarkers = new ArrayList<>();
            }

            if (mPolylineData.size() > 0) {
                mPolylineData.clear();
                mPolylineData = new ArrayList<>();
            }
        }
    }

    private void setCameraView() {
        //Overall map view: 0.2* 0.2 = 0.04
        double bottomBoundary = mUserPosition.getmGeoPoint().getLatitude() - .1;
        double leftBoundary = mUserPosition.getmGeoPoint().getLongitude() - .1;

        double topBoundary = mUserPosition.getmGeoPoint().getLatitude() + .1;
        double rightBoundary = mUserPosition.getmGeoPoint().getLongitude() + .1;

        mMapBounds = new LatLngBounds(
                new LatLng(bottomBoundary, leftBoundary),
                new LatLng(topBoundary, rightBoundary)
        );
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMapBounds, 0));
    }

    private void setUserPosition() {
        for (UserLocation userLocation : mUserLocation) {
            if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                mUserPosition = userLocation;
            }

        }
    }

    private void initMap(Bundle savedInstanceState) {
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.map_api_key))
                    .build();
        }
    }


    private void initUserListRecyclerView() {
        mUserRecyclerAdapter = new UserRecyclerAdapter(mUserList, this);
        mUserListRecyclerView.setAdapter(mUserRecyclerAdapter);
        mUserListRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
        startUserLocationsRunnable();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        map.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        map.setMyLocationEnabled(true);
        //mGoogleMap = map;
        //setCameraView();
        mGoogleMap = map;
        mGoogleMap.setOnPolylineClickListener(this);
        addMapMarkers();
        mGoogleMap.setOnInfoWindowClickListener(this);

    }

    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
        stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_full_screen_map: {
                if (mMapLayoutState == MAP_LAYOUT_STATE_CONTRACTED) {
                    mMapLayoutState = MAP_LAYOUT_STATE_EXPANDED;
                    expandMapAnimation();
                } else if (mMapLayoutState == MAP_LAYOUT_STATE_EXPANDED) {
                    mMapLayoutState = MAP_LAYOUT_STATE_CONTRACTED;
                    contractMapAnimation();
                }
                break;
            }
            case R.id.btn_reset_map: {
                addMapMarkers();
                break;
            }
        }
    }

    //set fragment map to full screen
    private void expandMapAnimation() {
        ViewWeightAnimationWrapper mapAnimationWrapper = new ViewWeightAnimationWrapper(mMapContainer);
        ObjectAnimator mapAnimation = ObjectAnimator.ofFloat(mapAnimationWrapper,
                "weight",
                50,
                100);
        mapAnimation.setDuration(800);

        ViewWeightAnimationWrapper recyclerAnimationWrapper = new ViewWeightAnimationWrapper(mUserListRecyclerView);
        ObjectAnimator recyclerAnimation = ObjectAnimator.ofFloat(recyclerAnimationWrapper,
                "weight",
                50,
                0);
        recyclerAnimation.setDuration(800);

        recyclerAnimation.start();
        mapAnimation.start();
    }

    private void contractMapAnimation() {
        ViewWeightAnimationWrapper mapAnimationWrapper = new ViewWeightAnimationWrapper(mMapContainer);
        ObjectAnimator mapAnimation = ObjectAnimator.ofFloat(mapAnimationWrapper,
                "weight",
                100,
                50);
        mapAnimation.setDuration(800);

        ViewWeightAnimationWrapper recyclerAnimationWrapper = new ViewWeightAnimationWrapper(mUserListRecyclerView);
        ObjectAnimator recyclerAnimation = ObjectAnimator.ofFloat(recyclerAnimationWrapper,
                "weight",
                0,
                50);
        recyclerAnimation.setDuration(800);

        recyclerAnimation.start();
        mapAnimation.start();
    }

    //Show dialog
    @Override
    public void onInfoWindowClick(final Marker marker) {
        if (marker.getSnippet().equals("This is you")) {
            marker.hideInfoWindow();
        } else {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(marker.getSnippet())
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            resetSelectedMarker();
                            mSelectedMarker = marker;
                            calculateDirections(marker);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        }
    }

    @Override
    public void onPolylineClick(Polyline polyline) {
        int index = 0;
        for (PolylineData polylineData : mPolylineData) {
            index++;
            Log.d(TAG, "onPolylineClick: toString: " + polylineData.toString());
            if (polyline.getId().equals(polylineData.getmPolyline().getId())) {
                polylineData.getmPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.blue1));
                polylineData.getmPolyline().setZIndex(1);

                LatLng endLocation = new LatLng(
                        polylineData.getmLeg().endLocation.lat,
                        polylineData.getmLeg().endLocation.lng
                );

                Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                        .position(endLocation)
                        .title("Trip: #" + index)
                        .snippet("Duration: " + polylineData.getmLeg().duration));
                marker.showInfoWindow();
                mTripMarkers.add(marker);
            } else {
                polylineData.getmPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                polylineData.getmPolyline().setZIndex(0);
            }
        }

    }

    @Override
    public void onUserClicked(int position) {
        String selectedUserId = mUserList.get(position).getUser_id();
        for (ClusterMarker clusterMarker : mClusterMarkers) {
            if (selectedUserId.equals(clusterMarker.getmUser().getUser_id())) {
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(clusterMarker.getPosition().latitude, clusterMarker.getPosition().longitude)),
                        600, null);
                break;
            }
        }
    }
}



















