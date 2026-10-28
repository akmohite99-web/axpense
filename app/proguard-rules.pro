# R8 / ProGuard rules for the release build.
# Room, WorkManager, Firebase and Compose ship their own consumer rules, so only
# app-specific needs are listed here.

# Keep line numbers so crash reports can be de-obfuscated with the release mapping.txt
# (app/build/outputs/mapping/release/mapping.txt; keep a copy for every shipped version).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager stores this class's NAME in its database when the hourly backup is scheduled.
# If R8 renamed it, work scheduled by an earlier version could no longer be started after an
# app update. Keep the name stable.
-keep class com.apex.axpense.work.BackupWorker { *; }
