/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import <UIKit/UIKit.h>

#import <ReactABI34_0_0/ABI34_0_0RCTBridgeModule.h>
#import <ReactABI34_0_0/ABI34_0_0RCTConvert.h>
#import <ReactABI34_0_0/ABI34_0_0RCTDefines.h>
#import <ReactABI34_0_0/ABI34_0_0RCTEventDispatcher.h>
#import <ReactABI34_0_0/ABI34_0_0RCTLog.h>
#import <ReactABI34_0_0/UIView+ReactABI34_0_0.h>

@class ABI34_0_0RCTBridge;
@class ABI34_0_0RCTShadowView;
@class ABI34_0_0RCTSparseArray;
@class ABI34_0_0RCTUIManager;

typedef void (^ABI34_0_0RCTViewManagerUIBlock)(ABI34_0_0RCTUIManager *uiManager, NSDictionary<NSNumber *, UIView *> *viewRegistry);

@interface ABI34_0_0RCTViewManager : NSObject <ABI34_0_0RCTBridgeModule>

/**
 * The bridge can be used to access both the ABI34_0_0RCTUIIManager and the ABI34_0_0RCTEventDispatcher,
 * allowing the manager (or the views that it manages) to manipulate the view
 * hierarchy and send events back to the JS context.
 */
@property (nonatomic, weak) ABI34_0_0RCTBridge *bridge;

/**
 * This method instantiates a native view to be managed by the module. Override
 * this to return a custom view instance, which may be preconfigured with default
 * properties, subviews, etc. This method will be called many times, and should
 * return a fresh instance each time. The view module MUST NOT cache the returned
 * view and return the same instance for subsequent calls.
 */
- (UIView *)view;

/**
 * This method instantiates a shadow view to be managed by the module. If omitted,
 * an ordinary ABI34_0_0RCTShadowView instance will be created, which is typically fine for
 * most view types. As with the -view method, the -shadowView method should return
 * a fresh instance each time it is called.
 */
- (ABI34_0_0RCTShadowView *)shadowView;

/**
 * DEPRECATED: declare properties of type ABI34_0_0RCTBubblingEventBlock instead
 *
 * Returns an array of names of events that can be sent by native views. This
 * should return bubbling, directly-dispatched event types. The event name
 * should not include a prefix such as 'on' or 'top', as this will be applied
 * as needed. When subscribing to the event, use the 'Captured' suffix to
 * indicate the captured form, or omit the suffix for the bubbling form.
 *
 * Note that this method is not inherited when you subclass a view module, and
 * you should not call [super customBubblingEventTypes] when overriding it.
 */
- (NSArray<NSString *> *)customBubblingEventTypes __deprecated_msg("Use ABI34_0_0RCTBubblingEventBlock props instead.");

/**
 * This handles the simple case, where JS and native property names match.
 */
#define ABI34_0_0RCT_EXPORT_VIEW_PROPERTY(name, type) \
+ (NSArray<NSString *> *)propConfig_##name ABI34_0_0RCT_DYNAMIC { return @[@#type]; }

/**
 * This macro maps a named property to an arbitrary key path in the view.
 */
#define ABI34_0_0RCT_REMAP_VIEW_PROPERTY(name, keyPath, type) \
+ (NSArray<NSString *> *)propConfig_##name ABI34_0_0RCT_DYNAMIC { return @[@#type, @#keyPath]; }

/**
 * This macro can be used when you need to provide custom logic for setting
 * view properties. The macro should be followed by a method body, which can
 * refer to "json", "view" and "defaultView" to implement the required logic.
 */
#define ABI34_0_0RCT_CUSTOM_VIEW_PROPERTY(name, type, viewClass) \
ABI34_0_0RCT_REMAP_VIEW_PROPERTY(name, __custom__, type)         \
- (void)set_##name:(id)json forView:(viewClass *)view withDefaultView:(viewClass *)defaultView ABI34_0_0RCT_DYNAMIC

/**
 * This macro is used to map properties to the shadow view, instead of the view.
 */
#define ABI34_0_0RCT_EXPORT_SHADOW_PROPERTY(name, type) \
+ (NSArray<NSString *> *)propConfigShadow_##name ABI34_0_0RCT_DYNAMIC { return @[@#type]; }

/**
 * This macro maps a named property to an arbitrary key path in the shadow view.
 */
#define ABI34_0_0RCT_REMAP_SHADOW_PROPERTY(name, keyPath, type) \
+ (NSArray<NSString *> *)propConfigShadow_##name ABI34_0_0RCT_DYNAMIC { return @[@#type, @#keyPath]; }

/**
 * This macro can be used when you need to provide custom logic for setting
 * shadow view properties. The macro should be followed by a method body, which can
 * refer to "json" and "view".
 */
#define ABI34_0_0RCT_CUSTOM_SHADOW_PROPERTY(name, type, viewClass) \
ABI34_0_0RCT_REMAP_SHADOW_PROPERTY(name, __custom__, type)         \
- (void)set_##name:(id)json forShadowView:(viewClass *)view ABI34_0_0RCT_DYNAMIC

@end
