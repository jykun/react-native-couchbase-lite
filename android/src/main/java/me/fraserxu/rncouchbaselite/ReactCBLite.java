package me.fraserxu.rncouchbaselite;

import android.content.Context;
import android.content.res.AssetManager;

import com.couchbase.lite.Database;
import com.couchbase.lite.DocumentChange;
import com.couchbase.lite.Manager;
import com.couchbase.lite.View;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.javascript.JavaScriptViewCompiler;
import com.couchbase.lite.listener.Credentials;
import com.couchbase.lite.listener.LiteListener;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.android.*;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.JavascriptException;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;

public class ReactCBLite extends ReactContextBaseJavaModule {

    public static final String REACT_CLASS = "ReactCBLite";
    private static final int DEFAULT_LISTEN_PORT = 5984;
    private final String TAG = "ReactCBLite";
    private ReactApplicationContext context;
    private int listenPort;
    private Credentials allowedCredentials;
    private Manager managerServer;

    private static final String DB_EVENT_KEY = "couchBaseDBEvent";

    public ReactCBLite(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    /**
     * Returns constants of this module in React-native to share (javascript)
     */
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("DBChanged", DB_EVENT_KEY);
        return constants;
    }

    @ReactMethod
    public void init(int listenPort, String login, String password, Callback errorCallback) {
        initCBLite(listenPort, login, password, errorCallback);
    }

    private void initCBLite(int listenPort, String login, String password, Callback errorCallback) {
        try {
            if(this.managerServer == null) {
                allowedCredentials = new Credentials(login, password);

                View.setCompiler(new JavaScriptViewCompiler());

                AndroidContext context = new AndroidContext(this.context);
                Manager.enableLogging(Log.TAG, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_SYNC, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_CHANGE_TRACKER, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_BLOB_STORE, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_LISTENER, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_MULTI_STREAM_WRITER, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_REMOTE_REQUEST, Log.VERBOSE);
                Manager.enableLogging(Log.TAG_ROUTER, Log.VERBOSE);
                Manager manager = new Manager(context, Manager.DEFAULT_OPTIONS);

                listenPort = startCBLListener(listenPort, manager, allowedCredentials);

                this.managerServer = manager;

                Log.i(TAG, "initCBLite() completed successfully with: " + String.format(
                        "http://%s:%s@localhost:%d/",
                        allowedCredentials.getLogin(),
                        allowedCredentials.getPassword(),
                        listenPort));
            }
          errorCallback.invoke();

        } catch (final Exception e) {
            e.printStackTrace();
          errorCallback.invoke(e.getMessage());
        }
    }

    private int startCBLListener(int listenPort, Manager manager, Credentials allowedCredentials) {
        LiteListener listener = new LiteListener(manager, listenPort, allowedCredentials);
        int boundPort = listener.getListenPort();
        Thread thread = new Thread(listener);
        thread.start();

        return boundPort;
    }

    /**
     * Function to be shared to React-native, it monitor a local database changes
     * @param  databaseLocal    String      database for local server
     * @param  onEnd            Callback    function to call when finish
     */
    @ReactMethod
    public void monitorDatabase(String databaseLocal, Callback onEnd) {
        try {
            Manager manager = this.managerServer;
            Database db = manager.getExistingDatabase(databaseLocal);

            if(db != null) {
                db.addChangeListener(new Database.ChangeListener() {
                    @Override
                    public void changed(Database.ChangeEvent event) {
                        for (DocumentChange dc : event.getChanges()) {
                            WritableMap eventM = Arguments.createMap();
                            eventM.putString("databaseName", event.getSource().getName());
                            eventM.putString("id", dc.getDocumentId());
                            sendEvent(DB_EVENT_KEY, eventM);
                        }
                    }
                });
            }

            if (onEnd != null)
                onEnd.invoke();
        }catch(Exception e){
            throw new JavascriptException(e.getMessage());
        }
    }
  
    /**
     * Function to be shared to React-native, copy a prebuild database if not exist
     * @param  databaseLocal           String      database for local server
     * @param  withPreBuildDatabase    String      database for preBuild
     * @param  onEnd                   Callback    function to call when finish
     */
    @ReactMethod
    public void copyDatabase(String databaseLocal, String withPreBuildDatabase, Callback onEnd) {
        // create a manager
        Manager manager;
        try {
            manager = new Manager(new AndroidContext(getReactApplicationContext()), Manager.DEFAULT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "Cannot create manager object");
            return;
        }

        // setup database (copying initial data if needed)
        Database _database;
        try {
            _database = manager.getExistingDatabase(databaseLocal);
            if (_database == null) {
                Log.i(TAG, "Database not found, extracting initial dataset.");
                try {
                    AssetManager assets = getReactApplicationContext().getAssets();
                    InputStream cannedDb = assets.open("couchtalk.cblite");
                    manager.replaceDatabase(databaseLocal, cannedDb, null);
                } catch (IOException e) {
                    Log.e(TAG, String.format("Couldn't load canned database. %s", e));
                }
                // HACK: intentionally may remain `null` so app crashes instead of silent trouble…
                _database = manager.getExistingDatabase(databaseLocal);
            }
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot get database");
            return;
        }
        final Database database = _database;
    }

    /**
     * Function to send: push pull events
     */
    private void sendEvent(String eventName, WritableMap params) {
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
