# =============================================================================
# Ori:Dev — R8 / Proguard keep rules (release minification)
# =============================================================================
# Organized per library. Do NOT add blanket `-keep class dev.ori.** { *; }` —
# that defeats the purpose of minification. If R8 strips something, add a
# targeted rule here.
# =============================================================================

# -----------------------------------------------------------------------------
# Kotlin / Coroutines
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod
-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    <init>(...);
}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.flow.**.internal.**

# kotlinx-coroutines-play-services
-keep class kotlinx.coroutines.tasks.** { *; }
-dontwarn kotlinx.coroutines.tasks.**

# Serialization (if used transitively)
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# -----------------------------------------------------------------------------
# SSHJ (com.hierynomus:sshj)
# -----------------------------------------------------------------------------
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.schmizz.sshj.transport.kex.** { *; }
-keep class net.schmizz.sshj.transport.cipher.** { *; }
-keep class net.schmizz.sshj.transport.mac.** { *; }
-keep class net.schmizz.sshj.transport.compression.** { *; }
-keep class net.schmizz.sshj.signature.** { *; }
-keep class net.schmizz.sshj.userauth.method.** { *; }
-keep class net.schmizz.sshj.userauth.keyprovider.** { *; }
-keep class net.schmizz.sshj.common.** { *; }
-keep class net.schmizz.sshj.transport.random.** { *; }

-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.slf4j.**
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn com.jcraft.**

# -----------------------------------------------------------------------------
# BouncyCastle
# -----------------------------------------------------------------------------
-keep class org.bouncycastle.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keepclassmembers class org.bouncycastle.** {
    <init>(...);
}
-dontwarn org.bouncycastle.**

# -----------------------------------------------------------------------------
# Apache Commons Net (FTP)
# -----------------------------------------------------------------------------
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn javax.naming.**

# -----------------------------------------------------------------------------
# OkHttp / Okio (Proxmox)
# -----------------------------------------------------------------------------
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# -----------------------------------------------------------------------------
# Moshi (including codegen)
# -----------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-dontwarn com.squareup.moshi.**

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-dontwarn androidx.room.paging.**

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
-keep class **_Impl { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.android.internal.lifecycle.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-dontwarn dagger.hilt.**

# -----------------------------------------------------------------------------
# Sora-Editor + TextMate + snakeyaml
# -----------------------------------------------------------------------------
-keep class io.github.rosemoe.sora.** { *; }
-keep interface io.github.rosemoe.sora.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.snakeyaml.** { *; }
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn io.github.rosemoe.sora.**
-dontwarn org.eclipse.tm4e.**
-dontwarn org.snakeyaml.**
-dontwarn org.yaml.snakeyaml.**

# -----------------------------------------------------------------------------
# ConnectBot termlib
# -----------------------------------------------------------------------------
-keep class org.connectbot.** { *; }
-keep class de.mud.terminal.** { *; }
-dontwarn org.connectbot.**
-dontwarn de.mud.terminal.**

# -----------------------------------------------------------------------------
# java-diff-utils
# -----------------------------------------------------------------------------
-keep class com.github.difflib.** { *; }
-dontwarn com.github.difflib.**

# -----------------------------------------------------------------------------
# Coil 3
# -----------------------------------------------------------------------------
-keep class coil3.** { *; }
-keep interface coil3.** { *; }
-dontwarn coil3.**

# -----------------------------------------------------------------------------
# Play Services Wearable
# -----------------------------------------------------------------------------
-keep class com.google.android.gms.wearable.** { *; }
-keep interface com.google.android.gms.wearable.** { *; }
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-keep class * extends androidx.wear.tiles.TileService { *; }
-keep class * extends com.google.android.gms.common.api.GoogleApiClient { *; }
-dontwarn com.google.android.gms.**

# -----------------------------------------------------------------------------
# Horologist
# -----------------------------------------------------------------------------
-keep class com.google.android.horologist.** { *; }
-keep interface com.google.android.horologist.** { *; }
-dontwarn com.google.android.horologist.**

# -----------------------------------------------------------------------------
# ACRA (crash reporting)
# -----------------------------------------------------------------------------
-keep class org.acra.** { *; }
-keep interface org.acra.** { *; }
-keep @org.acra.annotation.AcraCore class * { *; }
-keep @org.acra.annotation.AcraHttpSender class * { *; }
-keep @org.acra.annotation.AcraMailSender class * { *; }
-keep @org.acra.annotation.AcraDialog class * { *; }
-keep @org.acra.annotation.AcraNotification class * { *; }
-keep @org.acra.annotation.AcraToast class * { *; }
-keep @org.acra.annotation.AcraScheduler class * { *; }
-keep @org.acra.annotation.AcraLimiter class * { *; }
-keepclassmembers class * {
    @org.acra.annotation.* <fields>;
}
-keep class * implements org.acra.sender.ReportSenderFactory { *; }
-keep class * implements org.acra.sender.ReportSender { *; }
-keep class * implements org.acra.collector.Collector { *; }
-dontwarn org.acra.**

# -----------------------------------------------------------------------------
# Compose runtime
# -----------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# DataStore
# -----------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
-keep interface androidx.datastore.** { *; }
-keepclassmembers class * implements androidx.datastore.core.Serializer {
    <init>(...);
    <methods>;
}
-dontwarn androidx.datastore.**

# -----------------------------------------------------------------------------
# WorkManager + hilt-work
# -----------------------------------------------------------------------------
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class androidx.hilt.work.HiltWorkerFactory { *; }
-keep class androidx.hilt.work.** { *; }
-keep class **_AssistedFactory { *; }
-keep class **_AssistedFactory$* { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keepclassmembers class * {
    @androidx.hilt.work.HiltWorker <init>(...);
    @dagger.assisted.AssistedInject <init>(...);
}
-keep class * implements androidx.hilt.work.WorkerAssistedFactory { *; }
-dontwarn androidx.work.**
-dontwarn androidx.hilt.work.**

# -----------------------------------------------------------------------------
# AndroidX Startup (used by profileinstaller, workmanager)
# -----------------------------------------------------------------------------
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer { *; }

# -----------------------------------------------------------------------------
# Project — Ori:Dev
# -----------------------------------------------------------------------------
# ViewModels need a public constructor for Hilt/factory instantiation.
-keep class dev.ori.**.*ViewModel { <init>(...); }
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *

# UiState sealed classes — accessed reflectively by Compose state holders
# and serialized for rememberSaveable.
-keep class dev.ori.**.UiState { *; }
-keep class dev.ori.**.UiState$* { *; }

# ACRA config + scrubber in app/crash/ — reflected from attachBaseContext
-keep class dev.ori.app.crash.** { *; }

# -----------------------------------------------------------------------------
# Misc suppressions
# -----------------------------------------------------------------------------
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.StringConcatFactory
