# ── Debug info ───────────────────────────────────────────────────────────────
# Preserve class + line number info so crash reports are readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson / Retrofit model classes ────────────────────────────────────────────
# Keep all network DTOs and model classes used by Gson for JSON deserialization.
# @SerializedName protects field-to-JSON-key mapping, but the classes and their
# default constructors must also be explicitly kept so R8 does not remove them.
-keep class com.example.vapecrib.network.** { *; }
-keep class com.example.vapecrib.model.** { *; }

# Preserve generic type signatures — required by Gson for TypeToken resolution
# (Retrofit consumer rules already include this, but be explicit)
-keepattributes Signature
-keepattributes *Annotation*

# ── Retrofit ─────────────────────────────────────────────────────────────────
# Retrofit's own consumer rules in its AAR handle most of this, but keep the
# service interface methods explicitly so R8 does not strip HTTP-annotated ones.
-keep,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── MPAndroidChart ───────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── Room database ────────────────────────────────────────────────────────────
-keep class com.example.vapecrib.data.db.** { *; }

# ── EncryptedSharedPreferences (Tink) ────────────────────────────────────────
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }