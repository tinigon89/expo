package expo.modules.updates;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSBundleLoader;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import androidx.annotation.Nullable;
import expo.modules.updates.db.Reaper;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.launcher.EmergencyLauncher;
import expo.modules.updates.launcher.Launcher;
import expo.modules.updates.launcher.LauncherWithSelectionPolicy;
import expo.modules.updates.launcher.SelectionPolicy;
import expo.modules.updates.launcher.SelectionPolicyNewest;
import expo.modules.updates.loader.EmbeddedLoader;
import expo.modules.updates.manifest.Manifest;
import expo.modules.updates.loader.RemoteLoader;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;

public class UpdatesController {

  private static final String TAG = UpdatesController.class.getSimpleName();

  private static final String UPDATES_EVENT_NAME = "Expo.nativeUpdatesEvent";
  private static final String UPDATE_AVAILABLE_EVENT = "updateAvailable";
  private static final String UPDATE_NO_UPDATE_AVAILABLE_EVENT = "noUpdateAvailable";
  private static final String UPDATE_ERROR_EVENT = "error";

  private static UpdatesController sInstance;

  private WeakReference<ReactNativeHost> mReactNativeHost;

  private Uri mManifestUrl;
  private File mUpdatesDirectory;
  private Exception mUpdatesDirectoryException;
  private Launcher mLauncher;
  private DatabaseHolder mDatabaseHolder;
  private SelectionPolicy mSelectionPolicy;

  // launch conditions
  private boolean mIsReadyToLaunch = false;
  private boolean mTimeoutFinished = false;
  private boolean mHasLaunched = false;

  private UpdatesController(Context context, Uri url) {
    sInstance = this;
    mManifestUrl = url;
    mDatabaseHolder = new DatabaseHolder(UpdatesDatabase.getInstance(context));
    mSelectionPolicy = new SelectionPolicyNewest(getRuntimeVersion(context));
    if (context instanceof ReactApplication) {
      mReactNativeHost = new WeakReference<>(((ReactApplication) context).getReactNativeHost());
    }

    try {
      mUpdatesDirectory = UpdateUtils.getOrCreateUpdatesDirectory(context);
    } catch (Exception e) {
      mUpdatesDirectoryException = e;
      mUpdatesDirectory = null;
    }
  }

  public static UpdatesController getInstance() {
    if (sInstance == null) {
      throw new IllegalStateException("UpdatesController.getInstance() was called before the module was initialized");
    }
    return sInstance;
  }

  /**
   * Initializes the UpdatesController singleton. This should be called as early as possible in the
   * application's lifecycle.
   * @param context the base context of the application, ideally a {@link ReactApplication}
   */
  public static void initialize(Context context) {
    if (sInstance == null) {
      Uri url = null;
      try {
        ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        String urlString = ai.metaData.getString("expo.modules.updates.EXPO_UPDATE_URL");
        url = urlString == null ? null : Uri.parse(urlString);
      } catch (Exception e) {
        Log.e(TAG, "Could not read value expo.modules.updates.EXPO_UPDATE_URL in AndroidManifest", e);
      }
      if (url == null) {
        throw new AssertionError("UpdatesController cannot be initialized without a valid value for expo.modules.updates.EXPO_UPDATE_URL in AndroidManifest");
      }
      new UpdatesController(context, url);
    }
  }

  /**
   * If UpdatesController.initialize() is not provided with a {@link ReactApplication}, this method
   * can be used to set a {@link ReactNativeHost} on the class. This is optional, but required in
   * order for `Updates.reload()` and some Updates module events to work.
   * @param reactNativeHost the ReactNativeHost of the application running the Updates module
   */
  public void setReactNativeHost(ReactNativeHost reactNativeHost) {
    mReactNativeHost = new WeakReference<>(reactNativeHost);
  }

  // database

  private class DatabaseHolder {
    private UpdatesDatabase mDatabase;
    private boolean isInUse = false;

    public DatabaseHolder(UpdatesDatabase database) {
      mDatabase = database;
    }

    public synchronized UpdatesDatabase getDatabase() {
      while (isInUse) {
        try {
          wait();
        } catch (InterruptedException e) {
          Log.e(TAG, "Interrupted while waiting for database", e);
        }
      }

      isInUse = true;
      return mDatabase;
    }

