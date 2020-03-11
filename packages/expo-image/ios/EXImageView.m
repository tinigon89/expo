// Copyright 2020-present 650 Industries. All rights reserved.

#import <expo-image/EXImageView.h>
#import <React/RCTConvert.h>

@implementation EXImageView

- (void)setSource:(NSDictionary *)source
{
  NSURL *imageUrl = [RCTConvert NSURL:source[@"uri"]];

  [self sd_setImageWithURL:imageUrl];
}

@end
