package com.uberpets.mobile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;

import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;


import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import afu.org.checkerframework.checker.nullness.qual.NonNull;

public class  DriverHome
        extends AppCompatActivity
        implements
            NavigationView.OnNavigationItemSelectedListener,
            OnMapReadyCallback,
            TravelRequestFragment.OnFragmentInteractionListener,
            DriverFollowUpTravel.OnFragmentInteractionListener
{

    private GoogleMap mMap;
    private Location currentLocation;
    private LatLng mockLocation;
    private Marker currentPositionMarker;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private static float ZOOM_VALUE = 14.0f;
    private static double MOVEMENT_SPEED = 0.001;
    private int locationRequestCode = 1000;
    private Fragment notification;

    private Socket mSocket;
    private final String TAG_CONNECTION_SERVER = "CONNECTION_SERVER";
    private final String EVENT_NOTIFICATION_TRAVEL = "NOTIFICATION_OF_TRAVEL";
    private final String EVENT_CONNECTION = "message";
    private Emitter.Listener mListenerConnection;
    private Emitter.Listener mListenerNotificationTravel;

    //private final String URL = "https://young-wave-26125.herokuapp.com";
    private final String URL = "http://192.168.43.175:8081";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //is used to obtain user's location, with this our app no needs to manually manage connections
        //to Google Play Services through GoogleApiClient
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermission();

        {
            try {
                mSocket = IO.socket(URL);
                Log.d(TAG_CONNECTION_SERVER,"io socket succes");
                connectToServer();
                listenNotificaciónTravel();
            } catch (URISyntaxException e) {
                Log.d(TAG_CONNECTION_SERVER,"io socket failure");
            }
        }
    }

    @Override
    public void onBackPressed() {
        /*finishPreviusFragments();
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.driver_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public void requestPermission(){
        //check if user has granted location permission,
        // its necessary to use mFusedLocationProviderClient
        if (ActivityCompat.checkSelfPermission(DriverHome.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(DriverHome.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},locationRequestCode);
        }else{
            fetchLastLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String [] permissions, @NonNull int [] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1000: {
                // Si se cancela la solicitud, las matrices de resultados están vacías.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchLastLocation();
                }
            }
        }
    }

    public void fetchLastLocation() {
        //check if user has granted location permission,
        // its necessary to use mFusedLocationProviderClient
        if (ActivityCompat.checkSelfPermission(DriverHome.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(DriverHome.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }else{
            // obtain the last location and save in task, that's Collection's Activities
            Task<Location> task = mFusedLocationProviderClient.getLastLocation();
            // add object OnSuccessListener, when the connection is established and the location is fetched
            task.addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        currentLocation = location;
                        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                                .findFragmentById(R.id.map);
                        mapFragment.getMapAsync(DriverHome.this);
                    }
                }
            });
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            if (googleMap != null) {
                Log.d("INFO", "GOOGLE GOOD LOADED");
                mMap = googleMap;
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                mockLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                int height = 32;
                int width = 64;
                BitmapDrawable bitmapdraw = (BitmapDrawable) getResources().getDrawable(R.drawable.car);
                Bitmap b = bitmapdraw.getBitmap();
                Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false);

                //MarkerOptions are used to create a new Marker.You can specify location, title etc with MarkerOptions
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(latLng)
                        .title("Estas Acá")
                        .icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
                        .anchor(0.5f, 0.5f);

                //Adding the created the marker on the map
                currentPositionMarker = mMap.addMarker(markerOptions);

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, ZOOM_VALUE));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ERROR", "GOOGLE MAPS NOT LOADED");
        }
    }

    public boolean popFragment() {
        boolean isPop = false;

        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.upper_section_fragment);

        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            isPop = true;
            getSupportFragmentManager().popBackStackImmediate();
        }

        return isPop;
    }

    public void finishPreviusFragments() {
        if (!popFragment()) {
            finish();
        }
    }

    public void replaceFragment(Fragment fragment, boolean addToBackStack) {

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();

        if (addToBackStack) {
            transaction.addToBackStack(null);

        } else {
            getSupportFragmentManager().popBackStack(null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);

        }
        transaction.replace(R.id.upper_section_fragment, fragment);
        transaction.commit();
        getSupportFragmentManager().executePendingTransactions();
    }

    private void removeUpperSectionFragment(){
        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.upper_section_fragment);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.remove(fragment);
        transaction.commit();
    }

    public void moveLocationUp(android.view.View view){
        moveLocation(MOVEMENT_SPEED,0);
    }

    public void moveLocationDown(android.view.View view){
        moveLocation(-MOVEMENT_SPEED,0);
    }

    public void moveLocationLeft(android.view.View view){
        moveLocation(0,-MOVEMENT_SPEED);
    }

    public void moveLocationRight(android.view.View view){
        moveLocation(0,MOVEMENT_SPEED);
    }

    public void moveLocation(double latitude, double longitude){
        Location prevLocation = new Location("");
        prevLocation.setLatitude(mockLocation.latitude);
        prevLocation.setLongitude(mockLocation.longitude);

        mockLocation = new LatLng(mockLocation.latitude + latitude, mockLocation.longitude + longitude);

        Location newLocation = new Location("");
        newLocation.setLongitude(mockLocation.longitude);
        newLocation.setLatitude(mockLocation.latitude);

        currentPositionMarker.setPosition(mockLocation);

        Float bearing = prevLocation.bearingTo(newLocation);

        currentPositionMarker.setRotation(bearing - 270);

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mockLocation, ZOOM_VALUE));
    }


    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onAttachFragment(Fragment fragment){

    }

    public void showNewTravelNotification(android.view.View view) {
        replaceFragment(new TravelRequestFragment(),false);
    }

    public void rejectTravel(android.view.View view){
        //TODO: mandar notificacion al server
        removeUpperSectionFragment();
    }

    public void acceptTravel(android.view.View view){
        //TODO: conexion con el servidor
        replaceFragment(DriverFollowUpTravel.newInstance("",""), true);
    }

    public void cancelOngoingTravel(android.view.View view){
        //TODO: show pop-up "are you sure"
        removeUpperSectionFragment();
    }

    public void finishTravel(android.view.View view){
        Intent intent = new Intent(this, DriverFinalScreen.class);
        startActivity(intent);
    }


    /* BEGIN OF SOCKET CONNECTION*/

    public void connectToServer(){
        mSocket.connect();
        mSocket.on(EVENT_CONNECTION, mListenerConnection = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        Log.d(TAG_CONNECTION_SERVER, "Established Connection");
                    }
                });
            }
        });
        mSocket.emit("ROL","DRIVER");
    }


    //listen if arrive message that driver arrived to user
    public void listenNotificaciónTravel() {
        mSocket.on(EVENT_NOTIFICATION_TRAVEL, mListenerNotificationTravel = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];

                        //hay q corregir el fragment seguro rompe
                        //finishPreviusFragments();
                        replaceFragment(new TravelRequestFragment(),false);
                        /*View f = findViewById(R.id.requestTravelFragment);
                        f.setAlpha(1);*/
                    }
                });
            }
        });
    }

    /* END OF SOCKET CONNECTION*/


    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("FINISH", mListenerConnection);
        mSocket.off("FINISH", mListenerNotificationTravel);
    }

}