    public synchronized void releaseDatabase() {
      isInUse = false;
      notify();
    }
  }

  public UpdatesDatabase getDatabase() {
    return mDatabaseHolder.getDatabase();
  }

  public void releaseDatabase() {
    mDatabaseHolder.releaseDatabase();
  }

  /**
   * Returns the path on disk to the launch asset (JS bundle) file for the React Native host to use.
   * Blocks until the configured timeout runs out, or a new update has been downloaded and is ready
   * to use (whichever comes sooner). ReactNativeHost.getJSBundleFile() should call into this.
   *
   * If this returns null, something has gone wrong and expo-updates has not been able to launch or
   * find an update to use. In (and only in) this case, `getBundleAssetName()` will return a nonnull
   * fallback value to use.
   */
  public synchronized @Nullable String getLaunchAssetFile() {
    while (!mIsReadyToLaunch || !mTimeoutFinished) {
      try {
        wait();
      } catch (InterruptedException e) {
        Log.e(TAG, "Interrupted while waiting for launch asset file", e);
      }
    }

    mHasLaunched = true;

    if (mLauncher == null) {
      return null;
    }
    return mLauncher.getLaunchAssetFile();
  }

  /**
   * Returns the filename of the launch asset (JS bundle) file embedded in the APK bundle, which can
   * be read using `context.getAssets()`. This is only nonnull if `getLaunchAssetFile` is null and
   * should only be used in such a situation. ReactNativeHost.getBundleAssetName() should call into
   * this.
   */
  public @Nullable String getBundleAssetName() {
    if (mLauncher == null) {
      return null;
    }
    return mLauncher.getBundleAssetName();
  }

  /**
   * Returns a map of the locally downloaded assets for the current update. Keys are the remote URLs
   * of the assets and values are local paths. This should be exported by the Updates JS module and
   * can be used by `expo-asset` or a similar module to override React Native's asset resolution and
   * use the locally downloaded assets.
   */
  public @Nullable Map<AssetEntity, String> getLocalAssetFiles() {
    if (mLauncher == null) {
      return null;
    }
    return mLauncher.getLocalAssetFiles();
  }

  // other getters

  public Uri getManifestUrl() {
    return mManifestUrl;
  }

  public File getUpdatesDirectory() {
    return mUpdatesDirectory;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLauncher.getLaunchedUpdate();
  }

  public SelectionPolicy getSelectionPolicy() {
    return mSelectionPolicy;
  }

  public boolean isEmergencyLaunch() {
    return mLauncher != null && mLauncher instanceof EmergencyLauncher;
  }

