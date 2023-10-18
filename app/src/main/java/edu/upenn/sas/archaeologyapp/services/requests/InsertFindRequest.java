package edu.upenn.sas.archaeologyapp.services.requests;


import static edu.upenn.sas.archaeologyapp.ui.DataEntryActivity.getMaterialCategoryOptions;
import static edu.upenn.sas.archaeologyapp.util.Constants.DEFAULT_VOLLEY_TIMEOUT;


import android.content.Context;
import androidx.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A class that contains methods for making a request to upload a find onto the server.
 */
public class InsertFindRequest {

    /**
     * This method calls a request to upload a find onto the server.
     * @param URL: The URL to which this request is sent. It is created using getContextURL() in the same class
     * @param parametersObject: The object containing all the parameters to be sent to the server.
     * @param token: The token that must exist to authenticate the requests
     * @param queue: The request queue to which the created request is put to
     * @param successListener: The method that is going to get executed when the RESTful request returns successfully.
     * @param failureListener: The method that is going to get executed when the requset fails.
     */
    public static void insertFindRequest(String URL, JSONObject parametersObject , String token, RequestQueue queue,  Response.Listener<JSONObject> successListener,  Response.ErrorListener failureListener) {

        Request jsonArrayRequest = new JsonObjectRequest(Request.Method.POST, URL, parametersObject,successListener,
                failureListener ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Authorization", "Token " + token);
                return params;
            }
        };
        jsonArrayRequest.setRetryPolicy(new DefaultRetryPolicy(DEFAULT_VOLLEY_TIMEOUT,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(jsonArrayRequest);
    }

    /**
     * This method creates an JSONObject that contains all the necessary paramter for the insertion_find request.
     * It makes sure fields are not null and the data to be sent to the server is fine.
     *
     * Notice we have two steps to create such an object
     * 1. Checking the paramters are okay.
     * 2. Put all the paramters into a JSONobject
     * @param context The Context from which the method calls
     * @param utm_hemisphere The hemisphere of the find
     * @param utm_zone The zone of the find
     * @param area_utm_easting_meters The easting coordinate of the find
     * @param area_utm_northing_meters The northing coordinate of the find.
     * @param context_number The context_number of this find
     * @param material The material of the find
     * @param category The category of the material of the find
     * @param director_notes The comment and notes of the find
     * @return Returns a JSONObject containing all the parameters needed for the insertion find request.
     */
    public static @NonNull JSONObject createInsertMaterialParametersObject(Context context, @NonNull String utm_hemisphere, @NonNull int utm_zone, @NonNull int area_utm_easting_meters, @NonNull int area_utm_northing_meters, @NonNull int context_number, String material, String category, String director_notes) {

        //1. Checking the parameters are alright
        Objects.requireNonNull(utm_hemisphere);
        Objects.requireNonNull(utm_zone);
        Objects.requireNonNull(area_utm_easting_meters);
        Objects.requireNonNull(area_utm_northing_meters);
        Objects.requireNonNull(context_number);
        if ((material == null && category != null) || (material != null && category == null)){
            throw new RuntimeException("Material and Category are either both null or both non-null");
        }
        if (material != null && material.length()  > 255){
                throw new RuntimeException("Material cannot be a string with more than 255 characters");
        }
        if (category != null && category.length()  > 255){
                throw new RuntimeException("Category cannot be a string with more than 255 characters");
        }

        if (material != null && category != null){
            String[] materialCategoryOptions = getMaterialCategoryOptions(context);
            boolean legitMaterialCategory = false;
            for (int i = 0; i < materialCategoryOptions.length; i++){

                if ( materialCategoryOptions[i].equals(material +" : " + category )){
                    legitMaterialCategory = true;
                    break;
                }
            }
            if (legitMaterialCategory == false){
                throw new RuntimeException("The material and category is wrong");
            }
        }
        //2. Packing the parameters into a nice JSON Object.

        JSONObject parametersObj = new JSONObject();
        try {
            parametersObj.put("utm_hemisphere",utm_hemisphere);
            parametersObj.put("utm_zone",utm_zone);
            parametersObj.put("area_utm_easting_meters",area_utm_easting_meters);
            parametersObj.put("area_utm_northing_meters",area_utm_northing_meters);
            parametersObj.put("context_number",context_number);
            parametersObj.put("material",material == null? JSONObject.NULL: material);
            parametersObj.put("category",category == null? JSONObject.NULL: category);
            parametersObj.put("director_notes",director_notes == null? JSONObject.NULL: director_notes);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return parametersObj;
    }



}
