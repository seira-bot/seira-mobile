# R8 rules for the release build. The libraries bring their own rules:
# kotlinx.serialization (companion serializers), OkHttp, coroutines, Tink
# (protobuf fields), and ML Kit barcode (protobuf fields and JNI names).

# Without LineNumberTable, R8 removes the line information, and a stack trace
# that Google Play retraces with mapping.txt has no line numbers.
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

# Glance creates the widget's answer callback by class name when a button is tapped.
-keep class com.openmausbot.companion.widget.WidgetAnswerAction { <init>(); }
