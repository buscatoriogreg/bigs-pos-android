# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.rgbpos.bigs.data.model.** { *; }
-keepclassmembers,allowobfuscation class * {
  @kotlinx.serialization.SerialName <fields>;
}

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
