package edu.upenn.sas.archaeologyapp.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.upenn.sas.archaeologyapp.services.LocationCollector;
import edu.upenn.sas.archaeologyapp.R;
import edu.upenn.sas.archaeologyapp.models.DataEntryElement;
import edu.upenn.sas.archaeologyapp.services.DatabaseHandler;
import edu.upenn.sas.archaeologyapp.util.Constants;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.coords.UTMCoord;

import static edu.upenn.sas.archaeologyapp.util.ExtraUtils.getSharedPreferenceString;
import static edu.upenn.sas.archaeologyapp.util.StaticSingletons.getRequestQueueSingleton;
import static edu.upenn.sas.archaeologyapp.services.UserAuthentication.getToken;
import static edu.upenn.sas.archaeologyapp.services.requests.ContextNumbersRequest.contextJSONArray2ContextStrArray;
import static edu.upenn.sas.archaeologyapp.services.requests.ContextNumbersRequest.contextNumbersRequest;
import static edu.upenn.sas.archaeologyapp.services.requests.ContextNumbersRequest.getContextURL;
import static edu.upenn.sas.archaeologyapp.util.Constants.DEFAULT_POSITION_UPDATE_INTERVAL;
import static edu.upenn.sas.archaeologyapp.util.Constants.DEFAULT_REACH_HOST;
import static edu.upenn.sas.archaeologyapp.util.Constants.DEFAULT_REACH_PORT;

import com.android.volley.RequestQueue;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * The Activity where the user enters all the data
 *
 * @author eanvith, Colin Roberts, and Christopher Besser
 */
public class DataEntryActivity extends BaseActivity {
    private boolean liveUpdatePosition = true, deleteImages = false;
    // Variables to store the users location data obtained from the Reach
    private Double latitude, longitude, altitude, ARRatio, preciseEasting, preciseNorthing;
    private String status, hemisphere;
    // Int constant used to determine if GPS permission was granted or denied
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 100;
    // Int constant used to determine if External Storage permission was granted or denied
    private final int MY_PERMISSION_ACCESS_EXTERNAL_STORAGE = 200;
    private static final int SELECT_IMAGE = 1, CAMERA_REQUEST = 2;
    // The shared preferences file name where we will store persistent app data
    public static final String PREFERENCES = "archaeological-survey-location-collector-preferences";
    // The text views for displaying latitude, longitude, altitude, and status values
    private TextView latitudeTextView, longitudeTextView, altitudeTextView, statusTextView, gridTextView,
            northingTextView, eastingTextView, sampleTextView, GPSConnectionTextView, reachConnectionTextView;
    // The linear layout for displaying the images captured/selected by the user
    private GridLayout imageContainer;
    // The paths where the images are saved
    ArrayList<String> photoPaths = new ArrayList<>();
    // The spinner for displaying the dropdown of materials
    Spinner materialsDropdown;
    // The spinner for displaying the dropdown of context numbers
    Spinner contextNumberDropdown;

    // The text box where the user can enter comments
    EditText commentsEditText;

    private Integer zone, northing, easting, sample;
    private Uri photoURI = null;
    private LocationCollector locationCollector;
    // The timestamp of the find's location
    private long timestamp;
    //The context of the this activity
    private Context context;
    //The uuid of of the find saved in the server
    private String find_uuid;
    //1 indicates the file is deleted, other values indicates the file is not deleted
    private int find_deleted;
    //
    private RequestQueue requestQueue;

    boolean pressing = false;
    private  Map<String, ArrayList<String>> materialCategories;

    private void getJSONArrayFromMaterialsJSONString() throws JSONException {
//        1. Parse the jsonarray into string
//        2. Make it a map, key is the material, value is an array of categories
        String materialString = getSharedPreferenceString("materialGeneralAPIResponse", context);
        JSONArray jA   = new JSONArray(materialString);
        Map<String, ArrayList<String>> hsmap = new HashMap<String, ArrayList<String>>();
        for(int i = 0; i < jA.length(); i++){
            String currentRowMaterial = ((JSONObject)jA.get(i)).getString("material");
            String currentRowCategory = ((JSONObject)jA.get(i)).getString("category");

            if (!hsmap.containsKey(currentRowMaterial)){
                ArrayList temp = new ArrayList<String>();
                temp.add(currentRowCategory);
                hsmap.put(currentRowMaterial, temp);
            }else{
                hsmap.get(currentRowMaterial).add(currentRowCategory);
            }
        }

        materialCategories = (hsmap);

    }

    private void setCategoriesDropdown(String material){
        //Notice that category is always set after materials.
        if(materialCategories != null && materialCategories.containsKey(material)){
            String [] categories =  materialCategories.get(material).toArray((new String[materialCategories.get(material).size()]));
            ArrayAdapter arrayAdapter = new ArrayAdapter(context, R.layout.listitem, categories);
            AutoCompleteTextView editText =  findViewById(R.id.dddw);
            editText.setText(categories[0], false);
            editText.setAdapter(arrayAdapter);
        }else{
            Toast.makeText(DataEntryActivity.this, "This material doesn't have categories", Toast.LENGTH_LONG).show();

        }
    }