  /**
   * Starts the update process to launch a previously-loaded update and (if configured to do so)
   * check for a new update from the server. This method should be called as early as possible in
   * the application's lifecycle.
   * @param context the base context of the application, ideally a {@link ReactApplication}
   */
  public synchronized void start(final Context context) {
    if (mUpdatesDirectory == null) {
      mLauncher = new EmergencyLauncher(context, mUpdatesDirectoryException);
      mIsReadyToLaunch = true;
      mTimeoutFinished = true;
      UpdatesController.this.notify();
      return;
    }

    int delay = 0;
    try {
      ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      delay = ai.metaData.getInt("expo.modules.updates.EXPO_UPDATES_LAUNCH_WAIT_MS", 0);
    } catch (Exception e) {
      Log.e(TAG, "Could not parse expo.modules.updates.EXPO_UPDATES_LAUNCH_WAIT_MS from AndroidManifest; defaulting to 0", e);
    }

    if (delay > 0) {
      HandlerThread handlerThread = new HandlerThread("expo-updates-timer");
      handlerThread.start();
      new Handler(handlerThread.getLooper()).postDelayed(this::finishTimeout, delay);
    } else {
      mTimeoutFinished = true;
    }

    UpdatesDatabase database = getDatabase();
    LauncherWithSelectionPolicy launcher = new LauncherWithSelectionPolicy(mUpdatesDirectory, mSelectionPolicy);
    mLauncher = launcher;
    if (mSelectionPolicy.shouldLoadNewUpdate(EmbeddedLoader.readEmbeddedManifest(context).getUpdateEntity(), launcher.getLaunchableUpdate(database))) {
      new EmbeddedLoader(context, database, mUpdatesDirectory).loadEmbeddedUpdate();
    }
    launcher.launch(database, context, new Launcher.LauncherCallback() {
      @Override
      public void onFailure(Exception e) {
        mLauncher = new EmergencyLauncher(context, e);
        onSuccess();
      }

      @Override
      public void onSuccess() {
        releaseDatabase();
        synchronized (UpdatesController.this) {
          mIsReadyToLaunch = true;
          UpdatesController.this.notify();
        }
      }
    });

    if (shouldCheckForUpdateOnLaunch(context)) {
      AsyncTask.execute(() -> {
        UpdatesDatabase db = getDatabase();
        new RemoteLoader(context, db, mUpdatesDirectory)
            .start(mManifestUrl, new RemoteLoader.LoaderCallback() {
              @Override
              public void onFailure(Exception e) {
                Log.e(TAG, "Failed to download remote update", e);
                releaseDatabase();

                WritableMap params = Arguments.createMap();
                params.putString("message", e.getMessage());
                sendEventToReactInstance(UPDATE_ERROR_EVENT, params);

                runReaper();
              }

              @Override
              public boolean onManifestDownloaded(Manifest manifest) {
                UpdateEntity launchedUpdate = getLaunchedUpdate();
                if (launchedUpdate == null) {
                  return true;
                }
                return mSelectionPolicy.shouldLoadNewUpdate(manifest.getUpdateEntity(), launchedUpdate);
              }

              @Override
              public void onSuccess(@Nullable UpdateEntity update) {
                final LauncherWithSelectionPolicy newLauncher = new LauncherWithSelectionPolicy(mUpdatesDirectory, mSelectionPolicy);
                newLauncher.launch(database, context, new Launcher.LauncherCallback() {
                  @Override
                  public void onFailure(Exception e) {
                    releaseDatabase();
                    finishTimeout();
                    Log.e(TAG, "Loaded new update but it failed to launch", e);
                  }

                  @Override
                  public void onSuccess() {
                    releaseDatabase();

                    boolean hasLaunched = mHasLaunched;
                    if (!hasLaunched) {
                      mLauncher = newLauncher;
                    }

                    finishTimeout();

                    if (hasLaunched) {
                      if (update == null) {
                        sendEventToReactInstance(UPDATE_NO_UPDATE_AVAILABLE_EVENT, null);
                      } else {
                        WritableMap params = Arguments.createMap();
                        params.putString("manifestString", update.metadata.toString());
                        sendEventToReactInstance(UPDATE_AVAILABLE_EVENT, params);
                      }
                    }

                    runReaper();
                  }
                });
              }
            });
      });
    } else {
      runReaper();
    }
  }

  private String getRuntimeVersion(Context context) {
    String runtimeVersion = null;
    String sdkVersion = null;
    try {
      ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      runtimeVersion = ai.metaData.getString("expo.modules.updates.EXPO_RUNTIME_VERSION");
      sdkVersion = ai.metaData.getString("expo.modules.updates.EXPO_SDK_VERSION");
    } catch (Exception e) {
      Log.e(TAG, "Failed to read meta-data from AndroidManifest", e);
    }
    if (runtimeVersion != null && runtimeVersion.length() > 0) {
      return runtimeVersion;
    } else if (sdkVersion != null && sdkVersion.length() > 0) {
      return sdkVersion;
    } else {
      throw new AssertionError("One of expo_runtime_version or expo_sdk_version must be defined in the Android app manifest");
    }
  }

