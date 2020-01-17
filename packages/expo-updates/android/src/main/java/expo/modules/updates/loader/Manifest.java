package expo.modules.updates.loader;

import android.net.Uri;
import android.util.Log;

import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class Manifest {

  private static String TAG = Manifest.class.getSimpleName();

  private static String BUNDLE_FILENAME = "shell-app.bundle";
  private static String EXPO_ASSETS_URL_BASE = "https://d1wp6m56sqw74a.cloudfront.net/~assets/";

  private UUID mId;
  private Date mCommitTime;
  private String mBinaryVersions;
  private JSONObject mMetadata;
  private Uri mBundleUrl;
  private JSONArray mAssets;

  private JSONObject mManifestJson;

  private Manifest(JSONObject manifestJson, UUID id, Date commitTime, String binaryVersions, JSONObject metadata, Uri bundleUrl, JSONArray assets) {
    mManifestJson = manifestJson;
    mId = id;
    mCommitTime = commitTime;
    mBinaryVersions = binaryVersions;
    mMetadata = metadata;
    mBundleUrl = bundleUrl;
    mAssets = assets;
  }

  public static Manifest fromBareManifestJson(JSONObject manifestJson) throws JSONException {
    UUID id = UUID.fromString(manifestJson.getString("id"));
    Date commitTime = new Date(manifestJson.getLong("commitTime"));
    String binaryVersions = manifestJson.getString("binaryVersions");
    JSONObject metadata = manifestJson.optJSONObject("metadata");
    Uri bundleUrl = Uri.parse(manifestJson.getString("bundleUrl"));
    JSONArray assets = manifestJson.optJSONArray("assets");

    return new Manifest(manifestJson, id, commitTime, binaryVersions, metadata, bundleUrl, assets);
  }

  public static Manifest fromManagedManifestJson(JSONObject manifestJson) throws JSONException {
    UUID id = UUID.fromString(manifestJson.getString("releaseId"));
    String commitTimeString = manifestJson.getString("commitTime");
    String binaryVersions = manifestJson.getString("sdkVersion");
    JSONObject binaryVersionsObject = manifestJson.optJSONObject("binaryVersions");
    if (binaryVersionsObject != null) {
      binaryVersions = binaryVersionsObject.optString("android", binaryVersions);
    }
    Uri bundleUrl = Uri.parse(manifestJson.getString("bundleUrl"));

    Date commitTime;
    try {
      DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      commitTime = formatter.parse(commitTimeString);
    } catch (ParseException e) {
      Log.e(TAG, "Could not parse commitTime", e);
      commitTime = new Date();
    }

    JSONArray bundledAssets = manifestJson.optJSONArray("bundledAssets");
    JSONArray assets = null;
    if (bundledAssets != null && bundledAssets.length() > 0) {
      assets = new JSONArray();
      for (int i = 0; i < bundledAssets.length(); i++) {
        String bundledAsset = bundledAssets.getString(i);
        int extensionIndex = bundledAsset.lastIndexOf('.');
        int prefixLength = "asset_".length();
        String hash = extensionIndex > 0
            ? bundledAsset.substring(prefixLength, extensionIndex)
            : bundledAsset.substring(prefixLength);
        String type = extensionIndex > 0 ? bundledAsset.substring(extensionIndex + 1) : "";

      }
    }

    return new Manifest(manifestJson, id, commitTime, binaryVersions, manifestJson, bundleUrl, bundledAssets);
  }

  public JSONObject getRawManifestJson() {
    return mManifestJson;
  }

  public UpdateEntity getUpdateEntity() {
    UpdateEntity updateEntity = new UpdateEntity(mId, mCommitTime, mBinaryVersions);
    if (mMetadata != null) {
      updateEntity.metadata = mMetadata;
    }

    return updateEntity;
  }

  public ArrayList<AssetEntity> getAssetEntityList() {
    ArrayList<AssetEntity> assetList = new ArrayList<>();

    AssetEntity bundleAssetEntity = new AssetEntity(mBundleUrl, "js");
    bundleAssetEntity.isLaunchAsset = true;
    bundleAssetEntity.assetsFilename = BUNDLE_FILENAME;
    assetList.add(bundleAssetEntity);

    if (mAssets != null && mAssets.length() > 0) {
      if (mAssets.opt(0) instanceof String) {
        // process this as a managed manifest
        for (int i = 0; i < mAssets.length(); i++) {
          try {
            String bundledAsset = mAssets.getString(i);
            int extensionIndex = bundledAsset.lastIndexOf('.');
            int prefixLength = "asset_".length();
            String hash = extensionIndex > 0
                ? bundledAsset.substring(prefixLength, extensionIndex)
                : bundledAsset.substring(prefixLength);
            String type = extensionIndex > 0 ? bundledAsset.substring(extensionIndex + 1) : "";

            AssetEntity assetEntity = new AssetEntity(Uri.parse(EXPO_ASSETS_URL_BASE + hash), type);
            assetEntity.assetsFilename = bundledAsset;
            assetList.add(assetEntity);
          } catch (JSONException e) {
            Log.e(TAG, "Could not read asset from manifest", e);
          }
        }
      } else {
        for (int i = 0; i < mAssets.length(); i++) {
          try {
            JSONObject assetObject = mAssets.getJSONObject(i);
            AssetEntity assetEntity = new AssetEntity(
                Uri.parse(assetObject.getString("url")),
                assetObject.getString("type")
            );
            assetEntity.assetsFilename = assetObject.optString("assetsFilename");
            assetList.add(assetEntity);
          } catch (JSONException e) {
            Log.e(TAG, "Could not read asset from manifest", e);
          }
        }

      }
    }

    return assetList;
  }
}
