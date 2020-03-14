package de.wellenvogel.avnav.appapi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import de.wellenvogel.avnav.fileprovider.AssetsProvider;
import de.wellenvogel.avnav.fileprovider.UserFileProvider;
import de.wellenvogel.avnav.main.BuildConfig;
import de.wellenvogel.avnav.main.Constants;
import de.wellenvogel.avnav.util.AvnLog;
import de.wellenvogel.avnav.util.AvnUtil;

public class AddonHandler implements INavRequestHandler,IDeleteByUrl{

    static class AddonInfo implements IJsonObect {
        public String name;
        public String url;
        public String icon;
        public String title;
        @Override
        public JSONObject toJson() throws JSONException {
            JSONObject rt=new JSONObject();
            rt.put("name",name);
            rt.put("key",name);
            rt.put("canDelete",true);
            rt.put("url",url);
            rt.put("keepUrl",url.startsWith("http"));
            rt.put("icon",icon);
            if (title != null) rt.put("title",title);
            return rt;
        }
        public AddonInfo(String name){
            this.name=name;
        }
        static AddonInfo fromJson(JSONObject o) throws JSONException {
            AddonInfo rt=new AddonInfo(o.getString("name"));
            rt.title=o.optString("title",null);
            rt.icon=o.getString("icon");
            rt.url=o.getString("url");
            return rt;
        }
    }

    private Context context;
    private RequestHandler handler;

    public AddonHandler(Context ctx,RequestHandler handler){
        this.context=ctx;
        this.handler=handler;
    }

    @Override
    public ExtendedWebResourceResponse handleDownload(String name, Uri uri) throws Exception {
        throw new Exception("not implemented");
    }

    @Override
    public boolean handleUpload(PostVars postData, String name, boolean ignoreExisting) throws Exception {
        throw new Exception("not implemented");
    }

    @Override
    public JSONArray handleList() throws Exception{
        List<AddonInfo> addons=getAddons(true);
        JSONArray rt=new JSONArray();
        for (AddonInfo addon : addons) rt.put(addon.toJson());
        return rt;
    }

    private ArrayList<AddonInfo> getAddons(boolean check){
        ArrayList<AddonInfo> rt=new ArrayList<AddonInfo>();
        SharedPreferences prefs=AvnUtil.getSharedPreferences(context);
        String addonString=null;
        try {
            addonString=prefs.getString(Constants.ADDON_CONFIG, null);
            if (addonString != null){
                JSONArray addons=new JSONArray(addonString);
                for (int i=0;i<addons.length();i++){
                    JSONObject addon=addons.getJSONObject(i);
                    AddonInfo info=AddonInfo.fromJson(addon);
                    if (info.url == null) continue;
                    if (check ){
                        boolean ok=true;
                        for (String url : new String[]{info.url, info.icon}) {
                            if (url.startsWith("http")) continue;
                            if (url.startsWith("/")) url=url.substring(1);
                            else url="viewer/"+url;
                            INavRequestHandler nrh = handler.getPrefixHandler(url);
                            try {
                                ExtendedWebResourceResponse resp = nrh.handleDirectRequest(url);
                                if (resp == null) {
                                    throw new Exception("not found");
                                }
                                resp.getData().close();
                            } catch (Exception e) {
                                AvnLog.e("url/icon for userapp not found" + url);
                                ok=false;
                                break;
                            }
                        }
                        if (!ok) continue;
                    }
                    rt.add(info);
                }
            }
        }catch (Throwable e){
            AvnLog.e("error reading addon config",e);
        }
        return rt;
    }


    private void saveAddons(List<AddonInfo> addons) throws JSONException {
        JSONArray sv=new JSONArray();
        for (AddonInfo info:addons){
            sv.put(info.toJson());
        }
        AvnUtil.getSharedPreferences(context).edit()
                .putString(Constants.ADDON_CONFIG,sv.toString()).apply();
    }

    private int findAddon(List<AddonInfo> list, String name){
        for (int i=0;i<list.size();i++){
            if (list.get(i).name.equals(name)) return i;
        }
        return -1;
    }
    @Override
    public boolean deleteByUrl(String url) throws JSONException {
        ArrayList<AddonInfo> addons=getAddons(false);
        ArrayList<Integer> deletes=new ArrayList<Integer>();
        for (int i=0;i<addons.size();i++){
            if (addons.get(i).url.equals(url)) deletes.add(i);
        }
        if (deletes.size() < 1) return false;
        for (int k=deletes.size()-1;k>=0;k--){
            addons.remove(deletes.get(k).intValue());
        }
        saveAddons(addons);
        return true;
    }

    @Override
    public boolean handleDelete(String name, Uri uri) throws Exception {
        synchronized (this) {
            ArrayList<AddonInfo> addons = getAddons(false);
            int idx = findAddon(addons, name);
            if (idx < 0) return false;
            addons.remove(idx);
            saveAddons(addons);
            return true;
        }
    }

    private String computeName(String url, String icon, String title) throws NoSuchAlgorithmException {
        MessageDigest digest=java.security.MessageDigest.getInstance("MD5");
        if (url != null) digest.update(url.getBytes());
        if (icon != null) digest.update(icon.getBytes());
        if (title != null) digest.update(title.getBytes());
        String hash = new BigInteger(1, digest.digest()).toString(16);
        return hash;
    }

    @Override
    public JSONObject handleApiRequest(Uri uri,PostVars postData) throws Exception {
        String command= AvnUtil.getMandatoryParameter(uri,"command");
        if (command.equals("list")){
            RequestHandler.getReturn(new RequestHandler.KeyValue("data",handleList()));
        }
        if (command.equals("update")){
            String name=uri.getQueryParameter("name");
            String title=uri.getQueryParameter("title");
            String url=AvnUtil.getMandatoryParameter(uri,"url");
            String icon=AvnUtil.getMandatoryParameter(uri,"icon");
            ArrayList<AddonInfo> addons=getAddons(false);
            int idx=-1;
            if (name == null){
                name=computeName(url,icon,title);
                idx=findAddon(addons,name);
                if (idx >= 0 ) RequestHandler.getErrorReturn("a similar addon already exists");
                AddonInfo newAddon=new AddonInfo(name);
                newAddon.url=url;
                newAddon.icon=icon;
                newAddon.title=title;
                addons.add(newAddon);
            }
            else{
                idx=findAddon(addons,name);
                if (idx < 0) RequestHandler.getErrorReturn("addon not found");
                AddonInfo current=addons.get(idx);
                current.icon=icon;
                current.title=title;
                current.url=url;
            }
            saveAddons(addons);
        }
        return RequestHandler.getReturn();
    }

    @Override
    public ExtendedWebResourceResponse handleDirectRequest(String url) throws FileNotFoundException {
        return null;
    }

    @Override
    public String getPrefix() {
        return null;
    }


}
