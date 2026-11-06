# Firestore & Room model reflection rules
-keepclassmembers class com.example.data.model.** {
    public <init>();
    <fields>;
    public *** get*();
    public void set*(***);
}

-keepclassmembers class com.example.data.local.** {
    public <init>();
    <fields>;
    public *** get*();
    public void set*(***);
}

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-dontwarn androidx.room.paging.**

# Moshi & Retrofit
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn com.squareup.moshi.**
-dontwarn retrofit2.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep useful source locations in the mapping/retraced crash reports.
-keepattributes SourceFile,LineNumberTable
