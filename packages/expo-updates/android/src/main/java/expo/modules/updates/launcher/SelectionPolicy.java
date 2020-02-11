package expo.modules.updates.launcher;

import expo.modules.updates.db.entity.UpdateEntity;

import java.util.List;

public interface SelectionPolicy {
  UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates);
  boolean shouldLoadNewUpdate(UpdateEntity newUpdate, UpdateEntity launchedUpdate);
  List<UpdateEntity> markUpdatesForDeletion(List<UpdateEntity> updates, UpdateEntity launchedUpdate);
}
