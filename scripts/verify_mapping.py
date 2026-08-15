#!/usr/bin/env python3
"""Assert R8 preserved what must be preserved, by reading its own mapping file.

Why this exists
---------------
Everything cheap in the ladder proves the app COMPILES. The obvious way to prove
minification is correct -- instrumented tests against the release variant -- was
tried and abandoned: `connectedReleaseAndroidTest` hangs with no output until the
job timeout (run 31875004036, both API levels).

But R8 already emits a complete record of what it did. mapping.txt has been
published on every release of this app and never read. It is the authoritative
answer to "did R8 rename or remove this", and it costs milliseconds.

What it checks
--------------
1. Classes named in AndroidManifest.xml must map to THEMSELVES. Android
   instantiates them by string, so a rename is a launch crash that compiles
   perfectly -- the same class of failure as a missing @HiltAndroidApp.

2. Fields of @Serializable classes must keep their names. They are the JSON wire
   format; renaming them produces an app that runs, serializes, and silently
   writes documents nothing else can read.

Reading mapping.txt
-------------------
    com.example.Foo -> a.b.c:            <- renamed
    com.example.Foo -> com.example.Foo:  <- kept
        java.lang.String label -> label      <- field kept
        java.lang.String label -> a          <- field renamed
A class absent from the file entirely was removed by shrinking.
"""
import re
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def parse_mapping(path):
    """-> {original_class: (new_class, {original_field: new_field})}"""
    classes = {}
    current = None
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if not line[0].isspace():
                m = re.match(r"^(\S+) -> (\S+):", line)
                if m:
                    current = m.group(1)
                    classes[current] = (m.group(2), {})
            elif current:
                # "    <type> <name> -> <newname>"  (methods carry parentheses)
                m = re.match(r"^\s+\S+ (\w+) -> (\w+)$", line.rstrip())
                if m:
                    classes[current][1][m.group(1)] = m.group(2)
    return classes


def manifest_classes(manifest_path, package):
    """Every class Android instantiates by name from the manifest."""
    root = ET.parse(manifest_path).getroot()
    names = []
    for tag in ("application", "activity", "service", "receiver", "provider"):
        for el in root.iter(tag):
            n = el.get(ANDROID_NS + "name")
            if n:
                names.append(package + n if n.startswith(".") else n)
    return names


def serializable_classes(src_root):
    """Class names carrying @Serializable, found by source scan."""
    import pathlib
    found = []
    for f in pathlib.Path(src_root).rglob("*.kt"):
        text = f.read_text(encoding="utf-8", errors="replace")
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not pkg:
            continue
        for m in re.finditer(r"@Serializable\s+(?:data\s+)?class\s+(\w+)", text):
            found.append(f"{pkg.group(1)}.{m.group(1)}")
    return found


def main():
    mapping_path, manifest_path, src_root, package = sys.argv[1:5]
    mapping = parse_mapping(mapping_path)
    print(f"mapping.txt: {len(mapping)} classes")

    failures = []

    for cls in manifest_classes(manifest_path, package):
        if cls not in mapping:
            failures.append(f"{cls}: REMOVED by R8, but the manifest instantiates it by name")
        elif mapping[cls][0] != cls:
            failures.append(f"{cls}: RENAMED to {mapping[cls][0]}, but the manifest names it by string")
        else:
            print(f"  ok  manifest class kept: {cls}")

    # Classes that MUST be present in the shipped build. Passed as extra argv.
    #
    # This exists because the first run of this checker found that R8 had deleted
    # ItemDto outright from v0.0.2: nothing in the app used it, so it was dead code.
    # Correct R8 behaviour, and it silently voided the conformance suite's entire R8
    # claim -- a keep rule cannot preserve a class that was shrunk away, and a test
    # asserting "minification did not break serialization" proves nothing when the
    # serialized type is not in the artifact.
    required = sys.argv[5:]
    for cls in required:
        if cls not in mapping:
            failures.append(
                f"{cls}: REQUIRED but absent from the release build -- R8 shrank it away "
                f"as unused, so any claim about minifying it is vacuous"
            )
        else:
            print(f"  ok  required class present: {cls}")

    for cls in serializable_classes(src_root):
        if cls not in mapping:
            print(f"  --  @Serializable {cls} not in mapping (shrunk away; unused)")
            continue
        renamed = {k: v for k, v in mapping[cls][1].items() if k != v}
        if renamed:
            failures.append(f"{cls}: @Serializable fields renamed {renamed} -- this IS the JSON wire format")
        else:
            kept = list(mapping[cls][1])
            print(f"  ok  @Serializable fields kept: {cls} {kept}")

    print()
    if failures:
        for f in failures:
            print(f"::error::{f}")
        print(f"FAIL: {len(failures)} minification problem(s)")
        return 1
    print("PASS: R8 preserved everything that must survive.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
