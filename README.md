![npm](https://img.shields.io/npm/dm/cordova-plugin-theme-detection) ![npm](https://img.shields.io/npm/v/cordova-plugin-theme-detection) ![NPM](https://img.shields.io/npm/l/cordova-plugin-theme-detection)

# Cordova plugin for theme detection

## Donation

If you like this plugin feel free to by me a beer :beers:. So I can maintain this and other plugins and projects.

[![https://www.paypal.me/mariusb73/5](https://img.shields.io/badge/donate-paypal-blue.svg?style=flat-square)](https://www.paypal.me/mariusb73/5)


## Description

This plugin detects whether the dark mode is enabled on the device or not.

iOS 13+ must be installed on your device, to use this plugin.
For Android you can use it since Android 10 (API 29). The Browser platform requires `window.matchMedia()` support.

## Installation

Add the plugin with the following command:

`cordova plugin add cordova-plugin-theme-detection`

## Usage

```js
cordova.plugins.ThemeDetection.methodName(
  [parameter],
  function(success) {
    console.log(success);
  },
  function(error) {
    console.log(error);
  }
);
```

#### Ionic Native

If you are using Ionic, use the Ionic Native Wrapper. Install it with `npm install @ionic-native/theme-detection`.

Import the plugin in your app.module:

```ts
 @NgModule({
  declarations: [AppComponent],
  entryComponents: [],
  imports: [BrowserModule, IonicModule.forRoot(), AppRoutingModule],
  providers: [
    ThemeDetection,
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy }
  ],
  bootstrap: [AppComponent]
})
```

And import and use it in every of your components:

```ts
import { ThemeDetection } from "@ionic-native/theme-detection/ngx";

@Component({
  selector: "app-home",
  templateUrl: "home.page.html"
})
export class HomePage {
  constructor(private themeDetection: ThemeDetection) {}

  private async isAvailable(): Promise<ThemeDetectionResponse> {
    try {
      let dark_mode_available: ThemeDetectionResponse = await this.themeDetection.isAvailable();
    } catch (e) {
      console.log(e);
    }
  }

  private async isDarkModeEnabled(): Promise<ThemeDetectionResponse> {
    try {
      let dark_mode_enabled: ThemeDetectionResponse = await this.themeDetection.isDarkModeEnabled();
    } catch (e) {
      console.log(e);
    }
  }
}
```

### Methods

#### isAvailable

`cordova.plugins.ThemeDetection.isAvailable()`

Checks whether the device is running on iOS 13 or Android 10 (API 29) or newer and returns an object with a boolean value and a message.

#### isDarkModeEnabled

`cordova.plugins.ThemeDetection.isDarkModeEnabled()`

Checks whether the dark mode is enabled on device and returns an object with a boolean value and a message.

### Theme change event (Android)

On Android, the plugin fires a document event whenever the active theme changes
between light and dark. The native listener is started automatically when Cordova
is ready, so no additional subscription method needs to be called.

The event name is available as
`cordova.plugins.ThemeDetection.THEME_CHANGE_EVENT` and currently resolves to
`cordova-plugin-theme-detection:change`.

When the literal event name is used, the listener can be registered before
Cordova's `deviceready` event. Accessing the exported
`cordova.plugins.ThemeDetection.THEME_CHANGE_EVENT` constant should wait until
`deviceready`.

```js
document.addEventListener(
  "cordova-plugin-theme-detection:change",
  function(event) {
    console.log(event.detail.theme);
    // "dark" or "light"

    console.log(event.detail.isDarkModeEnabled);
    // boolean: true or false
  }
);

document.addEventListener("deviceready", function() {
  // The change event only reports changes. Query the initial value separately.
  cordova.plugins.ThemeDetection.isDarkModeEnabled(
    function(result) {
      console.log("Initial dark mode value:", result.value);
    },
    function(error) {
      console.error(error);
    }
  );
});
```

The event detail has the following shape:

```js
{
  theme: "dark" | "light",
  isDarkModeEnabled: boolean
}
```

Theme changes are delivered while the Cordova application process and WebView
are alive. A change that occurs while the application is in the background is
also checked when the application resumes. If the application process was
terminated, use `isDarkModeEnabled()` after the next `deviceready` event to get
the current value.

### Window background sync (Android)

Android resolves `android:windowBackground` once, when the window is created,
and Cordova's manifest template lists `uiMode` in `android:configChanges`. A
system dark/light switch therefore does not recreate the activity, and the
window keeps the background color it resolved at launch: an application started
in light mode still shows the light background after the device switches to
dark. That color is visible wherever the WebView does not cover the window —
most noticeably in edge-to-edge applications while the soft keyboard shrinks
the WebView.

The plugin re-resolves the theme's `windowBackground` for the new configuration
and re-applies it, so themes providing `values-night` variants keep working
without recreating the activity (which would reload the whole web application).
This runs automatically and does not depend on the theme change event above.

Set the following preference in `config.xml` to opt out, for example when the
application manages the window background itself at runtime:

```xml
<preference name="ThemeDetectionSyncWindowBackground" value="false" />
```

### Responses

**ThemeDetectionResponse**:

```js
ThemeDetectionResponse {
    value: boolean;
    message: string;
}
```

## Common issues

### UIUserInterfaceStyle

If you have set the following Property in your `config.xml` file, the plugin will always return false:

```xml
<config-file parent="UIUserInterfaceStyle" platform="ios" target="*-Info.plist">
  <string>Light</string>
</config-file>
```

Please remove this property from `config.xml`.

## Changelog

- 1.4.0: Keep the Android window background in sync with the system theme
- 1.3.1: Add Android theme change event
- 1.3.0: Add browser platform support
- 1.2.1: Updated README
- 1.2.0: Bugfix for Android 10
- 1.1.3: Updated from beta
- 1.1.2: Fix in documentation
- 1.1.1: Updated documentation for Android
- 1.1.0: Addded Android support
- 1.0.3: Update README.md for Ionic Native Wrapper support.
- 1.0.1: iOS Version Info if Plugin is not available.
- 1.0.0: Initial version support for iOS.
