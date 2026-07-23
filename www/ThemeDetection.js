var exec = require("cordova/exec");
var channel = require("cordova/channel");
var cordova = require("cordova");

var CLASS = "ThemeDetection";
var THEME_CHANGE_EVENT = "cordova-plugin-theme-detection:change";
var isListeningForThemeChanges = false;

exports.isAvailable = function(success, error) {
  exec(success, error, CLASS, "isAvailable", []);
};

exports.isDarkModeEnabled = function(success, error) {
  exec(success, error, CLASS, "isDarkModeEnabled", []);
};

exports.THEME_CHANGE_EVENT = THEME_CHANGE_EVENT;

function dispatchThemeChange(detail) {
  document.dispatchEvent(
    new CustomEvent(THEME_CHANGE_EVENT, { detail: detail })
  );
}

function subscribeThemeChanges() {
  if (cordova.platformId !== "android" || isListeningForThemeChanges) {
    return;
  }

  isListeningForThemeChanges = true;

  exec(
    dispatchThemeChange,
    function(error) {
      isListeningForThemeChanges = false;
      console.error("ThemeDetection change listener failed", error);
    },
    CLASS,
    "subscribeThemeChanges",
    []
  );
}

channel.onCordovaReady.subscribe(subscribeThemeChanges);
