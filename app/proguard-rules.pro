# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
-keep class com.bitchat.android.protocol.** { *; }
-keep class com.bitchat.android.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Keep SecureIdentityStateManager from being obfuscated to prevent reflection issues
-keep class com.bitchat.android.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# Keep all classes that might use reflection
-keep class com.bitchat.android.favorites.** { *; }
-keep class com.bitchat.android.nostr.** { *; }
-keep class com.bitchat.android.identity.** { *; }

# Keep Tor implementation (always included)
-keep class com.bitchat.android.net.RealTorProvider { *; }

# Arti (Custom Tor implementation in Rust) ProGuard rules
-keep class info.guardianproject.arti.** { *; }
-keep class org.torproject.arti.** { *; }
-keepnames class org.torproject.arti.**
-dontwarn info.guardianproject.arti.**
-dontwarn org.torproject.arti.**

# LiteRT-LM — on-device Gemma 4 inference.
# The native library (liblitertlm_jni.so) resolves these Kotlin classes, their
# constructors and their callback methods by name across the JNI boundary, and
# the runtime additionally uses kotlin-reflect. If R8 renames or strips any of
# it, the offline AI engine fails at load time with a NoSuchMethod/UnsatisfiedLink
# error that only shows up in a release build.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** {
    native <methods>;
    <init>(...);
}
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn kotlin.reflect.**

# Fix for AbstractMethodError on API < 29 where LocationListener methods are abstract
-keepclassmembers class * implements android.location.LocationListener {
    public <methods>;
}
