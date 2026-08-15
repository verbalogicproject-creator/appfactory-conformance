#!/usr/bin/env bash
# Preflight checks for terminal-only Android development.
#
# On an aarch64 phone there is no usable Android SDK, so GitHub Actions is the
# compiler and every mistake costs a ~3 minute round trip. These are the checks
# that need no SDK and catch the failure classes this repo has actually hit.
#
# Usage: bash scripts/preflight.sh   (run from repo root, before every push)

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

FAIL=0
note() { printf '  %s\n' "$1"; }
fail() { printf '\033[31mFAIL\033[0m %s\n' "$1"; FAIL=1; }
pass() { printf '\033[32mok\033[0m   %s\n' "$1"; }

# ---------------------------------------------------------------------------
# 1. Every libs.* alias used in a build file exists in the version catalog.
#     Catches: the missing-dependency class of error, at the alias level.
# ---------------------------------------------------------------------------
CATALOG=gradle/libs.versions.toml
if [ -f "$CATALOG" ]; then
  missing_alias=0
  # Gradle maps dashes in the catalog to dots in accessors: a-b-c -> libs.a.b.c
  declared=$(sed -n '/^\[libraries\]/,/^\[/p' "$CATALOG" \
             | grep -oP '^[a-zA-Z0-9_-]+(?=\s*=)' | tr '-' '.' | sort -u)
  used=$(grep -rhoP '\blibs\.\K[a-zA-Z0-9.]+' --include='*.gradle.kts' . \
         | grep -v '^plugins\.' | grep -v '^versions\.' | sort -u)
  for u in $used; do
    if ! printf '%s\n' "$declared" | grep -qx "$u"; then
      fail "build file uses libs.$u but it is not in $CATALOG [libraries]"
      missing_alias=1
    fi
  done
  [ $missing_alias -eq 0 ] && pass "all libs.* aliases resolve in the version catalog"
else
  note "no version catalog at $CATALOG, skipping alias check"
fi

# ---------------------------------------------------------------------------
# 2. Files named by the build actually exist.
#     Catches: proguardFiles pointing at a nonexistent proguard-rules.pro,
#              google-services plugin applied with no google-services.json.
# ---------------------------------------------------------------------------
missing_file=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  if [ ! -f "app/$f" ] && [ ! -f "$f" ]; then
    fail "build.gradle.kts references '$f' but no such file exists"
    missing_file=1
  fi
done < <(perl -0777 -ne 'while (/proguardFiles?\s*\((.*?)\)\s*$/gms) {
             my $b = $1; push @f, $b =~ /"([^"]+)"/g; }
           print "$_\n" for @f' app/build.gradle.kts 2>/dev/null \
         | grep -v '^proguard-android')

if grep -q 'google.services' app/build.gradle.kts 2>/dev/null; then
  if [ ! -f app/google-services.json ]; then
    fail "google-services plugin is applied but app/google-services.json is absent"
    missing_file=1
  fi
fi
[ $missing_file -eq 0 ] && pass "every build-referenced file is present"

# ---------------------------------------------------------------------------
# 3. Manifest resource references resolve.
#     Catches: AAPT 'resource mipmap/ic_launcher not found' after an icon purge.
# ---------------------------------------------------------------------------
MANIFEST=app/src/main/AndroidManifest.xml
if [ -f "$MANIFEST" ]; then
  missing_res=0
  while IFS= read -r ref; do
    [ -z "$ref" ] && continue
    kind=${ref%%/*}; name=${ref##*/}
    if ! find app/src/main/res -type d -name "$kind*" \
         -exec sh -c 'ls "$1" 2>/dev/null | grep -q "^'"$name"'\."' _ {} \; \
         -print 2>/dev/null | grep -q . ; then
      # also allow the resource to be declared in values/*.xml
      if ! grep -rqE "name=\"$name\"" app/src/main/res/values 2>/dev/null; then
        fail "$MANIFEST references @$kind/$name but no such resource exists"
        missing_res=1
      fi
    fi
  done < <(grep -oP '"@\K(mipmap|drawable|style|string|color|xml)/[a-zA-Z0-9_.]+' "$MANIFEST" | sort -u)
  [ $missing_res -eq 0 ] && pass "all manifest resource references resolve"
fi

