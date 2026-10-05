#import "Telecom.h"

@implementation Telecom

- (void)setup:(NSDictionary *)options
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
{
  reject(@"E_IOS_UNSUPPORTED", @"react-native-telecom v0.x supports Android only. iOS CallKit is coming later.", nil);
}

- (void)displayIncomingCall:(NSString *)uuid
                     handle:(NSString *)handle
                 callerName:(NSString *)callerName
                   hasVideo:(BOOL)hasVideo
                 autoAnswer:(BOOL)autoAnswer
{
}

- (void)startCall:(NSString *)uuid
           handle:(NSString *)handle
       callerName:(NSString *)callerName
         hasVideo:(BOOL)hasVideo
{
}

- (void)answerCall:(NSString *)uuid
{
}

- (void)endCall:(NSString *)uuid
{
}

- (void)endAllCalls
{
}

- (void)setMuted:(NSString *)uuid muted:(BOOL)muted
{
}

- (void)setOnHold:(NSString *)uuid hold:(BOOL)hold
{
}

- (void)addListener:(NSString *)eventName
{
}

- (void)removeListeners:(double)count
{
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeTelecomSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"Telecom";
}

@end
