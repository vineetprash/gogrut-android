## Rules for NewPipeExtractor (from its README)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

## Rhino / JDK compatibility
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Extractor reflects on a few JDK classes that R8 can't see on Android
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn javax.lang.model.element.Modifier

## LAME port uses reflection-free code, nothing to keep.