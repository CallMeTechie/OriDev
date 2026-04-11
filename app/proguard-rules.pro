# SSHJ
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
