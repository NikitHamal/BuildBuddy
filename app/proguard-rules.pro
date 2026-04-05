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

# ECJ (Eclipse Java Compiler) - on-device build pipeline
# ECJ references JDK and JDT Core APIs that don't exist on Android.
# We only use ECJ's batch compiler at runtime, so all missing references are safe to suppress.
-dontwarn javax.tools.**
-dontwarn javax.annotation.processing.**
-dontwarn com.sun.source.**
-dontwarn org.eclipse.jdt.core.**
-dontwarn org.eclipse.jdt.internal.compiler.apt.**
-dontwarn org.eclipse.jdt.internal.compiler.tool.**
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$*
-dontwarn org.eclipse.jdt.internal.compiler.SourceElementNotifier
-dontwarn org.eclipse.jdt.internal.compiler.ExtraFlags

# Keep javax.lang.model stubs for ECJ runtime
-keep class javax.lang.model.** { *; }

# Keep only the ECJ batch compiler entry point used at runtime
-keep class org.eclipse.jdt.internal.compiler.batch.Main { *; }

# D8/R8 compiler (runs in-process on the device)
-keep class com.android.tools.r8.** { *; }
-dontwarn com.android.tools.r8.**

# BouncyCastle (APK signing)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