# ---------------------------------------------------------------------------
# 4. Internal imports point at symbols that still exist.
#     Catches: the stale-import-after-delete class (e.g. a removed screen).
# ---------------------------------------------------------------------------
# Test sources count: `./gradlew build` compiles them, so a stale test import
# breaks CI exactly like a stale main import does.
SRC_DIRS=$(ls -d app/src/main/java app/src/test/java app/src/androidTest/java 2>/dev/null)
if [ -n "${SRC_DIRS:-}" ]; then
  missing_sym=0
  while IFS= read -r imp; do
    [ -z "$imp" ] && continue
    sym=${imp##*.}
    # Wildcards, lowercase (package-level) imports, and build-generated
    # symbols (R, BuildConfig, Hilt/Room codegen) are not source declarations.
    case "$sym" in
      *\*) continue;;
      [a-z]*) continue;;
      R|BuildConfig) continue;;
      Hilt_*|*_Factory|*_Impl|*_HiltModules) continue;;
    esac
    if ! grep -rqE "(class|object|interface|enum class|fun|val|const val) +$sym\b" \
         $SRC_DIRS 2>/dev/null; then
      fail "import $imp resolves to no declaration in app/src (stale import?)"
      missing_sym=1
    fi
  done < <(grep -rhoP '^import \Kcom\.[a-zA-Z0-9_.]+\*?' $SRC_DIRS | sort -u)
  [ $missing_sym -eq 0 ] && pass "all internal imports resolve to a declaration"
fi

# ---------------------------------------------------------------------------
# 5. Third-party import groups have a matching declared dependency.
#     Catches exactly this repo's lifecycle-runtime-compose failure: an
#     androidx.lifecycle.compose.* import with no lifecycle-*-compose artifact.
# ---------------------------------------------------------------------------
if [ -f "$CATALOG" ]; then
  missing_dep=0
  coords=$(grep -oP 'group\s*=\s*"\K[^"]+|name\s*=\s*"\K[^"]+' "$CATALOG" | tr '\n' ' ')
  while IFS= read -r imp; do
    [ -z "$imp" ] && continue
    # androidx.lifecycle.compose.foo -> require an artifact matching both
    # 'lifecycle' and 'compose'.
    a=$(printf '%s' "$imp" | cut -d. -f2)
    b=$(printf '%s' "$imp" | cut -d. -f3)
    case "$b" in ""|[A-Z]*) continue;; esac
    # Only sub-packages that ship as their OWN artifact are checkable this way.
    # androidx.compose.foundation.* is skipped deliberately: it arrives
    # transitively via material3 and is legitimately undeclared.
    case "$a.$b" in
      lifecycle.compose|activity.compose|navigation.compose \
      |hilt.navigation|paging.compose|constraintlayout.compose \
      |lifecycle.viewmodel) ;;
      *) continue;;
    esac
    # Require an artifact whose name ties BOTH segments together, e.g.
    # lifecycle-runtime-compose. A bare "compose" match is not enough -- that
    # is satisfied by any unrelated artifact such as activity-compose.
    if ! printf '%s' "$coords" | grep -q "$a-[a-z-]*$b\|$b-[a-z-]*$a"; then
      fail "import androidx.$a.$b.* needs an artifact matching '$a-*-$b'; none in $CATALOG"
      missing_dep=1
    fi
  done < <(grep -rhoP '^import \Kandroidx\.[a-zA-Z0-9_.]+' app/src/main/java | sort -u)
  [ $missing_dep -eq 0 ] && pass "androidx import groups have matching declared artifacts"
fi

