package expo.modules.updates.launcher;

import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.db.enums.UpdateStatus;
import expo.modules.updates.launcher.SelectionPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Update selection policy which chooses
 * the newest update (based on commit time) out
 * of all the possible stored updates.
 *
 * If multiple updates have the same (most
 * recent) commit time, this class will return
 * the earliest one in the list.
 */
public class SelectionPolicyNewest implements SelectionPolicy {

  @Override
  public UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates) {
    UpdateEntity updateToLaunch = null;
    for (UpdateEntity update : updates) {
      if (updateToLaunch == null || updateToLaunch.commitTime.before(update.commitTime)) {
        updateToLaunch = update;
      }
    }
    return updateToLaunch;
  }

  @Override
  public List<UpdateEntity> markUpdatesForDeletion(List<UpdateEntity> updates, UpdateEntity launchedUpdate) {
    List<UpdateEntity> updatesToMark = new ArrayList<>();
    for (UpdateEntity update : updates) {
      if (launchedUpdate != null && update.commitTime.before(launchedUpdate.commitTime)) {
        update.status = UpdateStatus.UNUSED;
        update.keep = false;
        updatesToMark.add(update);
      }
      if (launchedUpdate != null && update.id.equals(launchedUpdate.id)) {
        update.keep = true;
        updatesToMark.add(update);
      }
    }
    return updatesToMark;
  }

  @Override
  public boolean shouldLoadNewUpdate(UpdateEntity newUpdate, UpdateEntity launchedUpdate) {
    if (launchedUpdate == null) {
      return true;
    }
    if (newUpdate == null) {
      return false;
    }
    return newUpdate.commitTime.after(launchedUpdate.commitTime);
  }
}
