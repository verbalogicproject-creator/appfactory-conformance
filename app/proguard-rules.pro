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
# DELIBERATELY REMOVED on this branch.
#
# This is the conformance suite's negative test for the emulator rung. With the keep
# rules gone, R8 renames the @Serializable class's fields and strips the generated
# serializer. Nothing cheap notices:
#
#   preflight   PASS  nothing structural is wrong
#   compile     PASS  the Kotlin is valid
#   unit tests  PASS  R8 does not run for JVM unit tests
#   R8/bundle   PASS  minification succeeds; it just removes the wrong things
#   emulator    must FAIL, against the release build
#
# If the emulator run goes GREEN on this branch, that rung is decorative and the
# claim that it catches runtime-only failures is false.
