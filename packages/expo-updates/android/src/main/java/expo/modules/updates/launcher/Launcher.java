package expo.modules.updates.launcher;

import java.util.Map;

import androidx.annotation.Nullable;
import expo.modules.updates.db.entity.UpdateEntity;

public interface Launcher {

  public interface LauncherCallback{
    public void onFailure(Exception e);
    public void onSuccess();
  }

  public @Nullable UpdateEntity getLaunchedUpdate();
  public @Nullable String getLaunchAssetFile();
  public @Nullable String getBundleAssetName();
  public @Nullable Map<String, String> getLocalAssetFiles();
}
