# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.autobook.data.db.** { *; }
-keep class com.openloud.data.db.** { *; }
-dontwarn com.gemalto.jp2.JP2Decoder