    private void initializeMaterialsDropdown(){

                 //Material injection
        if (materialCategories!=null ){
            String [] materials =  materialCategories.keySet().toArray(new String[materialCategories.size()]);
            ArrayAdapter arrayAdapter = new ArrayAdapter(context, R.layout.listitem, materials);
            AutoCompleteTextView editText =  findViewById(R.id.bbbwdwdwbbb);
            editText.setText(materials[0], false);
            editText.setAdapter(arrayAdapter);
        }else{
            Toast.makeText(DataEntryActivity.this, "Materials and categories are not set yet", Toast.LENGTH_LONG).show();

        }

    }





    /**
     * Activity created
     *
     * @param savedInstanceState - state from memory
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;

        requestQueue = getRequestQueueSingleton(getApplicationContext());;
        setContentView(R.layout.content_data_entry);
        initializeViews();

        // Load persistent app data from shared preferences
        SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
        String reachHost = settings.getString("reachHost", DEFAULT_REACH_HOST);
        String reachPort = settings.getString("reachPort", DEFAULT_REACH_PORT);
        Integer positionUpdateInterval = settings.getInt("positionUpdateInterval", DEFAULT_POSITION_UPDATE_INTERVAL);
        // Initialize the locationCollector
        locationCollector = new LocationCollector(DataEntryActivity.this, reachHost, reachPort, positionUpdateInterval) {
            /**
             * Receive location broadcast
             * @param _latitude - latitude
             * @param _longitude - longitude
             * @param _altitude - altitude
             * @param _status - location status
             * @param _ARRatio - AR ratio
             */
            @Override
            public void broadcastLocation(double _latitude, double _longitude, double _altitude, String _status, Double _ARRatio) {
                if (liveUpdatePosition) {
                    setLocationDetails(_latitude, _longitude, _altitude, _status, _ARRatio);
                }
            }

            /**
             * Broadcast GPS status
             * @param status - GPS status
             */
            @Override
            public void broadcastGPSStatus(String status) {
                setGPSStatus(status);
            }

            /**
             * Broadcast reach status
             * @param status - reach status
             */
            public void broadcastReachStatus(String status) {
                setReachStatus(status);
            }
        };

        try {
            getJSONArrayFromMaterialsJSONString();
            initializeMaterialsDropdown();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Activity ended
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        locationCollector.cancelPositionUpdateTimer();
    }

    /**
     * Inflate options
     *
     * @param menu - options menu
     * @return Returns whether inflation succeeded
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Initializes all the views and other layout components
     */
    private void initializeViews() {
        // Set the toolbar


//        Toolbar toolbar = findViewById(R.id.toolbar_data_entry);
//        setSupportActionBar(toolbar);
        // Configure up button to go back to previous activity
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Get references to the latitude, longitude, altitude, and status text views
        latitudeTextView = findViewById(R.id.data_entry_lat_text);
        longitudeTextView = findViewById(R.id.data_entry_lng_text);
        altitudeTextView = findViewById(R.id.data_entry_alt_text);
        statusTextView = findViewById(R.id.data_entry_status_text);
        gridTextView = findViewById(R.id.data_entry_grid);
        northingTextView = findViewById(R.id.data_entry_northing);
        eastingTextView = findViewById(R.id.data_entry_easting);
        sampleTextView = findViewById(R.id.data_entry_sample);

        GPSConnectionTextView = findViewById(R.id.GPSConnection);
        reachConnectionTextView = findViewById(R.id.reachConnection);
        GPSConnectionTextView.setText(String.format(getResources().getString(R.string.GPSConnection), getString(R.string.blank_assignment)));
        reachConnectionTextView.setText(String.format(getResources().getString(R.string.reachConnection), getString(R.string.blank_assignment)));
        // Get reference to the image container
        //imageContainer = findViewById(R.id.data_entry_image_container);
        imageContainer = findViewById( R.id.new_data_entry_image_container);
        // Get reference to the comments edit text
        commentsEditText = findViewById(R.id.data_entry_comment_text_view);
        // Configure switch handler for update gps switch
        ToggleButton updateGPSButton = findViewById(R.id.data_entry_update_gps);

        updateGPSButton.setChecked(true);
        updateGPSButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            /**
             * Button checked
             * @param buttonView - pressed button
             * @param isChecked - whether the button is checked
             */
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                liveUpdatePosition = isChecked;
                if (liveUpdatePosition) {
                    resetUTMLocation();
                } else {
                    // Ensure we only update the sample #, UTM position if the user pressed the button
                    // (otherwise this would happen on activity load)
                    if (buttonView.isPressed() || pressing == true) {
                        pressing = false;
                        setUTMLocation();
                    }
                }
            }
        });
        // Configure click handler for show GPS location on map button
        findViewById(R.id.data_entry_show_map).setOnClickListener(new View.OnClickListener() {
            /**
             * User clicked map
             * @param v - map button
             */
            @Override
            public void onClick(View v) {
                // Check if a location is set
                if (latitude != null && longitude != null && altitude != null) {
                    // Open maps with saved latitude and longitude
                    String loc = "geo:0,0?q=" + latitude + "," + longitude + "(" + getString(R.string.location_map_pin_label) + ")" + "&z=16";
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(loc));
                    startActivity(mapIntent);
                } else {
                    // No GPS location currently set, show user message
                    Toast.makeText(DataEntryActivity.this, R.string.location_not_set, Toast.LENGTH_LONG).show();
                }
            }
        });
        // Configure click handler for opening gallery and allowing the user to select an image
