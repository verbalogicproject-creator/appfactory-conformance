# R8 runs on every release build (isMinifyEnabled = true).
#
# This file must EXIST even when empty: `proguardFiles` naming a file that was never
# created fails the build, and that exact omission cost a full CI round trip once.

# Keep source file and line numbers so a crash from a shipped build can be retraced
# against the published mapping.txt. Without these, a release stack trace has no line
# information and mapping.txt cannot recover it.
-keepattributes SourceFile,LineNumberTable
# Then hide the original file name, which would otherwise leak through.
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization -------------------------------------------------------
# Without these, R8 strips or renames the generated serializer and the @Serializable
# class's field names. The build stays green, unit tests on the JVM still pass
# (no R8 there), and JSON parsing fails only at runtime in the release build.
#
# This is why the conformance app round-trips a DTO in an INSTRUMENTED test against
# the release variant -- a unit test alone passes with these rules deleted.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