# ---------------------------------------------------------------------------
# 6. Hilt wiring is complete.
#     Catches a RUNTIME crash that compiles perfectly: @AndroidEntryPoint with
#     no @HiltAndroidApp Application throws at launch with
#       "Hilt Activity must be attached to an @HiltAndroidApp Application"
#     Nine consecutive green builds shipped this bug. CI cannot see it.
# ---------------------------------------------------------------------------
# Annotations must be matched at line start: a '// @HiltAndroidApp ...' comment
# would otherwise satisfy the check and mask the very bug being looked for.
if [ -n "${SRC_DIRS:-}" ] && grep -rqE '^\s*@(AndroidEntryPoint|HiltViewModel)\b' $SRC_DIRS 2>/dev/null; then
  hilt_ok=1
  if ! grep -rqE '^\s*@HiltAndroidApp\b' $SRC_DIRS 2>/dev/null; then
    fail "@AndroidEntryPoint/@HiltViewModel used but no @HiltAndroidApp Application: crashes at launch"
    hilt_ok=0
  else
    # The annotated class must be the one the manifest actually instantiates.
    # <application> and its android:name sit on different lines, so this must
    # slurp the file -- line-oriented grep cannot match across them.
    app_attr=$(perl -0777 -ne '
        if (/<application\b(.*?)>/s) { my $a=$1; print $1 if $a =~ /android:name="([^"]+)"/ }
      ' "$MANIFEST" 2>/dev/null)
    if [ -z "$app_attr" ]; then
      fail "@HiltAndroidApp exists but <application> has no android:name, so it is never used"
      hilt_ok=0
    else
      cls=${app_attr##*.}
      if ! grep -rlE '^\s*@HiltAndroidApp\b' $SRC_DIRS 2>/dev/null \
           | xargs -r grep -lE "class +$cls\b" >/dev/null 2>&1; then
        fail "manifest instantiates '$app_attr' but @HiltAndroidApp is on a different class"
        hilt_ok=0
      fi
    fi
  fi
  [ $hilt_ok -eq 1 ] && pass "Hilt wiring complete (@HiltAndroidApp present and registered in manifest)"
fi

# ---------------------------------------------------------------------------
# 7. Every XML resource is well-formed.
#     Catches a build-stopping AAPT failure that no other check here can see,
#     because it is a lexical fault rather than a structural one.
#
#     The instance that motivated this: an XML comment containing a double
#     hyphen. The XML spec forbids it inside comments, so AAPT fails with a bare
#     "ParseError at [row,col]" and no explanation. It is trivially easy to
#     introduce by carrying an em-dash writing habit over from shell or Kotlin,
#     where the same characters are harmless, and the file LOOKS correct.
#
#     Deliberately parsed with a real XML parser, not a regex. The first attempt
#     at this used a regex and produced a false positive on the '-->' terminator
#     itself, which is exactly the kind of nearly-right check that grants false
#     confidence.
# ---------------------------------------------------------------------------
if command -v python3 >/dev/null 2>&1 && [ -d app/src ]; then
  xml_bad=$(python3 - <<'PY' 2>/dev/null
import glob, xml.etree.ElementTree as ET
for f in sorted(glob.glob('app/src/**/*.xml', recursive=True)):
    try:
        ET.parse(f)
    except ET.ParseError as e:
        print(f"{f}: {e}")
PY
)
  if [ -n "$xml_bad" ]; then
    while IFS= read -r line; do
      [ -n "$line" ] && fail "malformed XML resource -- $line"
    done <<< "$xml_bad"
  else
    pass "all XML resources are well-formed"
  fi
fi

# ---------------------------------------------------------------------------
# 8. Workflow YAML parses.
#     An invalid workflow does not fail loudly. GitHub creates a run, attributes
#     it to whatever push introduced the file, marks it failed, and names it
#     after the FILE PATH instead of the workflow's `name:` -- because it could
#     not read the name. `gh run view --log-failed` then returns "log not found",
#     because no job ever started, so there is nothing to read.
#
#     The instance that motivated this: a heredoc written flush-left inside a
#     `run: |` block scalar. Content less indented than the block terminates it,
#     which is invalid YAML but looks entirely natural, because that is exactly
#     where a heredoc belongs in a shell script.
#
#     Cost when missed: a tag pushed against a release workflow that could not
#     run at all.
# ---------------------------------------------------------------------------
if [ -d .github/workflows ] && command -v python3 >/dev/null 2>&1; then
  yaml_out=$(python3 - <<'PY' 2>/dev/null
import glob, sys
try:
    import yaml
except ImportError:
    print("SKIP"); sys.exit(0)
for f in sorted(glob.glob('.github/workflows/*.y*ml')):
    try:
        yaml.safe_load(open(f))
    except Exception as e:
        print(f"{f}: {str(e).splitlines()[0]}")
PY
)
  if [ "$yaml_out" = "SKIP" ]; then
    : # pyyaml unavailable; say nothing rather than claim a check that did not run
  elif [ -n "$yaml_out" ]; then
    while IFS= read -r line; do
      [ -n "$line" ] && fail "invalid workflow YAML -- $line"
    done <<< "$yaml_out"
  else
    pass "all workflow YAML files parse"
  fi
fi

# ---------------------------------------------------------------------------
# 9. Every declared @Database version has a committed schema JSON.
#     Catches "bumped the version, forgot to commit the schema", which fails at
#     RUNTIME on the emulator with
#       FileNotFoundException: Cannot find the schema file in the assets folder
#     and reads like a broken test rather than a missing file.
#
#     Committing matters because a clean CI checkout has no app/schemas/, and the
#     androidTest asset merge does not wait for KSP to write it. Putting the
#     directory on the asset path is necessary but not sufficient.
#
#     Only meaningful with exportSchema = true; skipped otherwise.
# ---------------------------------------------------------------------------
if [ -n "${SRC_DIRS:-}" ] && grep -rqE '@Database\b' $SRC_DIRS 2>/dev/null; then
  db_ok=1
  # `version = N` inside the @Database annotation, which may span lines.
  db_ver=$(perl -0777 -ne 'print "$1\n" while /\@Database\s*\((.*?)\)/gs' $(grep -rlE '@Database\b' $SRC_DIRS 2>/dev/null) 2>/dev/null \
           | perl -ne 'print "$1\n" if /version\s*=\s*(\d+)/')
  for v in $db_ver; do
    if ! find app/schemas -name "$v.json" 2>/dev/null | grep -q .; then
      fail "@Database declares version $v but no committed app/schemas/**/$v.json exists"
      db_ok=0
    fi
  done
  if [ -n "$db_ver" ] && [ $db_ok -eq 1 ]; then
    pass "every @Database version has a committed schema ($(echo $db_ver | tr '\n' ' '))"
  fi
fi

echo
if [ $FAIL -eq 0 ]; then
  printf '\033[32mPreflight clean.\033[0m Push and watch: gh run watch --exit-status\n'
else
  printf '\033[31mPreflight found problems.\033[0m Fix before pushing; each CI round trip is ~3 min.\n'
fi
exit $FAIL
