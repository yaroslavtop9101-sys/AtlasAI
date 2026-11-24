# default rules

# MediaPipe (локальная модель)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.proto.**
-dontwarn com.google.protobuf.**
