# Conformance claims

This app exists to keep the `appfactory` pipeline honest. It does nothing useful on
purpose — every feature is here because it exercises exactly one failure class that
has actually shipped in this lineage.

**A claim without an evidence artifact is unchecked, and is listed as unchecked.**
That distinction is the whole difference between this pipeline and the one that
displayed eleven green builds of an app which crashed before rendering a pixel.

## Status

| # | Claim | Rung | Evidence | Status |
|---|---|---|---|---|
| 1 | Static preflight catches structural faults with no SDK | V1 | — | ☐ unchecked |
| 2 | The project compiles in CI | V3 | — | ☐ unchecked |
| 3 | Unit tests run and are **not vacuous** | V4 | — | ☐ unchecked |
| 4 | R8 / release compile succeeds | V5 | — | ☐ unchecked |
| 5 | Instrumented tests pass on API 28 and 34 | V6a | — | ☐ unchecked |
| 6 | **The release build installs and launches** | V6a | — | ☐ unchecked |
| 7 | `v0.0.1` publishes a signed APK + AAB + mapping.txt | V7 | — | ☐ unchecked |
| 8 | The published APK's cert digest matches the pin | V7 | — | ☐ unchecked |
| 9 | The APK installs on the physical device and runs | V7 | — | ☐ unchecked |
| 10 | The on-screen git SHA matches the tagged commit | V7 | — | ☐ unchecked |
| 11 | `v0.0.2` installs **over** `v0.0.1` | V6b | — | ☐ unchecked |
| 12 | A Room 1→2 migration runs without data loss | V4 | — | ☐ unchecked |
| 13 | A real crash is retrieved and retraced via mapping.txt | V8 | — | ☐ unchecked |
| 14 | Preflight catches all three bugs on `conformance/red` | V1 | — | ☐ unchecked |
| 15 | The emulator rung is observably RED on `conformance/red` | V6a | — | ☐ unchecked |

Claims 14 and 15 matter as much as the rest. **A ladder never observed failing is
not a ladder** — it is a decoration that reports success. Both are verified by
deliberately breaking things on a branch kept for the purpose.

## The failure classes, and what covers each

| Failure that actually shipped | Why it was invisible | Covered by |
|---|---|---|
| `@HiltAndroidApp` missing | compiles clean; throws at `onCreate` | preflight 060 + claim 6 |
| `font_certs.xml` fabricated | valid to AAPT, semantic garbage; threw on first glyph | system fonts only + claim 6 |
| R8 stripped the serializer | JVM unit tests don't run R8 | claim 5, instrumented against the **release** build |
| release signed with the runner's debug key | signed, just with a *different key each run* | claim 8, cert digest pin |
| `proguard-rules.pro` referenced, absent | multi-line `proguardFiles` evaded a line-oriented grep | preflight 020 |
| `shrinkResources` vs `isShrinkResources` | Kotlin DSL cannot be checked without compiling | claim 2 |
| artifact upload matched nothing | `if-no-files-found` defaults to `warn` | `error` + claim 7 |
| test gate passed on zero tests | `testDebugUnitTest` is NO-SOURCE, reports success | claim 3 |

## Irreversible decisions

Three things about an Android app can never change after the first user installs it.
They are recorded here because there is no recovery from getting them wrong.

| Decision | Value | Consequence of changing it |
|---|---|---|
| `applicationId` | `com.verbalogix.conformance` | a different app; the installed cohort is orphaned |
| signing certificate | pinned in `.appfactory/release/cert.sha256` | no installed device will accept the update, ever |
| persisted schema version | Room v1, `exportSchema = true` | v2 must migrate from it or destroy user data |

## When this document becomes stale

Update it when: any claim gains or loses evidence; a rung is added or removed; a new
failure class is discovered in the field (add a row and the check that catches it);
or the version matrix in `gradle/libs.versions.toml` moves.

If the evidence column is stale, **this document is lying**, and a lying conformance
report is worse than none — it is the same false confidence as an untested check.
