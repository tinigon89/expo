package expo.modules.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UpdateUtils {

  private static final String TAG = UpdateUtils.class.getSimpleName();
  private static final String UPDATES_DIRECTORY_NAME = ".expo-internal";

  public static String getBinaryVersion(Context context) {
    String versionName = null;
    try {
      PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      versionName = pInfo.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Could not determine binary version", e);
    }
    return versionName;
  }

  public static File getOrCreateUpdatesDirectory(Context context) {
    File updatesDirectory = new File(context.getFilesDir(), UPDATES_DIRECTORY_NAME);
    boolean exists = updatesDirectory.exists();
    boolean isFile = updatesDirectory.isFile();
    if (!exists || isFile) {
      if (isFile) {
        if (!updatesDirectory.delete()) {
          throw new AssertionError("Updates directory should not be a file");
        }
      }

      if (!updatesDirectory.mkdir()) {
        throw new AssertionError("Updates directory must exist or be able to be created");
      }
    }
    return updatesDirectory;
  }

  public static String sha1(String string) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] data = string.getBytes("UTF-8");
      md.update(data, 0, data.length);
      byte[] sha1hash = md.digest();
      return bytesToHex(sha1hash);
    } catch (Exception e) {
      Log.e(TAG, "Could not encode via SHA-1", e);
    }
    // fall back to returning a uri-encoded string if we can't do SHA-1 for some reason
    return Uri.encode(string);
  }

  public static byte[] sha1(File file) throws NoSuchAlgorithmException, IOException {
    try (
        InputStream inputStream = new FileInputStream(file);
        DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-1"))
    ) {
      MessageDigest md = digestInputStream.getMessageDigest();
      return md.digest();
    } catch (NoSuchAlgorithmException | IOException e) {
      Log.e(TAG, "Failed to hash asset " + file.toString(), e);
      throw e;
    }
  }

  // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }
}
