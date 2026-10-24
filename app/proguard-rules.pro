# kotlinx.serialization — keep generated serializers for route assets and nav routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.campus.bustracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Firebase RTDB (when wired) maps POJOs reflectively — keep contract models intact.
-keep class com.campus.bustracker.data.model.** { *; }

# osmdroid
-dontwarn org.osmdroid.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
