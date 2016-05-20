//
//  CouchbaseLite.m
//  CouchbaseLite
//
//  Created by James Nocentini on 02/12/2015.
//  Copyright © 2015 Couchbase. All rights reserved.
//

#import "ReactCBLite.h"

#import "RCTLog.h"

#import "CouchbaseLite/CouchbaseLite.h"
#import "CouchbaseLiteListener/CouchbaseLiteListener.h"
#import "CBLRegisterJSViewCompiler.h"

#define SYSTEM_VERSION_EQUAL_TO(v)                  ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedSame)
#define SYSTEM_VERSION_GREATER_THAN(v)              ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedDescending)
#define SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(v)  ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedAscending)
#define SYSTEM_VERSION_LESS_THAN(v)                 ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedAscending)
#define SYSTEM_VERSION_LESS_THAN_OR_EQUAL_TO(v)     ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedDescending)

@implementation ReactCBLite

NSString* const DB_CHANGED = @"couchBaseDBEvent";

RCT_EXPORT_MODULE()

- (id)init
{
    self = [super init];
    return self;
}

RCT_EXPORT_METHOD(init:(float)port username:(NSString *)username password:(NSString *)password callback:(RCTResponseSenderBlock)callback)
{
    NSLog(@"Launching Couchbase Lite...");
    CBLManager* dbmgr = [CBLManager sharedInstance];
    [CBLManager enableLogging: @"Sync"];
    CBLRegisterJSViewCompiler();
    
    CBLListener* listener = [[CBLListener alloc] initWithManager:dbmgr port:port];
    if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"9.0")) {
      [listener setPasswords:@{username: password}];
    }
    [listener start:nil];
    
    NSLog(@"Couchbase Lite url = %@", listener.URL);
    callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(monitorDatabase:(NSString *)databaseLocal callback:(RCTResponseSenderBlock)callback)
{
    NSError* error;
    CBLManager* dbmgr = [CBLManager sharedInstance];
    CBLDatabase* database = [dbmgr databaseNamed:databaseLocal error:&error];

    [[NSNotificationCenter defaultCenter] addObserverForName: kCBLDatabaseChangeNotification
                                                      object: database
                                                       queue: nil
                                                  usingBlock: ^(NSNotification *n) {
                                                      NSArray* changes = n.userInfo[@"changes"];
                                                      for (CBLDatabaseChange* change in changes) {
                                                          NSDictionary* map = @{
                                                                                @"databaseName": database.name,
                                                                                @"id": change.documentID
                                                                                };
                                                          [self.bridge.eventDispatcher sendAppEventWithName:DB_CHANGED body:map];
                                                          NSLog(@"Document '%@' changed.", change.documentID);
                                                      }
                                                  }
     ];
    
     callback(@[[NSNull null]]);
}

RCT_EXPORT_METHOD(copyDatabase:(NSString *)databaseLocal withPreBuildDatabase:(NSString *)withPreBuildDatabase callback:(RCTResponseSenderBlock)callback)
{
    NSError *error;
    CBLManager* dbManager = [CBLManager sharedInstance];
    CBLDatabase* database = [dbManager existingDatabaseNamed:databaseLocal error: &error];
    if (!database) {
        NSString* cannedDbPath = [[NSBundle mainBundle] pathForResource: withPreBuildDatabase
                                                                 ofType: @"cblite"];
        BOOL ok = [dbManager replaceDatabaseNamed: databaseLocal
                                 withDatabaseFile: cannedDbPath
                                  withAttachments: nil
                                            error: &error];
        if (!ok) {
            NSLog(@"database '%@' copy fail", databaseLocal);
            callback(@[@"copy fail"]);
        } else {
            database = [dbManager existingDatabaseNamed: @"catalog" error: &error];
            NSLog(@"database '%@' copy succ", databaseLocal);
            callback(@[[NSNull null]]);
        }
    } else {
        NSLog(@"database '%@' already exist", databaseLocal);
        callback(@[@"database already exist"]);
    }
}

@end
