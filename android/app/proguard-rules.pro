# R8 rules for the release build. The libraries bring their own rules:
# kotlinx.serialization (companion serializers), OkHttp, coroutines, Tink
# (protobuf fields), and ML Kit barcode (protobuf fields and JNI names).

# Google Play retraces crash reports with the mapping file only when the
# file and line attributes stay in the dex.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libwebrtc 150.7871.01 ships no R8 rules. Its native code finds these
# classes and members by name over JNI. The release build has no live calls,
# so R8 removes WebRTC completely; this rule applies only when a build
# reaches PeerConnectionFactory.
-if class org.webrtc.PeerConnectionFactory
-keep class org.webrtc.** { *; }
