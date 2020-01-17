package expo.modules.updates.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import expo.modules.updates.UpdateUtils;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.EmbeddedLoader;
import expo.modules.updates.loader.FileDownloader;
import expo.modules.updates.loader.Manifest;

public class Launcher {

  private static final String TAG = Launcher.class.getSimpleName();

  private File mUpdatesDirectory;
  private SelectionPolicy mSelectionPolicy;

  private UpdateEntity mLaunchedUpdate = null;
  private String mLaunchAssetFile = null;
  private Map<String, String> mLocalAssetFiles = null;

  private int mAssetsToDownload = 0;
  private int mAssetsToDownloadFinished = 0;

  private LauncherCallback mCallback = null;

  public interface LauncherCallback{
    public void onFinished();
  }

  public Launcher(File updatesDirectory, SelectionPolicy selectionPolicy) {
    mUpdatesDirectory = updatesDirectory;
    mSelectionPolicy = selectionPolicy;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLaunchedUpdate;
  }

  public String getLaunchAssetFile() {
    return mLaunchAssetFile;
  }

  public Map<String, String> getLocalAssetFiles() {
    return mLocalAssetFiles;
  }

  public synchronized void launch(UpdatesDatabase database, Context context, LauncherCallback callback) {
    if (mCallback != null) {
      throw new AssertionError("Launcher has already started. Create a new instance in order to launch a new version.");
    }
    mCallback = callback;
    mLaunchedUpdate = getLaunchableUpdate(database, context);

    // verify that we have the launch asset on disk
    // according to the database, we should, but something could have gone wrong on disk

    AssetEntity launchAsset = database.updateDao().loadLaunchAsset(mLaunchedUpdate.id);
    if (launchAsset.relativePath == null) {
      throw new AssertionError("Launch Asset relativePath should not be null");
    }

    File launchAssetFile = ensureAssetExists(launchAsset, database, context);
    if (launchAssetFile != null) {
      mLaunchAssetFile = launchAssetFile.toString();
    }

    List<AssetEntity> assetEntities = database.assetDao().loadAssetsForUpdate(mLaunchedUpdate.id);
    mLocalAssetFiles = new HashMap<>();
    for (AssetEntity asset : assetEntities) {
      String filename = asset.relativePath;
      if (filename != null) {
        File assetFile = ensureAssetExists(asset, database, context);
        if (assetFile != null) {
          mLocalAssetFiles.put(
              asset.url.toString(),
              assetFile.toString()
          );
        }
      }
    }

    if (mAssetsToDownload == 0) {
      mCallback.onFinished();
    }
  }

  public UpdateEntity getLaunchableUpdate(UpdatesDatabase database, Context context) {
    List<UpdateEntity> launchableUpdates = database.updateDao().loadLaunchableUpdates();

    String versionName = UpdateUtils.getBinaryVersion(context);

    if (versionName != null) {
      List<UpdateEntity> launchableUpdatesCopy = new ArrayList<>(launchableUpdates);
      for (UpdateEntity update : launchableUpdatesCopy) {
        String[] binaryVersions = update.binaryVersions.split(",");
        boolean matches = false;
        for (String version : binaryVersions) {
          if (version.equals(versionName)) {
            matches = true;
            break;
          }
        }
        if (!matches) {
          launchableUpdates.remove(update);
        }
      }
    }

    return mSelectionPolicy.selectUpdateToLaunch(launchableUpdates);
  }

  private File ensureAssetExists(AssetEntity asset, UpdatesDatabase database, Context context) {
    File assetFile = new File(mUpdatesDirectory, asset.relativePath);
    boolean assetFileExists = assetFile.exists();
    if (!assetFileExists) {
      // something has gone wrong, we're missing the launch asset
      // first we check to see if a copy is embedded in the binary
      Manifest embeddedManifest = EmbeddedLoader.readEmbeddedManifest(context);
      if (embeddedManifest != null) {
        ArrayList<AssetEntity> embeddedAssets = embeddedManifest.getAssetEntityList();
        AssetEntity matchingEmbeddedAsset = null;
        for (AssetEntity embeddedAsset : embeddedAssets) {
          if (embeddedAsset.url.equals(asset.url)) {
            matchingEmbeddedAsset = embeddedAsset;
            break;
          }
        }

        if (matchingEmbeddedAsset != null) {
          try {
            byte[] hash = EmbeddedLoader.copyAssetAndGetHash(matchingEmbeddedAsset, assetFile, context);
            if (hash != null && Arrays.equals(hash, asset.hash)) {
              assetFileExists = true;
            }
          } catch (Exception e) {
            // things are really not going our way...
          }
        }
      }
    }

    if (!assetFileExists) {
      // we still don't have the asset locally, so try downloading it remotely
      mAssetsToDownload++;
      FileDownloader.downloadAsset(asset, mUpdatesDirectory, context, new FileDownloader.AssetDownloadCallback() {
        @Override
        public void onFailure(Exception e, AssetEntity assetEntity) {
          Log.e(TAG, "Failed to load asset from disk or network", e);
          maybeFinish(assetEntity, null);
        }

        @Override
        public void onSuccess(AssetEntity assetEntity, boolean isNew) {
          database.assetDao().updateAsset(assetEntity);
          File assetFile = new File(mUpdatesDirectory, assetEntity.relativePath);
          maybeFinish(assetEntity, assetFile.exists() ? assetFile : null);
        }
      });
      return null;
    } else {
      return assetFile;
    }
  }

  private synchronized void maybeFinish(AssetEntity asset, File assetFile) {
    mAssetsToDownloadFinished++;
    if (asset.isLaunchAsset) {
      if (assetFile == null) {
        Log.e(TAG, "Could not launch; failed to load update from disk or network");
        mLaunchAssetFile = null;
      } else {
        mLaunchAssetFile = assetFile.toString();
      }
    } else {
      if (assetFile != null) {
        mLocalAssetFiles.put(
            asset.url.toString(),
            assetFile.toString()
        );
      }
    }

    if (mAssetsToDownloadFinished == mAssetsToDownload) {
      mCallback.onFinished();
    }
  }
}
