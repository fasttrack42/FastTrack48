# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable

# Profile and Gender were stored with Java serialization (Satchel), which records class and
# field names and the serialVersionUID. ProfileStore now keeps the profile in preferences and
# migrates an old file (or one an old backup restores) through Java serialization, so they stay
# kept as written. Data from earlier releases, written under R8's names, is read by shape in
# SafeRawSatchelSerializer.
-keep class com.legbehindneck.fasttrack48.data.Profile { *; }
-keep enum com.legbehindneck.fasttrack48.data.Gender { *; }
