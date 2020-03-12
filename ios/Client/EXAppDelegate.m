// Copyright 2015-present 650 Industries. All rights reserved.

@import ObjectiveC;

#import "EXBuildConstants.h"
#import "EXAppDelegate.h"

//#import <Crashlytics/Crashlytics.h>
#import <Fabric/Fabric.h>
#import <EXTaskManager/EXTaskService.h>
#import <UMCore/UMModuleRegistryProvider.h>

#import "ExpoKit.h"
#import "EXRootViewController.h"
#import "EXConstants.h"

NS_ASSUME_NONNULL_BEGIN

@interface ExpoKit ()

@end

@implementation EXAppDelegate

@synthesize window = _window;

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(nullable NSDictionary *)launchOptions
{
  // CrashlyticsKit.delegate = [ExpoKit sharedInstance]; // this must be set prior to init'ing fabric.
  // [Fabric with:@[CrashlyticsKit]];
  // [CrashlyticsKit setObjectValue:[EXBuildConstants sharedInstance].expoRuntimeVersion forKey:@"exp_client_version"];

  if ([application applicationState] != UIApplicationStateBackground) {
    // App launched in foreground
    [self _setUpUserInterfaceForApplication:application withLaunchOptions:launchOptions];
  }

 [super application:application didFinishLaunchingWithOptions:launchOptions];

  return YES;
}

- (void)applicationWillEnterForeground:(UIApplication *)application
{
  [self _setUpUserInterfaceForApplication:application withLaunchOptions:nil];

  [super applicationWillEnterForeground:application];
}

- (void)_setUpUserInterfaceForApplication:(UIApplication *)application withLaunchOptions:(nullable NSDictionary *)launchOptions
{
  if (_window) {
    return;
  }
  [[ExpoKit sharedInstance] registerRootViewControllerClass:[EXRootViewController class]];
  [[ExpoKit sharedInstance] application:application didFinishLaunchingWithOptions:launchOptions];

  _window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
  _window.backgroundColor = [UIColor whiteColor];
  _rootViewController = (EXRootViewController *)[ExpoKit sharedInstance].rootViewController;
  _window.rootViewController = _rootViewController;

  [_window makeKeyAndVisible];
}

@end

NS_ASSUME_NONNULL_END
