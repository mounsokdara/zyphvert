# FFmpegKitNext: keep JNI-bridged classes once the native module is wired in.
# -keep class com.arthenica.ffmpegkit.** { *; }

# Shizuku: the service is started by name in another process, so it must keep its name and the generated AIDL classes.
-keep class com.mounsokdara.zyphvert.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }

# metadata-extractor: optional desktop-only classes it can reference.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn com.drew.**
-dontwarn com.adobe.xmp.**

# Size: stronger R8 optimization.
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
