package expo.modules.updates.db;

import android.util.Log;

import expo.modules.updates.launcher.SelectionPolicy;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class Reaper {

  private static String TAG = Reaper.class.getSimpleName();

  public static void reapUnusedUpdates(UpdatesDatabase database, File updatesDirectory, UpdateEntity launchedUpdate, SelectionPolicy selectionPolicy) {
    if (launchedUpdate == null) {
      Log.d(TAG, "Tried to reap while no update was launched; aborting");
      return;
    }

    List<UpdateEntity> allUpdates = database.updateDao().loadAllUpdates();
    List<UpdateEntity> markedUpdates = selectionPolicy.markUpdatesForDeletion(allUpdates, launchedUpdate);
    database.updateDao().updateUpdates(markedUpdates);
    List<AssetEntity> assetsToDelete = database.assetDao().markAndLoadAssetsForDeletion();

    LinkedList<AssetEntity> deletedAssets = new LinkedList<>();
    LinkedList<AssetEntity> erroredAssets = new LinkedList<>();

    for (AssetEntity asset : assetsToDelete) {
      if (!asset.markedForDeletion) {
        Log.e(TAG, "Tried to delete asset with URL " + asset.url + " but it was not marked for deletion");
        continue;
      }

      File path = new File(updatesDirectory, asset.relativePath);
      try {
        if (path.delete()) {
          deletedAssets.add(asset);
        } else {
          Log.e(TAG, "Failed to delete asset with URL " + asset.url);
          erroredAssets.add(asset);
        }
      } catch (Exception e) {
        Log.e(TAG, "Could not delete asset with URL " + asset.url, e);
        erroredAssets.add(asset);
      }
    }

    // retry failed deletions
    for (AssetEntity asset : erroredAssets) {
      File path = new File(updatesDirectory, asset.relativePath);
      try {
        if (path.delete()) {
          deletedAssets.add(asset);
          erroredAssets.remove(asset);
        } else {
          Log.e(TAG, "Retried deleting asset with URL " + asset.url + " and failed again");
        }
      } catch (Exception e) {
        Log.e(TAG, "Retried deleting asset with URL " + asset.url + " and errored again", e);
        erroredAssets.add(asset);
      }
    }

    database.assetDao().deleteAssets(deletedAssets);
    database.updateDao().deleteUnusedUpdates();
  }
}
