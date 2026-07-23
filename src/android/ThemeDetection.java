package de.mariusbackes.cordova.plugin;

import android.content.res.Configuration;
import android.os.Build;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ThemeDetection extends CordovaPlugin {
  public enum Action {
    isAvailable,
    isDarkModeEnabled,
    subscribeThemeChanges
  }

  private CallbackContext themeChangeCallback = null;
  private int lastNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

  // System-wide dark theme is officially available since Android 10 (API 29)
  private static final int MINIMUM_VERSION = 29;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    boolean result = false;

    switch(Action.valueOf(action)){
      case isAvailable:
          result = isAvailable(callbackContext); break;
      case isDarkModeEnabled:
          result = isDarkModeEnabled(callbackContext); break;
      case subscribeThemeChanges:
          result = subscribeThemeChanges(callbackContext); break;
    }
    return result;
  }

  private boolean isAvailable(CallbackContext callbackContext) {
    try {
      int systemVersion = Build.VERSION.SDK_INT;
      boolean available = systemVersion >= MINIMUM_VERSION;

      String responseMessage = "Dark mode detection is not available. You need at least Android 10 (API 29), but you have installed API " + systemVersion;
      if(available) {
          responseMessage = "Dark mode detection is available";
      }

      JSONObject obj = createReturnObject(available, responseMessage);
      returnCordovaPluginResult(callbackContext, PluginResult.Status.OK, obj, false);
    } catch (Exception e) {
        JSONObject obj = createReturnObject(false, e.getMessage());
        returnCordovaPluginResult(callbackContext, PluginResult.Status.ERROR, obj, false);
        return false;
    }
    return true;
  }

  private boolean isDarkModeEnabled(CallbackContext callbackContext) {
    try {
      int uiMode = getNightMode(
          this.cordova.getActivity().getResources().getConfiguration()
      );
      boolean enabled = uiMode == Configuration.UI_MODE_NIGHT_YES;

      String responseMessage = "Dark mode is not enabled";
      if(enabled) {
          responseMessage = "Dark mode is enabled";
      }

      JSONObject obj = createReturnObject(enabled, responseMessage);
      returnCordovaPluginResult(callbackContext, PluginResult.Status.OK, obj, false);
    } catch (Exception e) {
        JSONObject obj = createReturnObject(false, e.getMessage());
        returnCordovaPluginResult(callbackContext, PluginResult.Status.ERROR, obj, false);
        return false;
    }
    return true;
  }

  private boolean subscribeThemeChanges(CallbackContext callbackContext) {
    themeChangeCallback = callbackContext;
    lastNightMode = getNightMode(
        this.cordova.getActivity().getResources().getConfiguration()
    );

    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
    result.setKeepCallback(true);
    callbackContext.sendPluginResult(result);
    return true;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    notifyIfThemeChanged(newConfig);
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    notifyIfThemeChanged(
        this.cordova.getActivity().getResources().getConfiguration()
    );
  }

  @Override
  public void onReset() {
    themeChangeCallback = null;
    super.onReset();
  }

  @Override
  public void onDestroy() {
    themeChangeCallback = null;
    super.onDestroy();
  }

  private int getNightMode(Configuration configuration) {
    return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
  }

  private void notifyIfThemeChanged(Configuration configuration) {
    if (themeChangeCallback == null) {
      return;
    }

    int nightMode = getNightMode(configuration);
    if (nightMode == lastNightMode) {
      return;
    }

    lastNightMode = nightMode;
    boolean enabled = nightMode == Configuration.UI_MODE_NIGHT_YES;

    try {
      JSONObject obj = new JSONObject();
      obj.put("theme", enabled ? "dark" : "light");
      obj.put("isDarkModeEnabled", enabled);

      returnCordovaPluginResult(
          themeChangeCallback,
          PluginResult.Status.OK,
          obj,
          true
      );
    } catch (JSONException e) {
      JSONObject obj = createReturnObject(false, e.getMessage());
      returnCordovaPluginResult(
          themeChangeCallback,
          PluginResult.Status.ERROR,
          obj,
          true
      );
    }
  }

  // creates a return object with all needed information
  private JSONObject createReturnObject(boolean value, String message) {
    try {
      JSONObject obj = new JSONObject();
      obj.put("value", value);
      obj.put("message", message);
      return obj;
    } catch (Exception e) {
        System.out.println("JSONObject error: " + e.getMessage());
    }
    return null;
  }

  // returns the plugin result to javascript interface
  private void returnCordovaPluginResult(
      CallbackContext callbackContext,
      Status status,
      JSONObject obj,
      boolean keepCallback
  ) {
    PluginResult result = new PluginResult(status, obj);
    result.setKeepCallback(keepCallback);
    callbackContext.sendPluginResult(result);
  }
}
