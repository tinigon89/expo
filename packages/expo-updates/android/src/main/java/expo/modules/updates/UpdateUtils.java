package expo.modules.updates;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.Nullable;
import expo.modules.updates.db.entity.AssetEntity;

public class UpdateUtils {

  private static final String TAG = UpdateUtils.class.getSimpleName();
  private static final String UPDATES_DIRECTORY_NAME = ".expo-internal";

  public static File getOrCreateUpdatesDirectory(Context context) throws Exception {
    File updatesDirectory = new File(context.getFilesDir(), UPDATES_DIRECTORY_NAME);
    boolean exists = updatesDirectory.exists();
    if (exists) {
      if (updatesDirectory.isFile()) {
        throw new Exception("File already exists at the location of the Updates Directory: " + updatesDirectory.toString() + " ; aborting");
      }
    } else {
      if (!updatesDirectory.mkdir()) {
        throw new Exception("Failed to create Updates Directory: mkdir() returned false");
      }
    }
    return updatesDirectory;
  }

  public static String sha256(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] data = string.getBytes("UTF-8");
      md.update(data, 0, data.length);
      byte[] sha1hash = md.digest();
      return bytesToHex(sha1hash);
    } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
      Log.e(TAG, "Failed to checksum string via SHA-256", e);
      throw e;
    }
  }

  public static byte[] sha256(File file) throws NoSuchAlgorithmException, IOException {
    try (
        InputStream inputStream = new FileInputStream(file);
        DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-256"))
    ) {
      MessageDigest md = digestInputStream.getMessageDigest();
      return md.digest();
    } catch (NoSuchAlgorithmException | IOException e) {
      Log.e(TAG, "Failed to checksum file via SHA-256: " + file.toString(), e);
      throw e;
    }
  }

  public static @Nullable byte[] sha256AndWriteToFile(InputStream inputStream, File destination) throws NoSuchAlgorithmException, IOException {
    boolean fileWriteSuccess = false;
    try (
      DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-256"))
    ) {
      // write file atomically by writing it to a temporary path and then renaming
      // this protects us against partially written files if the process is interrupted
      File tmpFile = new File(destination.getAbsolutePath() + ".tmp");
      FileUtils.copyInputStreamToFile(digestInputStream, tmpFile);
      fileWriteSuccess = tmpFile.renameTo(destination);
      if (!fileWriteSuccess) {
        throw new IOException("File download was successful, but failed to move from temporary to permanent location " + destination.getAbsolutePath());
      }

      MessageDigest md = digestInputStream.getMessageDigest();
      return md.digest();
    } catch (NoSuchAlgorithmException | IOException | NullPointerException e) {
      if (fileWriteSuccess) {
        // if the file was written successfully and we just couldn't hash it, return null
        // and let the caller handle having a null hash
        Log.e(TAG, "Failed to checksum file via SHA-256: " + destination.toString(), e);
        return null;
      } else {
        // otherwise, the file was not written successfully so we need to throw
        throw e;
      }
    }
  }

  public static String createFilenameForAsset(AssetEntity asset) {
    String base;
    try {
      base = sha256(asset.url.toString());
    } catch (Exception e) {
      // fall back to returning a uri-encoded string if we can't do SHA-256 for some reason
      base = Uri.encode(asset.url.toString());
    }
    return base + "." + asset.type;
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
