package de.mariusbackes.cordova.plugin;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;
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

  // Opt-out for syncWindowBackground(); on by default. Set to false in config.xml
  // when an app manages the window background itself at runtime.
  private static final String PREF_SYNC_WINDOW_BACKGROUND = "ThemeDetectionSyncWindowBackground";

  private static final String TAG = "ThemeDetection";

  // Night mode the window background was last resolved for.
  private int lastSyncedNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

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
    syncWindowBackground(newConfig);
    notifyIfThemeChanged(newConfig);
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    // Safety net: the theme can also change while the app sits in the background.
    syncWindowBackground(
        this.cordova.getActivity().getResources().getConfiguration()
    );
    notifyIfThemeChanged(
        this.cordova.getActivity().getResources().getConfiguration()
    );
  }

  /**
   * Re-applies the activity theme's windowBackground for the current configuration.
   *
   * Android resolves android:windowBackground once, when the window is created.
   * Cordova's manifest template opts into `uiMode` in android:configChanges, so a
   * system dark/light switch does not recreate the activity — and cordova refreshes
   * only the system bars (SystemBarPlugin), never the window background. The window
   * therefore keeps the color it resolved at launch: an app started in light mode
   * still shows the light background after switching to dark, wherever the WebView
   * does not cover the window. In an edge-to-edge app that is visible whenever the
   * WebView is inset — most noticeably while the soft keyboard shrinks it.
   *
   * Re-resolving the theme attribute here keeps the window in sync without
   * recreating the activity (which would reload the whole web app). Runs
   * independently of subscribeThemeChanges so apps get the fix without opting in.
   *
   * This is a workaround for a framework gap; it is written to become inert on its
   * own if cordova-android starts handling the window background itself. Plugin
   * callbacks run after cordova's own onConfigurationChanged work, so the value
   * would already be correct by the time this runs and the check below skips it.
   */
  private void syncWindowBackground(Configuration configuration) {
    if (preferences != null && !preferences.getBoolean(PREF_SYNC_WINDOW_BACKGROUND, true)) {
      return;
    }

    int nightMode = getNightMode(configuration);
    if (nightMode == lastSyncedNightMode) {
      return;
    }

    try {
      Activity activity = this.cordova.getActivity();
      Window window = activity.getWindow();
      if (window == null) {
        return;
      }

      TypedValue value = new TypedValue();
      if (!activity.getTheme().resolveAttribute(android.R.attr.windowBackground, value, true)) {
        Log.d(TAG, "windowBackground: theme has no windowBackground, skipping");
        return;
      }

      // resourceId is the resource the attribute points at (0 for a literal value).
      // Keep TYPE_REFERENCE's data as a fallback: a reference left unresolved carries
      // the id there instead.
      int resourceId = value.resourceId;
      if (resourceId == 0 && value.type == TypedValue.TYPE_REFERENCE) {
        resourceId = value.data;
      }

      Drawable next;
      if (resourceId != 0) {
        // Resolve through a context built for the supplied configuration rather than
        // through the activity's own Resources. The activity handles uiMode changes
        // itself (cordova's manifest template), so its Resources may still carry the
        // previous night mode at this point and would hand back the stale color.
        Context configContext = activity.createConfigurationContext(configuration);
        next = configContext.getDrawable(resourceId);
      } else if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        // Literal color in the theme: nothing configuration dependent to re-resolve.
        next = new ColorDrawable(value.data);
      } else {
        Log.d(TAG, "windowBackground: unsupported value type=" + value.type + ", skipping");
        return;
      }

      if (next == null) {
        Log.d(TAG, "windowBackground: resource 0x" + Integer.toHexString(resourceId) + " resolved to null, skipping");
        return;
      }

      // Skip when nothing would change: keeps this idempotent, and makes it inert if
      // cordova-android starts handling the window background itself (plugin
      // callbacks run after cordova's own onConfigurationChanged work).
      Drawable current = window.getDecorView().getBackground();
      boolean unchanged = current instanceof ColorDrawable && next instanceof ColorDrawable
          && ((ColorDrawable) current).getColor() == ((ColorDrawable) next).getColor();

      Log.d(TAG, "windowBackground: night=" + nightMode
          + " current=" + describeDrawable(current)
          + " next=" + describeDrawable(next)
          + (unchanged ? " -> unchanged" : " -> applying"));

      lastSyncedNightMode = nightMode;
      if (unchanged) {
        return;
      }

      window.setBackgroundDrawable(next);
    } catch (Exception e) {
      Log.w(TAG, "windowBackground sync failed", e);
    }
  }

  private String describeDrawable(Drawable drawable) {
    if (drawable == null) {
      return "null";
    }
    if (drawable instanceof ColorDrawable) {
      return String.format("#%08X", ((ColorDrawable) drawable).getColor());
    }
    return drawable.getClass().getSimpleName();
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