  private boolean shouldCheckForUpdateOnLaunch(Context context) {
    if (mManifestUrl == null) {
      return false;
    }

    String developerSetting = "ALWAYS";
    try {
      ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      developerSetting = ai.metaData.getString("expo.modules.updates.EXPO_UPDATES_CHECK_ON_LAUNCH", "ALWAYS");
    } catch (Exception e) {
      Log.e(TAG, "Failed to read value of expo.modules.updates.EXPO_UPDATES_CHECK_ON_LAUNCH in AndroidManifest; defaulting to ALWAYS", e);
    }

    if ("ALWAYS".equals(developerSetting)) {
      return true;
    } else if ("NEVER".equals(developerSetting)) {
      return false;
    } else if ("WIFI_ONLY".equals(developerSetting)) {
      ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) {
        Log.e(TAG, "Could not determine active network connection is metered; not checking for updates");
        return false;
      }
      return !cm.isActiveNetworkMetered();
    } else {
      Log.e(TAG, "Invalid value " + developerSetting + " for expo.modules.updates.EXPO_UPDATES_CHECK_ON_LAUNCH in AndroidManifest; defaulting to ALWAYS");
      return true;
    }
  }

  private synchronized void finishTimeout() {
    if (mTimeoutFinished) {
      // already finished, do nothing
      return;
    }
    mTimeoutFinished = true;
    notify();
  }

  private void runReaper() {
    AsyncTask.execute(() -> {
      UpdatesDatabase database = getDatabase();
      Reaper.reapUnusedUpdates(database, mUpdatesDirectory, getLaunchedUpdate(), mSelectionPolicy);
      releaseDatabase();
    });
  }

  public void relaunchReactApplication(Context context, Launcher.LauncherCallback callback) {
    if (mReactNativeHost == null || mReactNativeHost.get() == null) {
      callback.onFailure(new Exception("Could not reload application. Ensure you have passed the correct instance of ReactApplication into UpdatesController.initialize()."));
      return;
    }
    final ReactNativeHost host = mReactNativeHost.get();

    final String oldLaunchAssetFile = mLauncher.getLaunchAssetFile();

    UpdatesDatabase database = getDatabase();
    final LauncherWithSelectionPolicy newLauncher = new LauncherWithSelectionPolicy(mUpdatesDirectory, mSelectionPolicy);
    newLauncher.launch(database, context, new Launcher.LauncherCallback() {
      @Override
      public void onFailure(Exception e) {
        callback.onFailure(e);
      }

      @Override
      public void onSuccess() {
        mLauncher = newLauncher;
        releaseDatabase();

        final ReactInstanceManager instanceManager = host.getReactInstanceManager();

        String newLaunchAssetFile = mLauncher.getLaunchAssetFile();
        if (newLaunchAssetFile != null && !newLaunchAssetFile.equals(oldLaunchAssetFile)) {
          // Unfortunately, even though RN exposes a way to reload an application,
          // it assumes that the JS bundle will stay at the same location throughout
          // the entire lifecycle of the app. Since we need to change the location of
          // the bundle, we need to use reflection to set an otherwise inaccessible
          // field of the ReactInstanceManager.
          try {
            JSBundleLoader newJSBundleLoader = JSBundleLoader.createFileLoader(newLaunchAssetFile);
            Field jsBundleLoaderField = instanceManager.getClass().getDeclaredField("mBundleLoader");
            jsBundleLoaderField.setAccessible(true);
            jsBundleLoaderField.set(instanceManager, newJSBundleLoader);
          } catch (Exception e) {
            Log.e(TAG, "Could not reset JSBundleLoader in ReactInstanceManager", e);
          }
        }

        callback.onSuccess();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(instanceManager::recreateReactContextInBackground);

        runReaper();
      }
    });
  }

  private void sendEventToReactInstance(final String eventName, final WritableMap params) {
    if (mReactNativeHost != null && mReactNativeHost.get() != null) {
      final ReactInstanceManager instanceManager = mReactNativeHost.get().getReactInstanceManager();
      AsyncTask.execute(() -> {
        try {
          ReactContext reactContext = null;
          // in case we're trying to send an event before the reactContext has been initialized
          // continue to retry for 5000ms
          for (int i = 0; i < 5; i++) {
            reactContext = instanceManager.getCurrentReactContext();
            if (reactContext != null) {
              break;
            }
            Thread.sleep(1000);
          }

          if (reactContext != null) {
            DeviceEventManagerModule.RCTDeviceEventEmitter emitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
            if (emitter != null) {
              WritableMap eventParams = params;
              if (eventParams == null) {
                eventParams = Arguments.createMap();
              }
              eventParams.putString("type", eventName);
              emitter.emit(UPDATES_EVENT_NAME, eventParams);
              return;
            }
          }

          Log.e(TAG, "Could not emit " + eventName + " event; no event emitter was found.");
        } catch (Exception e) {
          Log.e(TAG, "Could not emit " + eventName + " event; no react context was found.");
        }
      });
    } else {
      Log.e(TAG, "Could not emit " + eventName + " event; UpdatesController was not initialized with an instance of ReactApplication.");
    }
  }
}
