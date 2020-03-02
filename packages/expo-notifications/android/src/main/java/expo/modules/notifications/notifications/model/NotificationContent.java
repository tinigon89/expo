package expo.modules.notifications.notifications.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;
import expo.modules.notifications.notifications.enums.NotificationPriority;

/**
 * A POJO representing a notification content: title, message, body, etc. Instances
 * should be created using {@link NotificationContent.Builder}.
 */
public class NotificationContent implements Parcelable {
  private String mTitle;
  private String mText;
  private String mSubtitle;
  private Number mBadgeCount;
  private boolean mShouldPlayDefaultSound;
  private Uri mSound;
  private boolean mShouldUseDefaultVibrationPattern;
  private long[] mVibrationPattern;
  private JSONObject mBody;
  private NotificationPriority mPriority;

  protected NotificationContent() {
  }

  public static final Creator<NotificationContent> CREATOR = new Creator<NotificationContent>() {
    @Override
    public NotificationContent createFromParcel(Parcel in) {
      return new NotificationContent(in);
    }

    @Override
    public NotificationContent[] newArray(int size) {
      return new NotificationContent[size];
    }
  };

  @Nullable
  public String getTitle() {
    return mTitle;
  }

  @Nullable
  public String getText() {
    return mText;
  }

  @Nullable
  public String getSubtitle() {
    return mSubtitle;
  }

  @Nullable
  public Number getBadgeCount() {
    return mBadgeCount;
  }

  public boolean shouldPlayDefaultSound() {
    return mShouldPlayDefaultSound;
  }

  @Nullable
  public Uri getSound() {
    return mSound;
  }

  public boolean shouldUseDefaultVibrationPattern() {
    return mShouldUseDefaultVibrationPattern;
  }

  @Nullable
  public long[] getVibrationPattern() {
    return mVibrationPattern;
  }

  @Nullable
  public JSONObject getBody() {
    return mBody;
  }

  @Nullable
  public NotificationPriority getPriority() {
    return mPriority;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  protected NotificationContent(Parcel in) {
    mTitle = in.readString();
    mText = in.readString();
    mSubtitle = in.readString();
    mBadgeCount = (Number) in.readSerializable();
    mShouldPlayDefaultSound = in.readByte() != 0;
    mSound = in.readParcelable(getClass().getClassLoader());
    mShouldUseDefaultVibrationPattern = in.readByte() != 0;
    mVibrationPattern = in.createLongArray();
    try {
      mBody = new JSONObject(in.readString());
    } catch (JSONException | NullPointerException e) {
      // do nothing
    }
    Number priorityNumber = (Number) in.readSerializable();
    if (priorityNumber != null) {
      mPriority = NotificationPriority.fromNativeValue(priorityNumber.intValue());
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(mTitle);
    dest.writeString(mText);
    dest.writeString(mSubtitle);
    dest.writeSerializable(mBadgeCount);
    dest.writeByte((byte) (mShouldPlayDefaultSound ? 1 : 0));
    dest.writeParcelable(mSound, 0);
    dest.writeByte((byte) (mShouldUseDefaultVibrationPattern ? 1 : 0));
    dest.writeLongArray(mVibrationPattern);
    dest.writeString(mBody != null ? mBody.toString() : null);
    dest.writeSerializable(mPriority != null ? mPriority.getNativeValue() : null);
  }

  public static class Builder {
    private String mTitle;
    private String mText;
    private String mSubtitle;
    private Number mBadgeCount;
    private boolean mShouldPlayDefaultSound;
    private Uri mSound;
    private boolean mShouldUseDefaultVibrationPattern;
    private long[] mVibrationPattern;
    private JSONObject mBody;
    private NotificationPriority mPriority;

    public Builder() {
      useDefaultSound();
      useDefaultVibrationPattern();
    }

    public Builder setTitle(String title) {
      mTitle = title;
      return this;
    }

    public Builder setSubtitle(String subtitle) {
      mSubtitle = subtitle;
      return this;
    }

    public Builder setText(String text) {
      mText = text;
      return this;
    }

    public Builder setBody(JSONObject body) {
      mBody = body;
      return this;
    }

    public Builder setPriority(NotificationPriority priority) {
      mPriority = priority;
      return this;
    }

    public Builder setBadgeCount(Number badgeCount) {
      mBadgeCount = badgeCount;
      return this;
    }

    public Builder useDefaultVibrationPattern() {
      mShouldUseDefaultVibrationPattern = true;
      mVibrationPattern = null;
      return this;
    }

    public Builder setVibrationPattern(long[] vibrationPattern) {
      mShouldUseDefaultVibrationPattern = false;
      mVibrationPattern = vibrationPattern;
      return this;
    }

    public Builder useDefaultSound() {
      mShouldPlayDefaultSound = true;
      mSound = null;
      return this;
    }

    public Builder setSound(Uri sound) {
      mShouldPlayDefaultSound = false;
      mSound = sound;
      return this;
    }

    public NotificationContent build() {
      NotificationContent content = new NotificationContent();
      content.mTitle = mTitle;
      content.mSubtitle = mSubtitle;
      content.mText = mText;
      content.mBadgeCount = mBadgeCount;
      content.mShouldUseDefaultVibrationPattern = mShouldUseDefaultVibrationPattern;
      content.mVibrationPattern = mVibrationPattern;
      content.mShouldPlayDefaultSound = mShouldPlayDefaultSound;
      content.mSound = mSound;
      content.mBody = mBody;
      content.mPriority = mPriority;
      return content;
    }
  }
}
