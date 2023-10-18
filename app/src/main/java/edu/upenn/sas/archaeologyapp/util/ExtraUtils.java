package edu.upenn.sas.archaeologyapp.util;

import static edu.upenn.sas.archaeologyapp.util.Constants.PREFERENCES;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
    These are miscellaneous classes and functions temporarily put here for convenience.
 */
public class ExtraUtils {
    @FunctionalInterface
    public interface InjectableFunc {
        void apply();
    }

    public static void putString(String key, String value, Context context){
        SharedPreferences settings = context.getSharedPreferences(PREFERENCES, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public static String getSharedPreferenceString(String key, Context context){
        SharedPreferences settings = context.getSharedPreferences(PREFERENCES, 0);
        return settings.getString(key, null);
    }

    public static class ImagePathBucketIDPair{
        private String imagePath;
        private String bucketID;
        public ImagePathBucketIDPair(String imagePath,  String bucketID){
            this.imagePath = imagePath;
            this.bucketID = bucketID;
        }

        public String getImagePath(){
            return this.imagePath;
        }

        public String getBucketID(){
            return this.bucketID;
        }
    }
    public static class ServerUUIDBucketIDPair{
        private String serverUUID;
        private String bucketID;
        public ServerUUIDBucketIDPair(String imagePath,  String bucketID){
            this.serverUUID = imagePath;
            this.bucketID = bucketID;
        }

        public String getServerUUID(){
            return this.serverUUID;
        }

        public String getBucketID(){
            return this.bucketID;
        }
    }



    public static Set<ImagePathBucketIDPair> createImagePathBucketIDPairConcurrentHashSet(){
        return  Collections.newSetFromMap(new ConcurrentHashMap<ImagePathBucketIDPair, Boolean>());
    }

    public static  Set<ServerUUIDBucketIDPair> createServerUUIDBucketIDPairConcurrentHashSet(){
        return  Collections.newSetFromMap(new ConcurrentHashMap<ServerUUIDBucketIDPair, Boolean>());
    }
}
