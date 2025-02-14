package edu.upenn.sas.archaeologyapp.ui;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.internal.ToolbarUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import edu.upenn.sas.archaeologyapp.R;
import edu.upenn.sas.archaeologyapp.models.PathElement;
import edu.upenn.sas.archaeologyapp.models.StringObjectResponseWrapper;
import edu.upenn.sas.archaeologyapp.models.DataEntryElement;
import edu.upenn.sas.archaeologyapp.services.DatabaseHandler;
import edu.upenn.sas.archaeologyapp.util.Constants;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.coords.UTMCoord;
import static edu.upenn.sas.archaeologyapp.R.id.map;

import static edu.upenn.sas.archaeologyapp.util.ExtraUtils.getSharedPreferenceString;
import static edu.upenn.sas.archaeologyapp.util.ExtraUtils.putString;
import static edu.upenn.sas.archaeologyapp.util.StaticSingletons.getRequestQueueSingleton;
import static edu.upenn.sas.archaeologyapp.services.UserAuthentication.getToken;
import static edu.upenn.sas.archaeologyapp.services.UserAuthentication.setToken;

import static edu.upenn.sas.archaeologyapp.services.VolleyStringWrapper.makeVolleyStringObjectRequest;
import static edu.upenn.sas.archaeologyapp.services.requests.MaterialRequest.materialRequest;
import static edu.upenn.sas.archaeologyapp.util.Constants.MATERIALS_URL;
import static edu.upenn.sas.archaeologyapp.util.Constants.globalWebServerURL;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * This activity shows the user the list of items presently in his bucket
 * @author Created by eanvith on 30/12/16.
 */
public class MainActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener
{
    // The shared preferences file name where we will store persistent app data
    public static final String PREFERENCES = "archaeological-survey-location-collector-preferences";
    private static int FINDS_MODE = 0, PATHS_MODE = 1, USER_MODE = 2;

    private Context context = this;
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 100, MY_PERMISSION_ACCESS_EXTERNAL_STORAGE = 200;
    // This int represents what we want to display (paths or finds). We start with finds.
    int displayMode = FINDS_MODE;
    // Reference to the list view
    ListView listView;
    // Reference to the list entry adapter for finds
//    BucketListEntryAdapter findsListEntryAdapter;

    NewBucketListEntryAdapter findsListEntryAdapter;
    // Reference to the list entry adapter for paths
    PathEntryAdapter pathsListEntryAdapter;
    // Reference to the swipe refresh layout
    SwipeRefreshLayout swipeRefreshLayout;
    // Reference to the bottom bar
    BottomNavigationView displayModeBar;
    // Reference to the Google map
    GoogleMap googleMap;
    private ViewTreeObserver.OnScrollChangedListener mOnScrollChangedListener;
    // Location manager for accessing the users location
    private LocationManager locationManager;
    // Listener with callbacks to get the users location
    private LocationListener locationListener;
    // Variables to store the users location data obtained from GPS, as a backup to the Reach data
    private Double GPSlatitude, GPSlongitude;
    // The timer used to periodically update the position
    private Timer positionUpdateTimer;
    // The text views for displaying latitude, longitude, altitude, and status values
    private TextView gridTextView, northingTextView, eastingTextView;
    // A request queue to handle python requests
    private String token;
    RequestQueue queue;


    /**
     * Change the appbar title
     * @param title - Change the appbar title
     */
    private void setAppBarTitle(String title){

        Toolbar toolbar = (Toolbar) findViewById(R.id.topAppBarMain);
        toolbar.setTitle(title);


    }

    private void changeButton(){
        if ( displayMode == FINDS_MODE){
            findViewById( R.id.addFindButton).setVisibility(View.VISIBLE);
            findViewById( R.id.recordPathButton).setVisibility(View.GONE);
           //
        }else if (displayMode == PATHS_MODE){
            findViewById( R.id.addFindButton).setVisibility(View.GONE);
            findViewById( R.id.recordPathButton).setVisibility(View.VISIBLE);
        }
    }


