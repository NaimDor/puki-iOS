# Release builds are shrunk with R8. Native code finds these classes by name, so they keep
# their names and members:

# ONNX Runtime (the voice and the pronunciation model) calls back into its Java classes from C++
-keep class ai.onnxruntime.** { *; }

# LiteRT-LM (Gemma) does the same, and reads its config classes over JNI
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

-keepclasseswithmembernames class * { native <methods>; }

# The QR scanner (pairing with the PC)
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Stack traces in crash reports still point at the right lines
-keepattributes SourceFile,LineNumberTable
