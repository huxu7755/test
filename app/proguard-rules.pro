# Default ProGuard rules for TodoApp

# Keep Room entities
-keep class com.marvis.todoapp.data.** { *; }

# Keep data classes
-keepattributes *Annotation*

# General Android
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
