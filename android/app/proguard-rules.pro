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

# WorkManager 2.10.0 (a Glance dependency) creates WorkDatabase_Impl and its
# InputMerger through the no-argument constructor by reflection. Room and
# WorkManager keep only the classes, and R8 full mode then removes the
# constructors: the app crashes at start in androidx.startup.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