    /**
     * Activity launched
     * @param savedInstanceState - state from memory
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        queue = getRequestQueueSingleton(getApplicationContext());;

        super.onCreate(savedInstanceState);
        token = getToken(context);
        setContentView(R.layout.activity_main);
        initializeViews();
        initiateGPS();
        gridTextView = findViewById(R.id.data_entry_grid);
        northingTextView = findViewById(R.id.data_entry_northing);
        eastingTextView = findViewById(R.id.data_entry_easting);
        resetUTMLocation();
        // Setup the position updater
        positionUpdateTimer = new Timer();
        restartPositionUpdateTimer();
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(map);
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            /**
             * Map is ready
             * @param m - map button
             */
            @Override
            public void onMapReady(GoogleMap m)
            {
                googleMap = m;
                findsListEntryAdapter.setMap(m);
                pathsListEntryAdapter.setMap(m);
                try
                {
                    m.setMyLocationEnabled(true);
                }
                catch (SecurityException e)
                {
                    Toast.makeText(getApplicationContext(), "Location must be enabled", Toast.LENGTH_LONG).show();
                }
                m.getUiSettings().setMyLocationButtonEnabled(true);
                m.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    /**
                     * Camera was moved
                     * @param i - move code?
                     */
                    @Override
                    public void onCameraMoveStarted(int i)
                    {
                        disableSwipeRefresh();
                    }
                });
                googleMap.setOnCameraMoveCanceledListener(new GoogleMap.OnCameraMoveCanceledListener() {
                    /**
                     * Camera move cancelled
                     */
                    @Override
                    public void onCameraMoveCanceled()
                    {
                        enableSwipeRefresh();
                    }
                });
                populateDataFromLocalStore();
            }
        });
        cacheOptionsFromDatabase();
        changeButton();
    }

    /**
     * Initializes all the views and other layout components
     */
    private void initializeViews()
    {
        // Set the toolbar
        Toolbar toolbar = findViewById(R.id.topAppBarMain);
        setSupportActionBar(toolbar);
        // Set the bottom bar
        displayModeBar = findViewById(R.id.displayModeBar);
        displayModeBar.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            /**
             * Item selected on bar
             * @param item - selected item
             * @return Returns whether the selection was handled
             */
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item)
            {
                switch (item.getItemId())
                {
                    case R.id.navigation_finds:
                        // Clicked on finds button
                        displayMode = FINDS_MODE;

                        changeButton();
                        setAppBarTitle("Finds List");
                        listView.setAdapter(findsListEntryAdapter);
                        setTitle(R.string.title_activity_main);//Keep here for compatiability
                        populateDataFromLocalStore();
                        return true;
                    case R.id.navigation_paths:
                        // Clicked on paths button
                        displayMode = PATHS_MODE;
                        changeButton();
                        setAppBarTitle("Paths List");
                        listView.setAdapter(pathsListEntryAdapter);
                        setTitle(R.string.title_activity_paths);//Keep here for compatiability
                        populateDataFromLocalStore();
                        return true;
                    case R.id.logout:

                        setToken("disabled_string", context);
                        System.out.println("Disabled token");
                        MainActivity.super.startActivityUsingIntent(LoginActivity.class);
                        Toast.makeText(context, "Disabled token", Toast.LENGTH_SHORT).show();

                }
                return false;
            }
        });
        // Configure the new action button to handle clicks
        findViewById(R.id.fab_new).setOnClickListener(new View.OnClickListener() {
            /**
             * User clicked the add button
             * @param view - add button
             */
            @Override
            public void onClick(View view)
            {
                if (displayMode == FINDS_MODE)
                {
                    // Open DataEntryActivity to create a new find entry
                    startActivityUsingIntent(DataEntryActivity.class, false);
                }
                else if (displayMode == PATHS_MODE)
                {
                    // Open PathEntryActivity to create a new path entry
                    startActivityUsingIntent(PathEntryActivity.class, false);
                }
            }
        });
        // Configure the settings action button to handle clicks
        findViewById(R.id.fab_sync).setOnClickListener(new View.OnClickListener() {
            /**
             * User clicked the sync button
             * @param view - sync button
             */
            @Override
            public void onClick(View view)
            {
                startActivityUsingIntent(SyncActivity.class, false);
            }
        });
        // Store references to the list and list entry
