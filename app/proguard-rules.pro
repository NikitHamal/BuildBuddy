# BuildBuddy ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.build.buddyai.**$$serializer { *; }
-keepclassmembers class com.build.buddyai.** { *** Companion; }
-keepclasseswithmembers class com.build.buddyai.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Room entities
-keep class com.build.buddyai.core.data.local.entity.** { *; }

# Hilt / Dagger / ViewModels
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class **_HiltModules_* { *; }
-keep class Hilt_* { *; }
-keep class *_Factory { *; }
-keep class *_MembersInjector { *; }
-dontwarn dagger.hilt.internal.**

# Networking
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep model and network classes for serialization/parsing
-keep class com.build.buddyai.core.model.** { *; }
-keep class com.build.buddyai.core.network.** { *; }

# Keep Google Fonts provider
-keep class androidx.compose.ui.text.googlefonts.** { *; }

# Keep Errorprone annotations (for Tink)
-keep class com.google.errorprone.annotations.** { *; }
-dontwarn com.google.errorprone.annotations.**

# Keep Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
