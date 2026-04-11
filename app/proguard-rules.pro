-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.voicesearch.model.** { *; }
-keep interface com.voicesearch.provider.SearchProvider { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
