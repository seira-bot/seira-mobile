# R8 rules for the release build. The libraries bring their own rules:
# kotlinx.serialization (companion serializers), OkHttp, coroutines, Tink
# (protobuf fields), and ML Kit barcode (protobuf fields and JNI names).

# Google Play retraces crash reports with the mapping file only when the
# file and line attributes stay in the dex.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit creates each ComponentRegistrar that its manifest names through the
# no-argument constructor. firebase-components 16.1.0 keeps only the class,
# and R8 full mode then removes the constructor: BarcodeScanning.getClient()
# throws a NullPointerException and the QR scanner shows "Scanner unavailable".
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

# libwebrtc 150.7871.01 ships no R8 rules. Its native code finds these
# classes and members by name over JNI.
-keep class org.webrtc.** { *; }
