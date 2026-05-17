# Compose
-dontwarn androidx.compose.**

# Coil
-dontwarn coil.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# Jsoup pulls in a soft SLF4J reference for an optional StaticLoggerBinder.
# We don't ship SLF4J — silence the warning so R8 minification can proceed.
-dontwarn org.slf4j.**
-dontwarn org.jsoup.**
