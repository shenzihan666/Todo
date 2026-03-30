# OkHttp
-dontwarn okhttp3.internal.platform.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# Kotlinx Serialization (DTOs used with Retrofit / WebSocket)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.todolist.app.**$$serializer { *; }
-keepclassmembers class com.todolist.app.** { *** Companion; }
-keepclasseswithmembers class com.todolist.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.todolist.app.data.speech.** { *; }
-keep @kotlinx.serialization.Serializable class com.todolist.app.data.network.dto.** { *; }
