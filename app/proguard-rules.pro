# R8 rules for the release build. Room and Compose ship their own consumer rules, so nothing app-specific is needed
# yet. Add rules here only when a release build fails or crashes.

# Keep line numbers so crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable

# Glance creates widget button callbacks by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
