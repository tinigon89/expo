# expo-notifications

Provides an API to fetch push notification tokens and to present, schedule, receive and respond to notifications.

# API documentation

The following methods are exported by the `expo-notifications` module:

- [`getExpoPushTokenAsync`](#getexpopushtokenasync) -- resolves with an Expo push token
- [`getDevicePushTokenAsync`](#getdevicepushtokenasync) -- resolves with a device push token
- [`addPushTokenListener`](#addpushtokenlistener) -- adds a listener called when a new push token is issued
- [`removePushTokenSubscription`](#removepushtokensubscription) -- removes the listener registered with `addPushTokenListener`
- [`removeAllPushTokenListeners`](#removeallpushtokenlisteners) -- removes all listeners registered with `addPushTokenListener`
- [`addNotificationReceivedListener`](#addnotificationreceivedlistener) -- adds a listener called whenever a new notification is received
- [`addNotificationsDroppedListener`](#addnotificationsdroppedlistener) -- adds a listener called whenever some notifications have been dropped
- [`addNotificationResponseReceivedListener`](#addnotificationresponsereceivedlistener) -- adds a listener called whenever user interacts with a notification
- [`removeNotificationSubscription`](#removenotificationsubscription) -- removes the listener registered with `addNotification*Listener()`
- [`removeAllNotificationListeners`](#removeAllNotificationListeners) -- removes all listeners registered with `addNotification*Listener()`
- [`setNotificationHandler`](#setnotificationhandler) -- sets the handler function responsible for deciding what to do with a notification that is received when the app is in foreground
- [`getPermissionsAsync`](#getpermissionsasync) -- fetches current permission settings related to notifications
- [`requestPermissionsAsync`](#requestpermissionsasync) -- requests permissions related to notifications

# Installation in managed Expo projects

This library is not yet usable within managed projects &mdash; it is likely to be included in an upcoming Expo SDK release.

# Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `react-native-unimodules` package](https://github.com/unimodules/react-native-unimodules) before continuing.

### Add the package to your npm dependencies

```
expo install expo-notifications
```

### Configure for iOS

Run `pod install` in the `ios` directory after installing the npm package.

### Configure for Android

Ensure that your project is configured for Firebase (if it is, it should contain a `google-services.json` file in `android/app`).

# Contributing

Contributions are very welcome! Please refer to guidelines described in the [contributing guide](https://github.com/expo/expo#contributing).

------

### `getExpoPushTokenAsync(options: ExpoTokenOptions): ExpoPushToken`

Returns an Expo token that can be used to send a push notification to this device using Expo push notifications service. [Read more in the Push Notifications guide](https://docs.expo.io/versions/latest/guides/push-notifications/).

> **Note:** For Expo backend to be able to send notifications to your app, you will need to provide it with push notification keys. This can be done using `expo-cli` (`expo credentials:manager`). [Read more in the TODO guide](#todo).

#### Arguments

This function accepts an optional object allowing you to pass in configuration, consisting of fields (all are optional, but some may have to be defined if configuration cannot be inferred):

- **experienceId (_string_)** -- The ID of the experience to which the token should be attributed. Defaults to [`Constants.manifest.id`](https://docs.expo.io/versions/latest/sdk/constants/#constantsmanifest) exposed by `expo-constants`. You may need to define it in bare workflow, where `expo-constants` doesn't expose the manifest.
- **devicePushToken ([_DevicePushToken_](#devicepushtoken))** -- The device push token with which to register at the backend. Defaults to a token fetched with [`getDevicePushTokenAsync()`](#getdevicepushtokenasync).
- **appId (_string_)** -- The ID of the application to which the token should be attributed. Defaults to [`Application.applicationId`](https://docs.expo.io/versions/latest/sdk/application/#applicationapplicationid) exposed by `expo-application`.
- **deviceId (_string_)** -- The ID of the application installation. `expo-notifications` is capable of generating one for you and it defaults to it. Most probably you won't need to customize that.
- **type (_string_)** -- Type of token sent to the server. Inferred from `devicePushToken`. Most probably you won't need to customize that.
- **development (_boolean_)** -- Makes sense only on iOS, where there are two push notification services: sandbox and production. This defines whether the push token is supposed to be used with the sandbox platform notification service. Defaults to [`Application.getIosPushNotificationServiceEnvironmentAsync()`](https://docs.expo.io/versions/latest/sdk/application/#applicationgetiospushnotificationserviceenvironmentasync) exposed by `expo-application` or `false`. Most probably you won't need to customize that.
- **baseUrl (_string_)** -- Base URL upon which the function will build a URL to which it will send the device push token. If `url` is defined, this option won't have any effect. Defaults to a production Expo backend URL. Most probably you won't need to change that.
- **url (_string_)** -- Endpoint URL to which the function will send the device push token. Most probably you won't need to customize that. Setting `url` overrides `baseUrl`.

#### Returns

Returns a `Promise` that resolves to an object with the following fields:

- **type (_string_)** -- Always `expo`.
- **data (_string_)** -- The push token as a string.

#### Examples

##### Fetching the Expo push token and uploading it to a server

```ts
import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';

export async function registerForPushNotificationsAsync(userId: string) {
  let experienceId = undefined;
  if (!Constants.manifest) {
    // Absence of the manifest means we're in bare workflow
    experienceId = '@username/example';
  }
  const expoPushToken = await Notifications.getExpoPushTokenAsync({
    experienceId,
  });
  await fetch('https://example.com/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      userId,
      expoPushToken,
    }),
  });
}
```

### `getDevicePushTokenAsync(): DevicePushToken`

Returns a native APNS, FCM token or a [`PushSubscription` data](https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription) that can be used with another push notification service.

#### Returns

A `Promise` that resolves to an object with the following fields:

- **type (_string_)** -- Either `ios`, `android` or `web`.
- **data (_string_ or _object_)** -- Either the push token as a string (for `type == "ios" | "android"`) or an object conforming to the type below (for `type == "web"`):
  ```ts
  {
    endpoint: string;
    keys: {
      p256dh: string;
      auth: string;
    }
  }
  ```

### `addPushTokenListener(listener: PushTokenListener): Subscription`

In rare situations a push token may be changed while the app is running. When a token is rolled, the old one becomes invalid and sending notifications to it will fail. A push token listener will let you handle this situation gracefully.

#### Arguments

A single and required argument is a function accepting a push token as an argument. It will be called whenever the push token changes.

#### Returns

A [`Subscription`](#subscription) object representing the subscription of the provided listener.

#### Examples

Registering a push token listener using a React hook

```tsx
import React from 'react';
import * as Notifications from 'expo-notifications';

import { registerDevicePushTokenAsync } from '../api';

export default function App() {
  React.useEffect(() => {
    const subscription = Notifications.addPushTokenListener(registerDevicePushTokenAsync);
    return () => subscription.remove();
  }, []);

  return (
    // Your app content
  );
}
```

### `removePushTokenSubscription(subscription: Subscription): void`

Removes a push token subscription returned by a `addPushTokenListener` call.

#### Arguments

A single and required argument is a subscription returned by `addPushTokenListener`.

### `removeAllPushTokenListeners(): void`

Removes all push token subscriptions that may have been registered with `addPushTokenListener`.

### `addNotificationReceivedListener(listener: (event: Notification) => void): void`

Listeners registered by this method will be called whenever a notification is received while the app is running.

#### Arguments

A single and required argument is a function accepting a notification ([`Notification`](#notification)) as an argument.

#### Returns

A [`Subscription`](#subscription) object representing the subscription of the provided listener.

#### Examples

Registering a notification listener using a React hook

```tsx
import React from 'react';
import * as Notifications from 'expo-notifications';

export default function App() {
  React.useEffect(() => {
    const subscription = Notifications.addNotificationReceivedListener(notification => {
      console.log(notification);
    });
    return () => subscription.remove();
  }, []);

  return (
    // Your app content
  );
}
```

### `addNotificationsDroppedListener(listener: () => void): void`

Listeners registered by this method will be called whenever some notifications have been dropped by the server. Applicable only to Firebase Cloud Messaging which we use as notifications service on Android. It corresponds to `onDeletedMessages()` callback. [More information can be found in Firebase docs](https://firebase.google.com/docs/cloud-messaging/android/receive#override-ondeletedmessages).

#### Arguments

A single and required argument is a function–callback.

#### Returns

A [`Subscription`](#subscription) object representing the subscription of the provided listener.

### `addNotificationResponseReceivedListener(listener: (event: NotificationResponse) => void): void`

Listeners registered by this method will be called whenever a user interacts with a notification (eg. taps on it).

#### Arguments

A single and required argument is a function accepting notification response ([`NotificationResponse`](#notificationresponse)) as an argument.

#### Returns

A [`Subscription`](#subscription) object representing the subscription of the provided listener.

#### Examples

Registering a notification listener using a React hook

```tsx
import React from 'react';
import { Linking } from 'react-native';
import * as Notifications from 'expo-notifications';

export default function Container({ navigation }) {
  React.useEffect(() => {
    const subscription = Notifications.addNotificationResponseReceivedListener(response => {
      const url = response.notification.request.content.data.url;
      Linking.openUrl(url);
    });
    return () => subscription.remove();
  }, [navigation]);

  return (
    // Your app content
  );
}
```

### `removeNotificationSubscription(subscription: Subscription): void`

Removes a notification subscription returned by a `addNotification*Listener` call.

#### Arguments

A single and required argument is a subscription returned by `addNotification*Listener`.

### `removeAllNotificationListeners(): void`

Removes all notification subscriptions that may have been registered with `addNotification*Listener`.

### `setNotificationHandler(handler: NotificationHandler | null): void`

When a notification is received while the app is running, using this function you can set a callback that will decide whether the notification should be shown to the user or not.

When a notification is received, `handleNotification` is called with the incoming notification as an argument. The function should respond with a behavior object within 3 seconds, otherwise the notification will be discarded. If the notification is handled successfully, `handleSuccess` is called with the identifier of the notification, otherwise (or on timeout) `handleError` will be called.

#### Arguments

The function receives a single argument which should be either `null` (if you want to clear the handler) or an object of fields:

- **handleNotification (_(Notification) => Promise<NotificationBehavior>_**) -- (required) a function accepting an incoming notification returning a `Promise` resolving to a behavior ([`NotificationBehavior`](#notificationbehavior)) applicable to the notification
- **handleSuccess (_(notificationId: string) => void_)** -- (optional) a function called whenever an incoming notification is handled successfully
- **handleError (_(error: Error) => void_)** -- (optional) a function called whenever handling of an incoming notification fails

#### Examples

Implementing a notification handler that always shows the notification when it is received

```ts
import * as Notifications from 'expo-notifications';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});
```

### `getPermissionsAsync(): Promise<NotificationPermissionsStatus>`

Calling this function checks current permissions settings related to notifications. It lets you verify whether the app is currently allowed to display alerts, play sounds, etc. There is no user-facing effect of calling this.

#### Returns

It returns a `Promise` resolving to an object representing permission settings (`NotificationPermissionsStatus`).

#### Examples

Check if the app is allowed to send any type of notifications (interrupting and non-interrupting–provisional on iOS)

```ts
import * as Notifications from 'expo-notifications';

export function allowsNotificationsAsync() {
  const settings = await Notifications.getPermissionsAsync();
  return (
    settings.granted || settings.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL
  );
}
```

### `requestPermissionsAsync(request?: NotificationPermissionsRequest): Promise<NotificationPermissionsStatus>`

Prompts the user for notification permissions according to request. Request defaults to asking the user to allow displaying alerts, setting badge count and playing sounds.

#### Arguments

An optional object of conforming to the following interface:

```ts
{
  android?: {};
  ios?: {
    allowAlert?: boolean;
    allowBadge?: boolean;
    allowSound?: boolean;
    allowDisplayInCarPlay?: boolean;
    allowCriticalAlerts?: boolean;
    provideAppNotificationSettings?: boolean;
    allowProvisional?: boolean;
    allowAnnouncements?: boolean;
  }
}
```

Each option corresponds to a different native platform authorization option (a list of iOS options is available [here](https://developer.apple.com/documentation/usernotifications/unauthorizationoptions), on Android all available permissions are granted by default and if a user declines any permission an app can't prompt the user to change).

#### Returns

It returns a `Promise` resolving to an object representing permission settings (`NotificationPermissionsStatus`).

#### Examples

Prompts the user to allow the app to show alerts, play sounds, set badge count and let Siri read out messages through AirPods

```ts
import * as Notifications from 'expo-notifications';

export function requestPermissionsAsync() {
  return await Notifications.requestPermissionsAsync({
    ios: {
      allowAlert: true,
      allowBadge: true,
      allowSound: true,
      allowAnnouncements: true,
    },
  });
}
```
