# Proguard rules for THRYNX
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class com.thrynx.app.core.model.** { *; }
-dontwarn androidx.media3.**
