package edu.upenn.sas.archaeologyapp.ui;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.upenn.sas.archaeologyapp.R;
import edu.upenn.sas.archaeologyapp.models.PathElement;
import edu.upenn.sas.archaeologyapp.models.StringObjectResponseWrapper;
import edu.upenn.sas.archaeologyapp.models.DataEntryElement;
import edu.upenn.sas.archaeologyapp.services.DatabaseHandler;
import static edu.upenn.sas.archaeologyapp.services.VolleyStringWrapper.makeVolleyStringObjectRequest;
import static edu.upenn.sas.archaeologyapp.util.Constants.globalWebServerURL;
/**
 * This activity is responsible for uploading all the records from the local database onto a server.
 * @author eanvith, Colin Roberts, Christopher Besser.
 */
public class SyncActivity extends AppCompatActivity
{
    // The button the user clicks to initiate the sync process
    Button syncButton;
    private HashMap<String, Integer> imageNumbers = new HashMap<>();
    // A list of records populated from the local database, that need to be uploaded onto the server
    ArrayList<DataEntryElement> elementsToUpload;
    // The index of the find currently being uploaded
    int uploadIndex, totalItems, pathUploadIndex, totalPaths;
    // A list of paths populated from the local database, that need to be uploaded onto the server
    ArrayList<PathElement> pathsToUpload;
    // A database helper class object that enables fetching of records from the local database
    DatabaseHandler databaseHandler;
    // A request queue to handle python requests
    RequestQueue queue;
    /**
     * Activity is launched
     * @param savedInstanceState - saved state from memory
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        queue = Volley.newRequestQueue(this);
        // Initialize the database helper class object, and read in the records from the local database
        databaseHandler = new DatabaseHandler(this);
        elementsToUpload = databaseHandler.getUnsyncedFindsRows();
        pathsToUpload = databaseHandler.getUnsyncedPathsRows();
        totalItems = elementsToUpload.size();
        uploadIndex = 0;
        totalPaths = pathsToUpload.size();
        pathUploadIndex = 0;
        // Attach a click listener to the sync button, and trigger the sync process on click of the button
        syncButton = findViewById(R.id.sync_button_sync_activity);
        syncButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Sync records
             * @param v - sync button
             */
            @Override
            public void onClick(View v)
            {
                // totalItems is 0, so nothing to sync
                if (uploadIndex >= totalItems && pathUploadIndex >= totalPaths)
                {
                    Toast.makeText(SyncActivity.this, "There are no records to sync.", Toast.LENGTH_SHORT).show();
                }
                // Disable the sync button while the sync is in progress
                syncButton.setEnabled(false);
                // Start uploading unsynced items
                uploadFinds();
                // Start uploading unsynced paths
                uploadPaths();
            }
        });
    }

    /**
     * Upload a find to the database
     */
    private void uploadFinds()
    {
        if (uploadIndex < totalItems)
        {
            final DataEntryElement find = elementsToUpload.get(uploadIndex);
            String zone = Integer.toString(find.getZone());
            String hemisphere = find.getHemisphere();
            String easting = Double.toString(find.getPreciseEasting());
            String northing = Double.toString(find.getPreciseNorthing());
            String sample = Integer.toString(find.getSample());
            String contextEasting = Integer.toString(find.getEasting());
            String contextNorthing = Integer.toString(find.getNorthing());
            String latitude = Double.toString(find.getLatitude());
            String longitude = Double.toString(find.getLongitude());
            String altitude = Double.toString(find.getAltitude());
            String status = find.getStatus();
            String material = find.getMaterial();
            String ARratio = Double.toString(find.getARRatio());
            String locationTimestamp = Double.toString(find.getCreatedTimestamp());
            String comments = find.getComments();
            String encoding = "";
            List<String> imagePaths = find.getImagePaths();
            List<String> imageNames = parseImageNames(imagePaths);
            List<String> imageBase64 = encodeImages(imagePaths);
            StringObjectResponseWrapper imageResponseWrapper = getImageResponseWrapper();
            try
            {
                encoding = URLEncoder.encode(comments, "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
            makeVolleyStringObjectRequest(globalWebServerURL + "/insert_find?zone=" + zone
                            + "&hemisphere=" + hemisphere + "&easting=" + easting + "&northing=" + northing
                            + "&contextEasting=" + contextEasting + "&contextNorthing=" + contextNorthing
                            + "&find=" + sample + "&latitude=" + latitude + "&longitude=" + longitude
                            + "&altitude=" + altitude + "&status=" + status + "&material=" + material
                            + "&comments=" + encoding + "&ARratio=" + ARratio + "&timestamp=" + locationTimestamp,
                    queue, new StringObjectResponseWrapper() {
                /**
                 * Response received
                 * @param response - database response
                 */
                @Override
                public void responseMethod(String response)
                {
                    Log.v("Sync", response);
                    if (!response.contains("Error"))
                    {
                        databaseHandler.setFindSynced(find);
                        ArrayList<String> paths = elementsToUpload.get(uploadIndex).getImagePaths();
                        String key = hemisphere + "." + zone + "." + contextEasting + "." + contextNorthing + "." + find;
                        if (imageNumbers.get(key) == null)
                        {
                            imageNumbers.put(key, 0);
                        }
                        for (String path: paths)
                        {
                            imageNumbers.put(key, imageNumbers.get(key) + 1);
                            String newDir = Environment.getExternalStorageDirectory().toString()
                                    + "/Archaeology/" + hemisphere + "/" + zone + "/" + contextEasting
                                    + "/" + contextNorthing + "/" + sample + "/photos/field/";
                            File dir = new File(newDir);
                            if (!dir.exists())
                            {
                                dir.mkdirs();
                            }
                            String newPath = newDir + "/" + imageNumbers.get(key) + ".JPG";
                            File oldImage = new File(path);
                            File newImage = new File(newPath);
                            if (!oldImage.renameTo(newImage))
                            {
                                Log.v("Moving Files", "Failed to move " + oldImage.getAbsolutePath()
                                        + " to " + newImage.getAbsolutePath());
                            }
                            Log.v("Moving Files", oldImage.getAbsolutePath() + " renamed to "
                                    + newImage.getAbsolutePath());
                        }
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Upload failed: " + response,
                                Toast.LENGTH_SHORT).show();
                    }
                    // Upload the next find
                    uploadIndex++;
                    uploadFinds();
                }

                /**
                 * Connection failed
                 * @param error - failure
                 */
                @Override
                public void errorMethod(VolleyError error)
                {
                    Toast.makeText(getApplicationContext(), "Upload failed (Communication error): " + error,
                            Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
                }
            });
            //then send images
            for (int i = 0; i < imageNames.size(); i++) {
                //imageNames.get(i) and imageBase64.get(i)
                String currentName = imageNames.get(i);
                String currentImageBase64 = imageBase64.get(i);
                StringRequest stringRequest = new StringRequest(Request.Method.POST, globalWebServerURL + "/insert_find_image",
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.d("Res++", response);
                            }
                        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                })
                {
                    @Override
                    protected Map<String, String> getParams() throws AuthFailureError {
                        Map<String, String> params = new HashMap<>();
                        params.put("zone", zone);
                        params.put("hemisphere", hemisphere);
                        params.put("easting", easting);
                        params.put("northing", northing);
                        params.put("find", sample);
                        params.put("imageName", currentName);
                        params.put("imageBase64", currentImageBase64);
                        Log.d("cool", String.valueOf(currentImageBase64.length()));
                        return params;
                    }
                }; //end of defining current POST request
                queue.add(stringRequest);
            }//end of for loop
        }
        else
        {
            Toast.makeText(SyncActivity.this, "Done syncing finds", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Upload a path to the database
     */
    private void uploadPaths()
    {
        if (pathUploadIndex < totalPaths)
        {
            final PathElement path = pathsToUpload.get(pathUploadIndex);
            String teamMember = path.getTeamMember();
            String beginLatitude = Double.toString(path.getBeginLatitude());
            String beginLongitude = Double.toString(path.getBeginLongitude());
            String beginAltitude = Double.toString(path.getBeginAltitude());
            String beginStatus = path.getBeginStatus();
            String beginARRatio =  Double.toString(path.getBeginARRatio());
            String endLatitude =  Double.toString(path.getEndLatitude());
            String endLongitude = Double.toString(path.getEndLongitude());
            String endAltitude = Double.toString(path.getEndAltitude());
            String endStatus =  path.getEndStatus();
            String endARRatio = Double.toString(path.getEndARRatio());
            String hemisphere = path.getHemisphere();
            String zone = Integer.toString(path.getZone());
            String beginNorthing = Double.toString(path.getBeginNorthing());
            String beginEasting = Double.toString(path.getBeginEasting());
            String endNorthing = Double.toString(path.getEndNorthing());
            String endEasting = Double.toString(path.getEndEasting());
            String beginTime = Double.toString(path.getBeginTime());
            String endTime = Double.toString(path.getEndTime());
            String encoding = "";
            try
            {
                encoding = URLEncoder.encode(teamMember, "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
            makeVolleyStringObjectRequest(globalWebServerURL + "/insert_path?teamMember=" + encoding
                            + "&hemisphere=" + hemisphere + "&zone=" + zone + "&beginEasting=" + beginEasting
                            + "&beginNorthing=" + beginNorthing + "&endEasting=" + endEasting + "&endNorthing="
                            + endNorthing + "&beginLatitude=" + beginLatitude + "&beginLongitude=" + beginLongitude
                            + "&beginAltitude=" + beginAltitude + "&beginStatus=" + beginStatus + "&beginARRatio="
                            + beginARRatio + "&endLatitude=" + endLatitude + "&endLongitude=" + endLongitude
                            + "&endAltitude=" + endAltitude + "&endStatus=" + endStatus + "&endARRatio="
                            + endARRatio + "&beginTime=" + beginTime + "&endTime=" + endTime, queue,
                    new StringObjectResponseWrapper() {
                /**
                 * Response received
                 * @param response - database response
                 */
                @Override
                public void responseMethod(String response)
                {
                    System.out.println(response);
                    if (!response.contains("Error"))
                    {
                        databaseHandler.setPathSynced(path);
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Upload failed: " + response, Toast.LENGTH_SHORT).show();
                    }
                    pathUploadIndex++;
                    uploadPaths();
                }

                /**
                 * Connection failed
                 * @param error - failure
                 */
                @Override
                public void errorMethod(VolleyError error)
                {
                    Toast.makeText(getApplicationContext(), "Upload unsuccessful (Communication error): " + error,
                            Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
                }
            });
        }
        else
        {
            Toast.makeText(SyncActivity.this, "Done syncing paths", Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> parseImageNames(List<String> imagePaths) {
        List<String> imageNames = new ArrayList<>();
        for (String imagePath: imagePaths) {
            Log.d("parseparse++", imagePath);
            String[] parsedResult = imagePath.split("/");
            imageNames.add(parsedResult[parsedResult.length - 1]);  //name is the last one
        }
        return imageNames;
    }

    private List<String> encodeImages(List<String> imagePaths) {
        List<String> encodedImages = new ArrayList<>();
        for (String imagePath: imagePaths) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                encodedImages.add(imageToString(bitmap));
        }
        return encodedImages;
    }

    private String imageToString(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        byte[] imgBytes = output.toByteArray();
        return android.util.Base64.encodeToString(imgBytes, android.util.Base64.NO_WRAP);
    }

    private StringObjectResponseWrapper getImageResponseWrapper() {
        return new StringObjectResponseWrapper() {
            /**
             * Response received
             * @param response - database response
             */
            @Override
            public void responseMethod(String response)
            {
                Log.v("Sync", response);
                if (!response.contains("Error"))
                {
                   Log.d("image uploaded", response);
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "Upload failed: " + response,
                            Toast.LENGTH_SHORT).show();
                }
            }

            /**
             * Connection failed
             * @param error - failure
             */
            @Override
            public void errorMethod(VolleyError error)
            {
                Toast.makeText(getApplicationContext(), "Upload image failed (Communication error): " + error,
                        Toast.LENGTH_SHORT).show();
                error.printStackTrace();
            }
        };
    }
}
