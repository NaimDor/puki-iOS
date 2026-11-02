# Release builds are shrunk with R8. Native code finds these classes by name, so they keep
# their names and members:

# ONNX Runtime (all the device brain's models) calls back into its Java classes from C++
-keep class ai.onnxruntime.** { *; }



-keepclasseswithmembernames class * { native <methods>; }

# Stack traces in crash reports still point at the right lines
-keepattributes SourceFile,LineNumberTable
