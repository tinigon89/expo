package expo.modules.updates.launcher;

import expo.modules.updates.db.entity.UpdateEntity;

import java.util.List;

public interface SelectionPolicy {
  public UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates);
  public boolean shouldLoadNewUpdate(UpdateEntity newUpdate, UpdateEntity launchedUpdate);
  public List<UpdateEntity> markUpdatesForDeletion(List<UpdateEntity> updates, UpdateEntity launchedUpdate);
}
