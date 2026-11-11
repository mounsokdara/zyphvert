# FFmpegKitNext: keep JNI-bridged classes once the native module is wired in.
# -keep class com.arthenica.ffmpegkit.** { *; }

# Shizuku: the service is started by name in another process, so it must keep its name and the generated AIDL classes.
-keep class com.mounsokdara.zyphvert.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