//        listView = findViewById(R.id.main_activity_list_view);
        listView = findViewById(R.id.new_main_activity_list_view);
        findsListEntryAdapter = new NewBucketListEntryAdapter(this, R.layout.new_bucket_list_entry);
        pathsListEntryAdapter = new PathEntryAdapter(this, R.layout.paths_list_entry);
        // Default to listing the finds first
        listView.setAdapter(findsListEntryAdapter);
        // Configure the list items to handle clicks
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            /**
             * User clicked item in list
             * @param adapterView - container view
             * @param view - selected item
             * @param position - item's position
             * @param id - item's id
             */
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
            {
                if (displayMode == FINDS_MODE)
                {
                    // Open the data entry activity with fields pre-populated
                    DataEntryElement dataEntryElement = findsListEntryAdapter.getItem(position);
                    Bundle paramsToPass = new Bundle();
                    paramsToPass.putString(Constants.PARAM_KEY_ID, dataEntryElement.getID());
                    paramsToPass.putInt(Constants.PARAM_KEY_ZONE, dataEntryElement.getZone());
                    paramsToPass.putString(Constants.PARAM_KEY_HEMISPHERE, dataEntryElement.getHemisphere());
                    paramsToPass.putInt(Constants.PARAM_KEY_NORTHING, dataEntryElement.getNorthing());
                    paramsToPass.putDouble(Constants.PARAM_KEY_PRECISE_NORTHING, dataEntryElement.getPreciseNorthing());
                    paramsToPass.putInt(Constants.PARAM_KEY_EASTING, dataEntryElement.getEasting());
                    paramsToPass.putDouble(Constants.PARAM_KEY_PRECISE_EASTING, dataEntryElement.getPreciseEasting());
                    paramsToPass.putInt(Constants.PARAM_KEY_SAMPLE, dataEntryElement.getSample());
                    paramsToPass.putDouble(Constants.PARAM_KEY_LATITUDE, dataEntryElement.getLatitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_LONGITUDE, dataEntryElement.getLongitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_ALTITUDE, dataEntryElement.getAltitude());
                    paramsToPass.putString(Constants.PARAM_KEY_STATUS, dataEntryElement.getStatus());
                    paramsToPass.putDouble(Constants.PARAM_KEY_AR_RATIO, dataEntryElement.getARRatio());
                    paramsToPass.putStringArrayList(Constants.PARAM_KEY_IMAGES, dataEntryElement.getImagePaths());
                    paramsToPass.putString(Constants.PARAM_KEY_MATERIAL, dataEntryElement.getMaterial());
                    paramsToPass.putString(Constants.PARAM_KEY_CONTEXT_NUMBER, dataEntryElement.getContextNumber());

                    paramsToPass.putString(Constants.PARAM_KEY_COMMENTS, dataEntryElement.getComments());
                    paramsToPass.putString(Constants.PARAM_FIND_UUID, dataEntryElement.getFindUUID());
                    paramsToPass.putInt(Constants.PARAM_FIND_DELETED, dataEntryElement.getFindDeleted());
                    startActivityUsingIntent(DataEntryActivity.class, false, paramsToPass);
                }
                else if (displayMode == PATHS_MODE)
                {
                    // Open the paths entry activity with fields pre-populated
                    PathElement pathElement = pathsListEntryAdapter.getItem(position);
                    Bundle paramsToPass = new Bundle();
                    paramsToPass.putString(Constants.PARAM_KEY_TEAM_MEMBER, pathElement.getTeamMember());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_LATITUDE, pathElement.getBeginLatitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_LONGITUDE, pathElement.getBeginLongitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_ALTITUDE, pathElement.getBeginAltitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_LATITUDE, pathElement.getEndLatitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_LONGITUDE, pathElement.getEndLongitude());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_ALTITUDE, pathElement.getEndAltitude());
                    paramsToPass.putString(Constants.PARAM_KEY_HEMISPHERE, pathElement.getHemisphere());
                    paramsToPass.putInt(Constants.PARAM_KEY_ZONE, pathElement.getZone());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_EASTING, pathElement.getBeginEasting());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_NORTHING, pathElement.getBeginNorthing());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_EASTING, pathElement.getEndEasting());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_NORTHING, pathElement.getEndNorthing());
                    paramsToPass.putLong(Constants.PARAM_KEY_BEGIN_TIME, pathElement.getBeginTime());
                    paramsToPass.putLong(Constants.PARAM_KEY_END_TIME, pathElement.getEndTime());
                    paramsToPass.putString(Constants.PARAM_KEY_BEGIN_STATUS, pathElement.getBeginStatus());
                    paramsToPass.putString(Constants.PARAM_KEY_END_STATUS, pathElement.getEndStatus());
                    paramsToPass.putDouble(Constants.PARAM_KEY_BEGIN_AR_RATIO, pathElement.getBeginARRatio());
                    paramsToPass.putDouble(Constants.PARAM_KEY_END_AR_RATIO, pathElement.getEndARRatio());
                    startActivityUsingIntent(PathEntryActivity.class, false, paramsToPass);
                }
            }
        });
        // Get a reference for the swipe layout and set the listener
        swipeRefreshLayout = findViewById(R.id.main_activity_swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Populate the list with data from DB
        populateDataFromLocalStore();


        findViewById(R.id.addFindButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view)
            {
                MainActivity.super.startActivityUsingIntent(DataEntryActivity.class, false);
//
            }
        });
        findViewById(R.id.recordPathButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view)
            {
                MainActivity.super.startActivityUsingIntent(PathEntryActivity.class, false);
            }
        });



    }

    /**
     * Start the GPS
     */
    private void initiateGPS()
    {
        // Acquire a reference to the system Location Manager
        if (locationManager == null)
        {
            locationManager = (LocationManager) MainActivity.this.getSystemService(Context.LOCATION_SERVICE);
        }
        // Check if GPS is turned on. If not, prompt the user to turn it on.
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            Toast.makeText(MainActivity.this, "This app needs GPS on to work. Please turn it on and restart the app.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Define a listener that responds to location updates
        if (locationListener == null)
        {
            locationListener = new LocationListener() {
                /**
                 * User's location changed
                 * @param location - new location
                 */
                public void onLocationChanged(Location location)
                {
                    // Called when a new location is found by the GPS location provider.
                    updateGPSlocation(location);
                }

                /**
                 * GPS status changed
                 * @param provider - GPS provider
                 * @param status - GPS status
                 * @param extras - GPS data
                 */
                public void onStatusChanged(String provider, int status, Bundle extras)
                {
                }

                /**
                 * GPS was enabled
                 * @param provider - GPS provider
                 */
                public void onProviderEnabled(String provider)
                {
                }

                /**
                 * GPS was disabled
                 * @param provider - GPS provider
                 */
                public void onProviderDisabled(String provider)
                {
                }
            };
        }

        // Check if the user has granted permission for using GPS
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            // Ask user for permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[]
                    {android.Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_ACCESS_FINE_LOCATION);
        }
        else
        {
            // Register the listener with the Location Manager to receive location updates
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    /**
     * This function is called when the user swipes down to refresh the list
     */
    @Override
    public void onRefresh()
    {
        // Populate list with data from DB
        swipeRefreshLayout.setRefreshing(true);
        populateDataFromLocalStore();
        swipeRefreshLayout.setRefreshing(false);
    }

    /**
     * Function to populate the list with data available locally
     */
    private void populateDataFromLocalStore()
    {
        DatabaseHandler databaseHandler = new DatabaseHandler(this);
        if (displayMode == FINDS_MODE)
        {
            // Get data from DB
            int numrows = databaseHandler.getUnsyncedFindsRows().size();
            // Populate map markers
            if (googleMap != null)
            {
                googleMap.clear();
                for (DataEntryElement elem: databaseHandler.getUnsyncedFindsRows())
                {
                    String id = elem.getZone() + "." + elem.getHemisphere() + "." + elem.getNorthing()
                            + "." + elem.getEasting() + "." + elem.getSample();
                    googleMap.addMarker(new MarkerOptions().position(new LatLng(elem.getLatitude(), elem.getLongitude())).title(id));
                }
                // Set map center to last placed marker
                if (numrows > 0)
                {
                    DataEntryElement lastElem = databaseHandler.getUnsyncedFindsRows().get(numrows - 1);
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastElem.getLatitude(),
                            lastElem.getLongitude()),14));
                }
            }
            // Clear list and populate with data got from DB
            findsListEntryAdapter.clear();
            findsListEntryAdapter.addAll(databaseHandler.getUnsyncedFindsRows());
            findsListEntryAdapter.notifyDataSetChanged();
        }
        else if (displayMode == PATHS_MODE)
        {
            // Get data from DB
            int numrows = databaseHandler.getUnsyncedPathsRows().size();
            // Populate map markers and lines
            if (googleMap != null)
            {
                googleMap.clear();
                for (PathElement elem: databaseHandler.getUnsyncedPathsRows())
                {
                    // Add the path only if it's been completed
                    if (elem.getEndTime() != 0)
                    {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
                        String id = elem.getTeamMember() + "'s path, " + sdf.format(new Date(elem.getBeginTime()));
                        // Add the starting point
                        googleMap.addMarker(new MarkerOptions().position(new LatLng(elem.getBeginLatitude(),
                                elem.getBeginLongitude())).title(id));
                        // Add the end point and line
                        googleMap.addMarker(new MarkerOptions().position(new LatLng(elem.getEndLatitude(),
                                elem.getEndLongitude())).title(id));
                        googleMap.addPolyline(new PolylineOptions().add(new LatLng(elem.getBeginLatitude(),
                                elem.getBeginLongitude()), new LatLng(elem.getEndLatitude(), elem.getEndLongitude()))
                                .width(5).color(Color.RED));
                    }
                }
                // Set map center to last placed path marker
                if (numrows > 0)
                {
                    PathElement lastElem = databaseHandler.getUnsyncedPathsRows().get(numrows - 1);
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastElem.getBeginLatitude(), lastElem.getBeginLongitude()), 17));
                }
            }
            pathsListEntryAdapter.clear();
            pathsListEntryAdapter.addAll(databaseHandler.getUnsyncedPathsRows());
            pathsListEntryAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Enable refresh
     */
    private void enableSwipeRefresh()
    {
        swipeRefreshLayout.setEnabled(true);
    }

    /**
     * Disable refresh
     */
    private void disableSwipeRefresh()
    {
        swipeRefreshLayout.setEnabled(false);
    }

    /**
     * Update the GPS location details from the GPS stream, but don't update the items position data
     * @param location The location to be used
     */
    private void updateGPSlocation(Location location)
    {
        // Get the latitude, longitutde, altitude, and save it in the respective variables
        GPSlongitude = location.getLongitude();
        GPSlatitude = location.getLatitude();
    }

    /**
     * Set the UTM location
     */
    private void setUTMLocation()
    {
        if (GPSlatitude != null && GPSlongitude != null)
        {
            Angle lat = Angle.fromDegrees(GPSlatitude);
            Angle lon = Angle.fromDegrees(GPSlongitude);
            UTMCoord UTMposition = UTMCoord.fromLatLon(lat, lon);
            int zone = UTMposition.getZone();
            String hemisphere = UTMposition.getHemisphere().contains("North") ? "N" : "S";
            int northing = (int) Math.floor(UTMposition.getNorthing());
            int easting = (int) Math.floor(UTMposition.getEasting());
            gridTextView.setText(getString(R.string.string_frmt, zone + hemisphere));
            northingTextView.setText(String.valueOf(northing));
            eastingTextView.setText(String.valueOf(easting));
        }
    }

    /**
     * Clear UTM location
     */
    private void resetUTMLocation()
    {
        gridTextView.setText(R.string.blank_assignment);
        northingTextView.setText(R.string.blank_assignment);
        eastingTextView.setText(R.string.blank_assignment);
    }

    /**
     * Restart the position update timer using the global interval variable positionUpdateInterval
     */
    private void restartPositionUpdateTimer()
    {
        positionUpdateTimer.cancel();
        positionUpdateTimer = new Timer();
        positionUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            /**
             * Run the timer
             */
            @Override
            public void run()
            {
                // Need to run setUTMLocation() on an UI thread
                runOnUiThread(new Runnable() {
                    /**
                     * Set the UTM location
                     */
                    @Override
                    public void run()
                    {
                        setUTMLocation();
                    }
                });
            }
        }, 0, Constants.DEFAULT_POSITION_UPDATE_INTERVAL * 1000);
    }

    /**
     * Handle permissions request
     * @param requestCode - request code
     * @param permissions - requested permissions
     * @param grantResults - grant results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case MY_PERMISSION_ACCESS_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // Permission was granted by user
                    initiateGPS();
                }
                else
                {
                    // Comes here if permission for GPS was denied by user
                    // Show a toast to the user requesting that he allows permission for GPS use
                    Toast.makeText(this, R.string.location_permission_denied_prompt, Toast.LENGTH_LONG).show();
                }
                break;
            case MY_PERMISSION_ACCESS_EXTERNAL_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // Permission was granted by user
                    Toast.makeText(this, R.string.external_storage_permission_granted_prompt, Toast.LENGTH_LONG).show();
                }
                else
                {
                    // Comes here if permission for GPS was denied by user
                    // Show a toast to the user requesting that he allows permission for GPS use
                    Toast.makeText(this, R.string.external_storage_permission_denied_prompt, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void loadMaterialsJSONStringFromAssets(){

        try {
            InputStream inputStream = (getApplicationContext().getAssets().open("json/materials.json"));
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            putString("materialGeneralAPIResponse", new String(buffer), context);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Cache database options
     */
    private void cacheOptionsFromDatabase()
    {
        // Check to see what options are already saved on this device
        SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
        final boolean teamMemberResponsePreviouslyLoaded = settings.contains("teamMemberAPIResponse");
        final boolean materialGeneralResponsePreviouslyLoaded = settings.contains("materialGeneralAPIResponse");

        makeVolleyStringObjectRequest(globalWebServerURL + "/get_team_members", queue, new StringObjectResponseWrapper() {
            /**
             * Server response
             * @param response - server response
             */
            @Override
            public void responseMethod(String response)
            {
                if (!response.contains("Error"))
                {
                    // Save this team member as the default
                    SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("teamMemberAPIResponse", response);
                    editor.commit();
                }
                else if (!teamMemberResponsePreviouslyLoaded)
                {
                    Toast.makeText(getApplicationContext(), "Could not load team members: "+response, Toast.LENGTH_SHORT).show();
                }
            }

            /**
             * Server error
             * @param error - failure
             */
            @Override
            public void errorMethod(VolleyError error)
            {
                if (!teamMemberResponsePreviouslyLoaded)
                {
                    Toast.makeText(getApplicationContext(), "Could not load team members (Communication error): "
                            + error, Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
                }
            }
        });


        //We only load materials from local assets or via restFul
        String currentmaterialString = getSharedPreferenceString("materialGeneralAPIResponse", context);
        System.out.println(currentmaterialString);
        if (currentmaterialString == null || currentmaterialString.equals("") ){
            loadMaterialsJSONStringFromAssets();
            // materialRequest(MATERIALS_URL,getToken(context),queue,context,materialGeneralResponsePreviouslyLoaded);

        }




    }




    /**
     * Activity paused
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        // Stop listening to GPS, if still listening
        try
        {
            locationManager.removeUpdates(locationListener);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Activity resumed
     */
    @Override
    protected void onResume()
    {
        super.onResume();
        // Repopulate data
        populateDataFromLocalStore();
        // initiate GPS again
        initiateGPS();
    }
}