# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# R8 Optimization & Obfuscation Settings
-repackageclasses ''
-allowaccessmodification
-dontusemixedcaseclassnames

# Obfuscate source file names while preserving line numbers for crash reporting
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# OkHttp optional dependencies
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Moshi rules (allow class obfuscation since code generation handles serialization)
-keepclasseswithmembers,allowobfuscation class * {
    @com.squareup.moshi.JsonClass <init>(...);
}
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class * extends com.squareup.moshi.JsonAdapter
