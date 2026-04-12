# =============================================================================
# Ori:Dev Wear — R8 / Proguard keep rules
# =============================================================================
# Wear-specific subset. No SSHJ / sora / termlib / ACRA needed here.
# =============================================================================

# -----------------------------------------------------------------------------
# Kotlin / Coroutines
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod
-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# kotlinx-coroutines-play-services
-keep class kotlinx.coroutines.tasks.** { *; }
-dontwarn kotlinx.coroutines.tasks.**

# -----------------------------------------------------------------------------
# Hilt / Dagger
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-dontwarn dagger.hilt.**

# -----------------------------------------------------------------------------
# Compose runtime
# -----------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# Wear Compose + Tiles
# -----------------------------------------------------------------------------
-keep class androidx.wear.compose.** { *; }
-keep interface androidx.wear.compose.** { *; }
-keep class androidx.wear.tiles.** { *; }
-keep interface androidx.wear.tiles.** { *; }
-keep class * extends androidx.wear.tiles.TileService { *; }
-dontwarn androidx.wear.**

# -----------------------------------------------------------------------------
# Play Services Wearable (DataClient, MessageClient, WearableListenerService)
# -----------------------------------------------------------------------------
-keep class com.google.android.gms.wearable.** { *; }
-keep interface com.google.android.gms.wearable.** { *; }
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-dontwarn com.google.android.gms.**

# -----------------------------------------------------------------------------
# Horologist
# -----------------------------------------------------------------------------
-keep class com.google.android.horologist.** { *; }
-keep interface com.google.android.horologist.** { *; }
-dontwarn com.google.android.horologist.**

# -----------------------------------------------------------------------------
# Project — Ori:Dev Wear
# -----------------------------------------------------------------------------
-keep class dev.ori.**.*ViewModel { <init>(...); }
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class dev.ori.**.UiState { *; }
-keep class dev.ori.**.UiState$* { *; }

# -----------------------------------------------------------------------------
# Misc suppressions
# -----------------------------------------------------------------------------
-dontwarn java.beans.**
-dontwarn javax.annotation.**
