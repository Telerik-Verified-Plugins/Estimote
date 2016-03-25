package com.estimote.sdk;

import android.os.RemoteException;
import android.util.Log;
import java.util.UUID;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class EstimotePlugin extends CordovaPlugin {

  private static final UUID ESTIMOTE_PROXIMITY_UUID = UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");//UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");//"B9407F30-F5F8-466E-AFF9-25556B57FE6D""23A01AF0-232A-4518-9C0E-323FB773F5EF";
  //private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";//UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");//"B9407F30-F5F8-466E-AFF9-25556B57FE6D""23A01AF0-232A-4518-9C0E-323FB773F5EF";    

  private static final String ESTIMOTE_PLUGIN_PARAM_REGION = "region";
  private static final String ESTIMOTE_PLUGIN_PARAM_UUID = "uuid";

  private Region region;

  private static final String LOG_TAG = "EstimotePlugin";
  private static final String ACTION_START_RANGING = "startRanging";
  private static final String ACTION_STOP_RANGING = "stopRanging";

  private CallbackContext rangingCallback;
  private BeaconManager beaconManager;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    try {
      if (action.equals(ACTION_START_RANGING)) {
        startRanging(args, callbackContext);
      } else if (action.equals(ACTION_STOP_RANGING)) {
        stopRanging();
      }
    } catch (Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
    return true;
  }

  private void startRanging(JSONArray args, final CallbackContext callbackCtx) throws JSONException {
    JSONObject options = args.getJSONObject(0);
    region = new Region(
        options.getString(ESTIMOTE_PLUGIN_PARAM_REGION),
        options.has(ESTIMOTE_PLUGIN_PARAM_UUID) ? UUID.fromString(options.getString(ESTIMOTE_PLUGIN_PARAM_UUID)) : ESTIMOTE_PROXIMITY_UUID,
        //options.has(ESTIMOTE_PLUGIN_PARAM_UUID) ? options.getString(ESTIMOTE_PLUGIN_PARAM_UUID) : ESTIMOTE_PROXIMITY_UUID,
        //ESTIMOTE_PROXIMITY_UUID,
        //null,
        null,
        null);

    Log.d(LOG_TAG, "startRanging-method called: "+region.getProximityUUID().toString());

    rangingCallback = callbackCtx;
    try {
      beaconManager = new BeaconManager(cordova.getActivity().getBaseContext());
 Log.d(LOG_TAG, "new Beacon manager created");
      beaconManager.setRangingListener(new BeaconManager.RangingListener() {
           
        @Override
        public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
          Log.d(LOG_TAG, "Ranged beacons: " + beacons);

          JSONObject device = new JSONObject();
          JSONArray array = new JSONArray();

          try {
            for (Beacon b : beacons) {

              String name = b.getProximityUUID().toString();//getName();
              String address = b.getMacAddress().toString();
              String proximityUUID = b.getProximityUUID().toString();

              JSONObject beacon = new JSONObject();

              beacon.put("name", name);
              beacon.put("macAddress", address);
              beacon.put("proximityUUID", proximityUUID);
              beacon.put("major", b.getMajor());
              beacon.put("minor", b.getMinor());
              beacon.put("rssi", b.getRssi());
              beacon.put("distance", computeDistance(b));

              array.put(beacon);
            }

            device.put("beacons", array);

            if (rangingCallback != null) {
              PluginResult result = new PluginResult(PluginResult.Status.OK, device);
              result.setKeepCallback(true);
              rangingCallback.sendPluginResult(result);
            } else {
              Log.e(LOG_TAG, "CallbackContext for discovery doesn't exist.");
            }

          } catch (JSONException e) {
            if (rangingCallback != null) {
              EstimotePlugin.this.error(rangingCallback,
                  e.getMessage(),
                  BluetoothError.ERR_UNKNOWN
              );
              rangingCallback = null;
            }
          }
        }
      });

      beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
        @Override
        public void onServiceReady() {
          try {
            beaconManager.startRanging(region);
          } catch (Throwable e) {
            Log.e(LOG_TAG, "Cannot start ranging", e);
            EstimotePlugin.this.error(callbackCtx, "Cannot start ranging::" + e.getMessage(), BluetoothError.ERR_UNKNOWN);
          }
        }
      });
    } catch (Exception e) {
      this.error(callbackCtx, "Outer exception handler. " + e.getMessage(), BluetoothError.ERR_UNKNOWN);
    }
  }

  private double computeDistance(final Beacon beacon) {
    return Utils.computeAccuracy(beacon);
  }

  private void stopRanging() throws RemoteException {
    if (beaconManager != null) {
      beaconManager.stopRanging(region);
    }
  }

  /**
   * Send an error to given CallbackContext containing the error code and message.
   *
   * @param ctx  Where to send the error.
   * @param msg  What seems to be the problem.
   * @param code Integer value as a an error "code"
   */
  private void error(CallbackContext ctx, String msg, int code) {
    try {
      JSONObject result = new JSONObject();
      result.put("message", msg);
      result.put("code", code);

      ctx.error(result);
    } catch (Exception e) {
      Log.e(LOG_TAG, "Error with... error raising, " + e.getMessage());
    }
  }
}