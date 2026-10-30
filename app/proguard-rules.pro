# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable

# Keep Glance widget related classes
-keep public class * extends androidx.glance.appwidget.GlanceAppWidget
-keep public class * extends androidx.glance.appwidget.action.ActionCallback

# Profile and Gender are stored with Java serialization (Satchel), which records class and
# field names and the serialVersionUID. Kept as written from now on, so a release never
# renames them under data already stored. Data from earlier releases, written under R8's
# names, is read by shape in SafeRawSatchelSerializer.
-keep class com.legbehindneck.fasttrack48.data.Profile { *; }
-keep enum com.legbehindneck.fasttrack48.data.Gender { *; }
