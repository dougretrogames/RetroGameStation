# Keep all native JNI method bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve canonical domain models, injectors, and interfaces
-keep class org.retrostation.core.** { *; }
-keep class org.retrogamestation.core.** { *; }
-keep class com.retro.gamestation.** { *; }

# Preserve native engine bridge implementations and callbacks
-keep class org.retrostation.engine.libretro.** { *; }
-keep class org.retrostation.engine.ppsspp.** { *; }
-keep class org.retrostation.engine.mupen64plus.** { *; }

# Preserve Views and SurfaceView subclasses for rendering callbacks
-keep public class * extends android.view.SurfaceView
-keep public class * extends android.view.View

# Preserve JNI Direct ByteBuffer interactions
-keepclassmembers class java.nio.ByteBuffer { *; }
