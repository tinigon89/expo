package expo.modules.updates.loader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import expo.modules.updates.R;
import expo.modules.updates.UpdateUtils;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import expo.modules.updates.db.entity.AssetEntity;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FileDownloader {

  private static final String TAG = FileDownloader.class.getSimpleName();

  private static OkHttpClient sClient = new OkHttpClient.Builder().build();

  public interface FileDownloadCallback {
    void onFailure(Exception e);
    void onSuccess(File file, byte[] hash);
  }

  public interface ManifestDownloadCallback {
    void onFailure(String message, Exception e);
    void onSuccess(Manifest manifest);
  }

  public interface AssetDownloadCallback {
    void onFailure(Exception e, AssetEntity assetEntity);
    void onSuccess(AssetEntity assetEntity, boolean isNew);
  }

  public static void downloadFileToPath(Request request, final File destination, final FileDownloadCallback callback) {
    downloadData(request, new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        callback.onFailure(e);
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          callback.onFailure(new Exception("Network request failed: " + response.body().string()));
          return;
        }

        try (
            InputStream inputStream = response.body().byteStream();
            DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-1"));
        ) {
          FileUtils.copyInputStreamToFile(digestInputStream, destination);

          MessageDigest md = digestInputStream.getMessageDigest();
          byte[] data = md.digest();
          callback.onSuccess(destination, data);
        } catch (NoSuchAlgorithmException | NullPointerException e) {
          Log.e(TAG, "Could got get SHA-1 hash of file", e);
          callback.onSuccess(destination, null);
        }
      }
    });
  }

  public static void downloadManifest(final Uri url, Context context, final ManifestDownloadCallback callback) {
    FileDownloader.downloadData(FileDownloader.addHeadersToManifestUrl(url, context), new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        callback.onFailure("Failed to download manifest from uri: " + url, e);
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          callback.onFailure("Failed to download manifest from uri: " + url, new Exception(response.body().string()));
          return;
        }

        try {
          String manifestString = response.body().string();
          JSONObject manifestJson = new JSONObject(manifestString);
          if (manifestJson.has("manifestString") && manifestJson.has("signature")) {
            final String innerManifestString = manifestJson.getString("manifestString");
            Crypto.verifyPublicRSASignature(
                innerManifestString,
                manifestJson.getString("signature"),
                new Crypto.RSASignatureListener() {
                  @Override
                  public void onError(Exception e, boolean isNetworkError) {
                    callback.onFailure("Could not validate signed manifest", e);
                  }

                  @Override
                  public void onCompleted(boolean isValid) {
                    if (isValid) {
                      try {
                        Manifest manifest = Manifest.fromManagedManifestJson(new JSONObject(innerManifestString));
                        callback.onSuccess(manifest);
                      } catch (JSONException e) {
                        callback.onFailure("Failed to parse manifest data", e);
                      }
                    } else {
                      callback.onFailure("Manifest signature is invalid; aborting", new Exception("Manifest signature is invalid"));
                    }
                  }
                }
            );
          } else {
            Manifest manifest = Manifest.fromManagedManifestJson(manifestJson);
            callback.onSuccess(manifest);
          }
        } catch (Exception e) {
          callback.onFailure("Failed to parse manifest data", e);
        }
      }
    });
  }

  public static void downloadAsset(final AssetEntity asset, File destinationDirectory, Context context, final AssetDownloadCallback callback) {
    final String filename = UpdateUtils.sha1(asset.url.toString()) + "." + asset.type;
    File path = new File(destinationDirectory, filename);

    if (path.exists()) {
      asset.relativePath = filename;
      callback.onSuccess(asset, false);
    } else {
      FileDownloader.downloadFileToPath(FileDownloader.addHeadersToUrl(asset.url, context), path, new FileDownloader.FileDownloadCallback() {
        @Override
        public void onFailure(Exception e) {
          callback.onFailure(e, asset);
        }

        @Override
        public void onSuccess(File file, byte[] hash) {
          asset.downloadTime = new Date();
          asset.relativePath = filename;
          asset.hash = hash;
          callback.onSuccess(asset, true);
        }
      });
    }
  }

  public static void downloadData(Request request, Callback callback) {
    downloadData(request, callback, false);
  }

  private static void downloadData(final Request request, final Callback callback, final boolean isRetry) {
    sClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        if (isRetry) {
          callback.onFailure(call, e);
        } else {
          downloadData(request, callback, true);
        }
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        callback.onResponse(call, response);
      }
    });
  }

  private static Request addHeadersToUrl(Uri url, Context context) {
    Request.Builder requestBuilder = new Request.Builder()
            .url(url.toString())
            .header("Expo-Platform", "android")
            .header("Expo-Api-Version", "1")
            .header("Expo-Client-Environment", "STANDALONE");

    String binaryVersion = UpdateUtils.getBinaryVersion(context);
    if (binaryVersion != null) {
      requestBuilder = requestBuilder.header("Expo-Binary-Version", binaryVersion)
              .header("Expo-SDK-Version", binaryVersion);
    }
    return requestBuilder.build();
  }

  private static Request addHeadersToManifestUrl(Uri url, Context context) {
    Request.Builder requestBuilder = new Request.Builder()
            .url(url.toString())
            .header("Accept", "application/expo+json,application/json")
            .header("Expo-Platform", "android")
            .header("Expo-JSON-Error", "true")
            .header("Expo-Accept-Signature", "true")
            .header("Expo-Release-Channel", context.getString(R.string.expo_release_channel))
            .cacheControl(CacheControl.FORCE_NETWORK);

    String binaryVersion = UpdateUtils.getBinaryVersion(context);
    if (binaryVersion != null) {
      requestBuilder = requestBuilder.header("Expo-Binary-Version", binaryVersion)
          // TODO: fix this
              .header("Expo-SDK-Version", "36.0.0");
    }
    return requestBuilder.build();
  }
}
