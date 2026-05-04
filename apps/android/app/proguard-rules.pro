# Add project specific ProGuard rules here.
-keep class com.sleek.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class io.socket.** { *; }
-keep class org.json.** { *; }