//        findViewById(R.id.data_entry_open_gallery).setOnClickListener(new View.OnClickListener() {
        findViewById(R.id.galleryButton).setOnClickListener(new View.OnClickListener() {

            /**
             * User clicked gallery
             * @param v - gallery button
             */
            @Override
            public void onClick(View v) {
                // Create an intent for selecting an image, and start that activity with SELECT_IMAGE requestCode
                Intent chooseIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                chooseIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                chooseIntent.setType("image/*");
                startActivityForResult(chooseIntent, SELECT_IMAGE);
            }
        });

        findViewById(R.id.gps_text).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String sampleText= (String)sampleTextView.getText();

                if(sampleText == null || sampleText.equals("")){
                    Toast.makeText(DataEntryActivity.this, "Still locating", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(DataEntryActivity.this, sampleText, Toast.LENGTH_SHORT).show();
                    pressing = true;
                    updateGPSButton.performClick();

                }


            }
        });

        // Configure click handler for opening camera and allowing the user to take a picture
        findViewById(R.id.data_entry_open_camera).setOnClickListener(new View.OnClickListener() {
//        findViewById(R.id.cameraeButton).setOnClickListener(new View.OnClickListener() {
            /**
             * User clicked camera
             * @param v - camera button
             */
            @Override
            public void onClick(View v) {
                // Check if user has given permission to write to external storage (required to save
                // image captured by camera app) If not, request for the permission
                if (!(ContextCompat.checkSelfPermission(DataEntryActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                    ActivityCompat.requestPermissions(DataEntryActivity.this, new String[]
                            {Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSION_ACCESS_EXTERNAL_STORAGE);
                    return;
                }
                File outputFromCamera = null;
                try {
                    // Image file to be passed to camera app. The camera app saves captured image in this file
                    outputFromCamera = createImageFile();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Open a camera app to capture images, if available
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                    Context context = getApplicationContext();
                    photoURI = FileProvider.getUriForFile(context, context.getPackageName()
                            + ".my.package.name.provider", outputFromCamera);
                    Log.v("Camera", photoURI.toString());
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                } else {
                    Toast.makeText(DataEntryActivity.this, R.string.camera_app_not_found, Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Configure click handler for show GPS location on map button
        findViewById(R.id.data_entry_sample).setOnClickListener(new View.OnClickListener() {
            /**
             * User clicked show GPS
             * @param v - show GPS button
             */
            @Override
            public void onClick(View v) {
                if (!liveUpdatePosition) {
                    setNewSampleNum();
                }
            }
        });

        // Configure the materials dropdown menu
        // Load the team member API response from the saved preferences

        String materialGeneralOptions[] = getMaterialCategoryOptions( context);


        materialsDropdown = findViewById(R.id.data_entry_materials_drop_down);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<String> materialsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, materialGeneralOptions);
        // Specify the layout to use when the list of choices appears
        materialsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        materialsDropdown.setAdapter(materialsAdapter);
        materialsDropdown.setSelection(0);
        prePopulateFields();
        if (hemisphere != null && zone != null && easting != null && northing != null) {
            contextNumbersRequest(getContextURL(hemisphere, zone, easting, northing), getToken(context), requestQueue, response -> {
                String[] previousContexts = contextJSONArray2ContextStrArray(response);
                int max = 0;
                for (int i = 0; i < previousContexts.length; i++) {
                    int current_context_value = Integer.valueOf(previousContexts[i]);
                    if (current_context_value > max) {
                        max = current_context_value;
                    }
                }
                String[] extendedContextOptions = Arrays.copyOf(previousContexts, previousContexts.length + 1);
                extendedContextOptions[previousContexts.length] = (max + 1) + " : new";
                setUpContextNumberSelect(extendedContextOptions);

            }, error -> {

            });
        }

        EditText materialEdit = (EditText)findViewById(R.id.bbbwdwdwbbb);
        materialEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Whatsup bro;");
                setCategoriesDropdown(s.toString());
            }

        });


    }



    /**
     * Get the materials from the saved sharedPreferences.
     * @param context - The context of the activity
     * @return a list of strings of the available materials.
     */
    @NonNull
    public static String[] getMaterialCategoryOptions(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCES, 0);
        String materialGeneralAPIResponse = settings.getString("materialGeneralAPIResponse", null);
        String materialCategoryOptions[] = parseMaterialGeneralAPIResponse(materialGeneralAPIResponse, context);
        return materialCategoryOptions;
    }
    /**
     * User clicked show GPS
     * @param context - The context of the activity
     * @return a list of strings of the available materials.
     */
    public static String[] parseMaterialGeneralAPIResponse(String materialGeneralAPIResponse, Context context) {
        ArrayList<String> parsedStrings;
        try {
            parsedStrings = new ArrayList<>();
            JSONArray parsedResponseJsonArray = new JSONArray(materialGeneralAPIResponse);
            for (int i = 0; i < parsedResponseJsonArray.length(); i++) {
                JSONObject obj = parsedResponseJsonArray.getJSONObject(i);
                parsedStrings.add(obj.getString("material") + " : " + obj.getString("category"));
            }

        } catch (JSONException e) {
            parsedStrings = new ArrayList<>();
            String defaultMaterial = context.getString(R.string.default_material_general);
            String defaultCategory = context.getString(R.string.default_material_category);
            parsedStrings.add(defaultMaterial +" : " + defaultCategory);
        }
        Object[] parsedStringsObjectArray = parsedStrings.toArray();
        return Arrays.copyOf(parsedStringsObjectArray, parsedStringsObjectArray.length, String[].class); // Arrays.stream(parsedStrings.toArray()).toArray(String[]::new);
    }



    /**
     * Check if any parameters were passed to this activity, and pre populate the data if required
     */
    private void prePopulateFields() {
        String id = getIntent().getStringExtra(Constants.PARAM_KEY_ID);
        // If null, it means nothing was passed
        if (id == null) {
            deleteImages = true;
            return;
        }
        // Populate the UTM position details, if present
        find_uuid = getIntent().getStringExtra(Constants.PARAM_FIND_UUID);
        find_deleted = getIntent().getIntExtra(Constants.PARAM_FIND_DELETED, Integer.MIN_VALUE);
        zone = getIntent().getIntExtra(Constants.PARAM_KEY_ZONE, Integer.MIN_VALUE);
        hemisphere = getIntent().getStringExtra(Constants.PARAM_KEY_HEMISPHERE);
        northing = getIntent().getIntExtra(Constants.PARAM_KEY_NORTHING, Integer.MIN_VALUE);
        preciseNorthing = getIntent().getDoubleExtra(Constants.PARAM_KEY_PRECISE_NORTHING, Double.NEGATIVE_INFINITY);
        easting = getIntent().getIntExtra(Constants.PARAM_KEY_EASTING, Integer.MIN_VALUE);
        preciseEasting = getIntent().getDoubleExtra(Constants.PARAM_KEY_PRECISE_EASTING, Double.NEGATIVE_INFINITY);
        sample = getIntent().getIntExtra(Constants.PARAM_KEY_SAMPLE, Integer.MIN_VALUE);
        if (zone != Integer.MIN_VALUE && hemisphere != null && northing != Integer.MIN_VALUE
                && easting != Integer.MIN_VALUE && sample != Integer.MIN_VALUE) {
            gridTextView.setText(getString(R.string.string_frmt, zone + hemisphere));
            northingTextView.setText(String.valueOf(northing));
            eastingTextView.setText(String.valueOf(easting));
            sampleTextView.setText(String.valueOf(sample));


        } else {
            resetUTMLocation();
        }
        // Populate the latitude, longitude and altitude, if present
        double passedLatitude = getIntent().getDoubleExtra(Constants.PARAM_KEY_LATITUDE, Double.MIN_VALUE);
        double passedLongitude = getIntent().getDoubleExtra(Constants.PARAM_KEY_LONGITUDE, Double.MIN_VALUE);
        double passedAltitude = getIntent().getDoubleExtra(Constants.PARAM_KEY_ALTITUDE, Double.MIN_VALUE);
        String passedStatus = getIntent().getStringExtra(Constants.PARAM_KEY_STATUS);
        double passedARRatio = getIntent().getDoubleExtra(Constants.PARAM_KEY_AR_RATIO, Double.MIN_VALUE);
        if (!(passedLatitude == Double.MIN_VALUE || passedLongitude == Double.MIN_VALUE
                || passedAltitude == Double.MIN_VALUE || passedStatus == null)) {
            setLocationDetails(passedLatitude, passedLongitude, passedAltitude, passedStatus, passedARRatio);
        }
        // Pause the coordinate fetches
        liveUpdatePosition = false;
        ToggleButton updateGPSButton = findViewById(R.id.data_entry_update_gps);
        updateGPSButton.setChecked(false);
        // Populate the image, if present
        ArrayList<String> passedPaths = getIntent().getStringArrayListExtra(Constants.PARAM_KEY_IMAGES);
        if (!passedPaths.isEmpty()) {
            photoPaths = passedPaths;

            populateImagesWithPhotoPaths();
        }
        // Populate the material, if present
        String passedMaterial = getIntent().getStringExtra(Constants.PARAM_KEY_MATERIAL);

        if (passedMaterial != null) {
            // Search the dropdown for a matching material, and set to that if found
            for (int i = 0; i < materialsDropdown.getCount(); i++) {
                if (materialsDropdown.getItemAtPosition(i).toString().equalsIgnoreCase(passedMaterial)) {
                    materialsDropdown.setSelection(i);
                }
            }
        }

        // Populate the material, if present


        String passedContextNumber = getIntent().getStringExtra(Constants.PARAM_KEY_CONTEXT_NUMBER);

        if (passedContextNumber != null && contextNumberDropdown != null) {
            // Search the dropdown for a matching context number, and set to that if found
            for (int i = 0; i < contextNumberDropdown.getCount(); i++) {
                if (contextNumberDropdown.getItemAtPosition(i).toString().equalsIgnoreCase(passedContextNumber)) {
                    contextNumberDropdown.setSelection(i);
                }
            }
        }


        // Populate the comments, if present
        String passedComments = getIntent().getStringExtra(Constants.PARAM_KEY_COMMENTS);
        if (passedComments != null) {
            commentsEditText.setText(passedComments);
        }
        // Add delete button below submit button, change submit button text
        View deleteButton = findViewById(R.id.data_entry_delete_button);
        deleteButton.setVisibility(View.VISIBLE);
        Button submitButton = findViewById(R.id.data_entry_submit_button);
        submitButton.setText(getString(R.string.confirm_changes));
    }

    /**
     * Handle activity result
     *
     * @param requestCode - request code
     * @param resultCode  - result code
     * @param data        - activity data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check if the user action was success
        if (resultCode == Activity.RESULT_OK) {
            try {
                // If user selected image from gallery
                if (requestCode == SELECT_IMAGE) {
                    // The user's photo app does not support multi-select
                    ClipData imageData = data.getClipData();
                    if (imageData == null) {
                        Uri uri = data.getData();
                        // Add this image to the bitmap arraylist
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        // Try saving this bitmap
                        if (!saveToFile(bitmap)) {
                            throw new Exception(getString(R.string.save_failed_exception));
                        }
                    }
                    // Get the multiple images returned within a ClipData object
                    else {
                        for (int i = 0; i < imageData.getItemCount(); i++) {
                            ClipData.Item image = imageData.getItemAt(i);
                            Uri uri = image.getUri();
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            // Try saving this bitmap
                            if (!saveToFile(bitmap)) {
                                throw new Exception(getString(R.string.save_failed_exception));
                            }
                        }
                    }
                    // Display the selected images

                    populateImagesWithPhotoPaths();
                }
                // If user captured image with camera
                else if (requestCode == CAMERA_REQUEST) {
                    // Display the image captured (and don't clear the current photos)

                    populateImagesWithPhotoPaths();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, R.string.save_failed_message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Load photos
     */
    private void populateImagesWithPhotoPaths() {
        // Empty the current photos
        imageContainer.removeAllViews();
        // Populate the appropriate photos
        for (int i = 0; i < photoPaths.size(); i++) {
            ImageView image = new ImageView(this);
            String img = photoPaths.get(i);
            image.setLayoutParams(new GridView.LayoutParams(185, 185));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setPadding(5, 5, 5, 5);
            image.setScaleType(ImageView.ScaleType.MATRIX);
            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(img, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;
            // Determine how much to scale down the image
            int scaleFactor = Math.min(photoW / 185, photoH / 185);
            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inPurgeable = true;
            image.setImageBitmap(BitmapFactory.decodeFile(img, bmOptions));
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Do you want to delete this picture?").setCancelable(true)
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        /**
                         * User confirmed delete
                         * @param dialog - alert dialog
                         * @param id - item id
                         */
                        public void onClick(final DialogInterface dialog, final int id) {
                            imageContainer.removeView(image);
                            photoPaths.remove(img);
                            dialog.cancel();
                            new File(img).delete();
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        /**
                         * User clicked cancel
                         * @param dialog - alert dialog
                         * @param id - button id
                         */
                        public void onClick(final DialogInterface dialog, final int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            image.setOnLongClickListener(new View.OnLongClickListener() {
                /**
                 * User held press on image
                 * @param v - image
                 * @return Returns whether the event was handled
                 */
                public boolean onLongClick(View v) {
                    Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    vb.vibrate(30);
                    alert.show();
                    return true;
                }
            });
            // Adds the view to the layout
            imageContainer.addView(image);
        }
    }

    /**
     * Create an image file using the current timestamp
     *
     * @return Returns the new image file
     */
    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_.JPG";
        // Use the apps storage
        File storageDir = new File(Environment.getExternalStorageDirectory().toString() + "/Archaeology");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        Log.v("Camera", storageDir.getAbsolutePath());
        // Create the image file with required name, extension type and storage path
        File image = null;
        try {
            String path = Environment.getExternalStorageDirectory() + "/Archaeology/" + imageFileName;
            Log.v("Camera", path);
            image = new File(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        photoPaths.add(image.getAbsolutePath());
        return image;
    }

    /**
     * Set up the select that contains all the context numbers the user can select
     * for this find.
     * @param contextOptions a list of strings of the context numbers available
     */

    private void setUpContextNumberSelect(String[] contextOptions){


        contextNumberDropdown = findViewById(R.id.data_entry_context_select_dropdown);


        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<String> contextNumberAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, contextOptions);
        // Specify the layout to use when the list of choices appears
        contextNumberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        String originalSelection = null;
        if (contextNumberDropdown.getSelectedItem() != null){
            originalSelection = contextNumberDropdown.getSelectedItem().toString();
        }
        contextNumberDropdown.setAdapter(contextNumberAdapter);

        if (originalSelection != null){
            // Search the dropdown for a matching context number, and set to that if found
            for (int i = 0; i < contextNumberDropdown.getCount(); i++) {
                if (contextNumberDropdown.getItemAtPosition(i).toString().equalsIgnoreCase(originalSelection)) {
                    contextNumberDropdown.setSelection(i);
                }
            }
        }
        //We want the default to be the last non new element of the drop down .
        else if (contextNumberDropdown.getCount() > 1){
            contextNumberDropdown.setSelection(contextNumberDropdown.getCount()-2);
        }
    }

    /**
     * Set UTM location
     */

    private void setUTMLocation() {
        if (latitude != null && longitude != null) {
            DatabaseHandler databaseHandler = new DatabaseHandler(this);
            Angle lat = Angle.fromDegrees(latitude);
            Angle lon = Angle.fromDegrees(longitude);
            UTMCoord UTMposition = UTMCoord.fromLatLon(lat, lon);

            zone = UTMposition.getZone();
            hemisphere = UTMposition.getHemisphere().contains("North") ? "N" : "S";
            preciseNorthing = UTMposition.getNorthing();
            northing = (int) Math.floor(preciseNorthing);
            preciseEasting = UTMposition.getEasting();
            easting = (int) Math.floor(preciseEasting);
            sample = databaseHandler.getLastSampleFromBucket(zone, hemisphere, northing, easting) + 1;
            gridTextView.setText(getString(R.string.string_frmt, zone + hemisphere));
            northingTextView.setText(String.valueOf(northing));
            eastingTextView.setText(String.valueOf(easting));
            sampleTextView.setText(String.valueOf(sample));
            timestamp = (new Date()).getTime();
            //Here, call the request to get the context information for this locatiuon
            prePopulateFields();
            contextNumbersRequest(getContextURL(hemisphere, zone, easting,northing), getToken(context), requestQueue, response->{
                String [] previousContexts = contextJSONArray2ContextStrArray(response);
                int max = 0;
                for (int i = 0; i < previousContexts.length; i++){
                    int current_context_value = Integer.valueOf(previousContexts[i]);
                    if (current_context_value > max){
                        max = current_context_value;
                    }
                }
                String [] extendedContextOptions =  Arrays.copyOf(previousContexts, previousContexts.length + 1);
                extendedContextOptions[previousContexts.length] = (max + 1) +" : new";
                setUpContextNumberSelect(extendedContextOptions);

            }, error -> {

            });

        }
    }

    /**
     * Clear UTM location
     */
    private void resetUTMLocation() {
        gridTextView.setText(R.string.blank_assignment);
        northingTextView.setText(R.string.blank_assignment);
        eastingTextView.setText(R.string.blank_assignment);
        sampleTextView.setText(R.string.blank_assignment);
    }

    /**
     * Set the required variables and text views once we get location from previous activity
     *
     * @param passedLatitude  - The latitude to be set
     * @param passedLongitude - The longitude to be set
     * @param passedAltitude  The altitude to be set
     * @param passedStatus    - item status
     * @param passedARRatio   - AR ratio
     */
    private void setLocationDetails(double passedLatitude, double passedLongitude, double passedAltitude,
                                    String passedStatus, Double passedARRatio) {
        // Get the latitude, longitude, altitude, and save it in the respective variables
        longitude = passedLongitude;
        latitude = passedLatitude;
        altitude = passedAltitude;
        status = passedStatus;
        ARRatio = passedARRatio;
        // Update the text views
        latitudeTextView.setText(String.format(getResources().getString(R.string.latitude), latitude));
        longitudeTextView.setText(String.format(getResources().getString(R.string.longitude), longitude));
        altitudeTextView.setText(String.format(getResources().getString(R.string.altitude), altitude));
        statusTextView.setText(String.format(getResources().getString(R.string.status), status));
    }

    /**
     * Request permissions result
     *
     * @param requestCode  - requested permission code
     * @param permissions  - granted permissions
     * @param grantResults - grants
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_ACCESS_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.location_permission_denied_prompt, Toast.LENGTH_LONG).show();
                }
                break;
            case MY_PERMISSION_ACCESS_EXTERNAL_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted by user
                    Toast.makeText(this, R.string.external_storage_permission_granted_prompt, Toast.LENGTH_LONG).show();
                } else {
                    // Comes here if permission for GPS was denied by user
                    // Show a toast to the user requesting that he allows permission for GPS use
                    Toast.makeText(this, R.string.external_storage_permission_denied_prompt, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    /**
     * App paused
     */
    @Override
    protected void onPause() {
        super.onPause();
        locationCollector.pause();
    }

    /**
     * App resumed
     */
    @Override
    protected void onResume() {
        super.onResume();
        locationCollector.resume();
    }

    /**
     * User selected option
     *
     * @param item - option selected
     * @return Returns whether the selection was handled
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Save data when back arrow on action bar is pressed
                saveData();
                return false;
            case R.id.settings:
                // Create and open a settings menu dialog box
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                LayoutInflater inflater = getLayoutInflater();
                View layout = inflater.inflate(R.layout.connection_settings_dialog, null);
                EditText reachHostTextView = layout.findViewById(R.id.reach_host);
                EditText reachPortTextView = layout.findViewById(R.id.reach_port);
                EditText positionUpdateIntervalTextView = layout.findViewById(R.id.position_update_interval);
                dialog.setTitle(getString(R.string.connection_settings));
                SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
                String reachHost = settings.getString("reachHost", DEFAULT_REACH_HOST);
                String reachPort = settings.getString("reachPort", DEFAULT_REACH_PORT);
                Integer positionUpdateInterval = settings.getInt("positionUpdateInterval", DEFAULT_POSITION_UPDATE_INTERVAL);
                reachHostTextView.setText(reachHost, TextView.BufferType.EDITABLE);
                reachPortTextView.setText(reachPort, TextView.BufferType.EDITABLE);
                positionUpdateIntervalTextView.setText(String.valueOf(positionUpdateInterval), TextView.BufferType.EDITABLE);
                dialog.setCancelable(true);
                dialog.setNegativeButton("Done", new DialogInterface.OnClickListener() {
                    /**
                     * User approved
                     * @param dialog - alert dialog
                     * @param id - done button id
                     */
                    public void onClick(DialogInterface dialog, int id) {
                        // Set the new host and port
                        String reachHost = reachHostTextView.getText().toString();
                        String reachPort = reachPortTextView.getText().toString();
                        Integer positionUpdateInterval = Integer.parseInt(positionUpdateIntervalTextView.getText().toString());
                        // Reset the socket connection
                        locationCollector.resetReachConnection(reachHost, reachPort);
                        // Save these new values to shared preferences
                        SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString("reachHost", reachHost);
                        editor.putString("reachPort", reachPort);
                        editor.putInt("positionUpdateInterval", positionUpdateInterval);
                        editor.commit();
                        // Restart the timer
                        locationCollector.resetPositionUpdateInterval(positionUpdateInterval);
                        // Close the dialog
                        dialog.cancel();
                    }
                });
                dialog.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
                    /**
                     * User cancelled
                     * @param dialog - alert dialog
                     * @param id - cancel button id
                     */
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                dialog.setView(layout);
                dialog.show();
                return false;
        }
        return true;
    }

    /**
     * User submitted find
     *
     * @param v - submit button
     */
    public void submitButtonPressed(View v) {
        // We only save changes if location info and image are present. Check if location info is set
        if (zone == null || hemisphere == null || northing == null || preciseNorthing == null ||
                sample == null || easting == null || preciseEasting == null || latitude == null || longitude == null) {
            Toast.makeText(DataEntryActivity.this, "You did not establish a fixed location. Please try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Check if at least one image is set
        else if (photoPaths.isEmpty()) {

            Toast.makeText(DataEntryActivity.this, "You need to include pictures to add a new item.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        saveData();
        deleteImages = false;
        onBackPressed();
    }

    /**
     * User deleted find
     *
     * @param v - delete button
     */
    public void deleteButtonPressed(View v) {
        DatabaseHandler databaseHandler = new DatabaseHandler(this);
        databaseHandler.setFindDeleted(getElement()); //Used to be setFindSynched
        //This isn't good. We shoujld not set it as synched as
        for (String path : photoPaths) {
            //Here we may have to also delete the images from the database.
            new File(path).delete();
        }
        onBackPressed();
    }

    /**
     * User pressed back button
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // True if the state was not passed from the calling activity
        if (deleteImages) {
            for (String path : photoPaths) {
                new File(path).delete();
            }
        }
        // Disconnect from GPS
        locationCollector.pause();
    }

    /**
     * Set GPS status
     *
     * @param status - GPS status
     */
    private void setGPSStatus(String status) {
        GPSConnectionTextView.setText(String.format(getResources().getString(R.string.GPSConnection), status));
    }

    /**
     * Set reach status
     *
     * @param status - reach status
     */
    private void setReachStatus(String status) {
        reachConnectionTextView.setText(String.format(getResources().getString(R.string.reachConnection), status));
    }

    /**
     * Update sample number
     */
    private void setNewSampleNum() {
        // Create and open a settings menu dialog box
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Set new sample #");
        SharedPreferences settings = getSharedPreferences(PREFERENCES, 0);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText sampleBox = new EditText(this);
        sampleBox.setHint("Sample");
        sampleBox.setSingleLine();
        layout.addView(sampleBox);
        sampleBox.setText(String.valueOf(sample), TextView.BufferType.EDITABLE);
        dialog.setCancelable(true);
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            /**
             * User cancelled
             * @param dialog - alert window
             * @param id - cancel button id
             */
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        dialog.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            /**
             * User approved
             * @param dialog - alert window
             * @param id - confirm button id
             */
            public void onClick(DialogInterface dialog, int id) {
                // Set the new sample #
                sample = Integer.valueOf(sampleBox.getText().toString());
                sampleTextView.setText(String.valueOf(sample));
                // Close the dialog
                dialog.cancel();
            }
        });
        dialog.setView(layout);
        dialog.show();
    }

    /**
     * Get item
     *
     * @return Returns the item
     */
    private DataEntryElement getElement() {
        // Get uuid from intent extras if this activity was opened for existing bucket entry
        String id = getIntent().getStringExtra(Constants.PARAM_KEY_ID);
        // If this is a new entry, check and generate a new uuid
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        // Get the material and the comment
        String material = materialsDropdown.getSelectedItem().toString();
        String contextNumber = contextNumberDropdown.getSelectedItem().toString();
        String comment = commentsEditText.getText().toString();
        return new DataEntryElement(id, latitude, longitude, altitude, status, ARRatio, photoPaths,
                material, contextNumber,comment, timestamp, timestamp, zone, hemisphere,
                northing, preciseNorthing, easting, preciseEasting, sample, false , find_uuid, find_deleted);
    }

    /**
     * Save all the data entered in the form
     */
    private void saveData() {
        DataEntryElement list[] = new DataEntryElement[1];
        list[0] = getElement();
        // Save the dataEntryElement to DB
        DatabaseHandler databaseHandler = new DatabaseHandler(this);

        databaseHandler.addFindsRows(list);
    }

    /**
     * Reduce the size of image if it is greater than maxWidth and maxHeight
     *
     * @param image The image to be resized
     * @return Resized image
     */
    private static Bitmap resize(Bitmap image) {
        int maxHeight = 1200;
        int maxWidth = 1800;
        float actualWidth = image.getWidth();
        float actualHeight = image.getHeight();
        float imgRatio = actualWidth / actualHeight;
        float maxRatio = (float) maxWidth / (float) maxHeight;
        // Resize only if image is bigger than maxHeight or maxWidth
        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            // Calculate dimensions of resized image
            if (imgRatio < maxRatio) {
                // adjust width according to maxHeight
                imgRatio = maxHeight / actualHeight;
                actualWidth = imgRatio * actualWidth;
                actualHeight = maxHeight;
            } else if (imgRatio > maxRatio) {
                // adjust height according to maxWidth
                imgRatio = maxWidth / actualWidth;
                actualHeight = imgRatio * actualHeight;
                actualWidth = maxWidth;
            } else {
                actualHeight = maxHeight;
                actualWidth = maxWidth;
            }
            // Create the resized image
            image = Bitmap.createScaledBitmap(image, (int) actualWidth, (int) actualHeight, true);
        }
        return image;
    }

    /**
     * Function to save bitmap to a file and store the path in photoPath
     *
     * @param bmp The BITMAP to be saved
     * @return true if saved, false otherwise
     */
    boolean saveToFile(Bitmap bmp) {
        FileOutputStream out = null;
        try {
            // Create an image file where the BITMAP will be saved
            File outputImageFile = createImageFile();
            // Resize and write the image to the file using JPEG compression
            out = new FileOutputStream(outputImageFile, false);
            bmp = resize(bmp);
            bmp.compress(Bitmap.CompressFormat.JPEG, 50, out);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                // Close the output stream to the file, if opened
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}