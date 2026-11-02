# Release builds are shrunk with R8. Native code finds these classes by name, so they keep
# their names and members:

# ONNX Runtime (all the device brain's models) calls back into its Java classes from C++
-keep class ai.onnxruntime.** { *; }



-keepclasseswithmembernames class * { native <methods>; }

# The QR scanner (pairing with the PC)
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Stack traces in crash reports still point at the right lines
-keepattributes SourceFile,LineNumberTable
