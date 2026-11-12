
-keep class com.mounsokdara.zyphvert.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }

-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn com.drew.**
-dontwarn com.adobe.xmp.**

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
