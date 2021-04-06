package de.wellenvogel.avnav.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.TimeZone;

import de.wellenvogel.avnav.main.Constants;

/**
 * Created by andreas on 26.11.15.
 */
public class AvnUtil {
    public static final double msToKn=3600.0/1852.0;

    public static long getLongPref(SharedPreferences prefs, String key, long defaultValue){
        try{
            return prefs.getLong(key,defaultValue);
        }catch (Throwable x){}
        try {
            String v = prefs.getString(key, null);
            if (v == null) return defaultValue;
            try {
                long rt = Long.parseLong(v);
                return rt;
            } catch (Exception e) {
                return defaultValue;
            }
        }catch (Throwable t){

        }
        return defaultValue;
    }

    public static boolean matchesNmeaFilter(String record, String[] nmeaFilter) {
        if (record == null || nmeaFilter == null || nmeaFilter.length < 1) return true;
        boolean matches = false;
        boolean hasPositiveCondition = false;
        for (String f : nmeaFilter) {
            boolean inverse = false;
            if (f.startsWith("^")) {
                inverse = true;
                f = f.substring(1);
            } else {
                hasPositiveCondition = true;
            }
            if (f.startsWith("$")) {
                if (!record.startsWith("$")) continue;
                if (record.substring(3).startsWith(f.substring(1))) {
                    if (!inverse) {
                        matches = true;
                    } else {
                        //an inverse match always wins
                        return false;
                    }
                }
            } else {
                if (record.startsWith(f)) {
                    if (!inverse) {
                        matches = true;
                    } else {
                        return false;
                    }
                }
            }
        }
        if (matches) return true;
        //we consider the check to fail if there was no match
        //but we had at least a positive condition
        return ! hasPositiveCondition;
    }
    public static String[] splitNmeaFilter(String nmeaFilter){
        if (nmeaFilter != null && ! nmeaFilter.isEmpty()){
            if (nmeaFilter.indexOf(",")>=0) {
                return nmeaFilter.split(" *, *");
            }
            else{
                return new String[]{nmeaFilter};
            }
        }
        return null;
    }

    public static String removeNonNmeaChars(String input){
        if (input == null) return input;
        return input.replaceAll("[^\\x20-\\x7F]", "");
    }

    public static File workdirStringToFile(String wd, Context context){
        if (wd.equals(Constants.INTERNAL_WORKDIR)){
            return context.getFilesDir();
        }
        if (wd.equals(Constants.EXTERNAL_WORKDIR)){
            return context.getExternalFilesDir(null);
        }
        return new File(wd);
    }
    public static File getWorkDir(SharedPreferences pref, Context context){
        if (pref == null){
            pref=getSharedPreferences(context);
        }
        String wd=pref.getString(Constants.WORKDIR,"");
        return workdirStringToFile(wd,context);
    }

    public static SharedPreferences getSharedPreferences(Context ctx){
        return ctx.getSharedPreferences(Constants.PREFNAME, Context.MODE_PRIVATE);
    }

    public static String getMandatoryParameter(Uri uri, String name)throws Exception{
        String rt=uri.getQueryParameter(name);
        if (rt == null) throw new Exception("missing mandatory parameter "+name);
        return rt;
    }
    public static boolean getFlagParameter(Uri uri, String name,boolean defaultV)throws Exception{
        String rt=uri.getQueryParameter(name);
        if (rt == null || rt.isEmpty()) return defaultV;
        return rt.toLowerCase().equals("true");
    }

    public static JSONObject readJsonFile(File file,long maxBytes) throws Exception {
        if (!file.exists()) throw new Exception("file " + file.getAbsolutePath() + " not found");
        if (file.length() > maxBytes)
            throw new Exception("file " + file.getAbsolutePath() + " too long to read");
        FileInputStream is = new FileInputStream(file);
        byte[] buffer = new byte[(int) (file.length())];
        int rd = is.read(buffer);
        if (rd != file.length())
            throw new Exception("unable to read all bytes for " + file.getAbsolutePath());
        JSONObject rt = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        return rt;
    }

    public static long toTimeStamp(net.sf.marineapi.nmea.util.Date date, net.sf.marineapi.nmea.util.Time time){
        if (date == null) return 0;
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.YEAR, date.getYear());
        cal.set(Calendar.MONTH, date.getMonth()-1); //!!! the java calendar counts from 0
        cal.set(Calendar.DAY_OF_MONTH, date.getDay());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MILLISECOND, (int) (time.getMilliseconds()));
        long millis=cal.getTime().getTime();
        return millis;
    }

    public static interface IJsonObect{
        JSONObject toJson() throws JSONException;
    }
}
