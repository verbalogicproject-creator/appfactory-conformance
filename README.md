# appfactory-conformance

The conformance app for the **appfactory** pipeline — a Claude Code marketplace that
turns an app idea into a signed, installed, verified-running Android APK.

This app does nothing useful, on purpose. It exists so the pipeline cannot lie.

## Why it exists

A pipeline cannot verify itself. "A tag produces a signed APK on a GitHub Release" is
a claim about GitHub Actions and a physical phone, and only a real repository can
make it. So this is that repository, and every feature in it is here because it
exercises exactly one failure class that has actually shipped:

| Feature | The failure it catches |
|---|---|
| Hilt: `@HiltAndroidApp` + `@AndroidEntryPoint` + `@HiltViewModel` | compiles clean, throws at launch |
| Room v1 with `exportSchema = true` | the persisted schema version is irreversible |
| `@Serializable` DTO with R8 keep rules | green build, green unit tests, runtime failure in release only |
| System fonts only, never a downloadable-font provider | a fabricated font certificate killed an app at launch through eleven green builds |
| `POST_NOTIFICATIONS` with an explicit denial path | an unhandled denial is a crash awaiting the first user who taps Deny |
| version, versionCode and git SHA on screen | so a screenshot is evidence, and a stale APK cannot pass as fresh |

See [CONFORMANCE.md](CONFORMANCE.md) for the claim ledger. **A claim without an
evidence artifact is listed as unchecked.**

## The constraint that shapes everything

Android build-tools are x86_64-only, and the development machine is an aarch64 phone
under PRoot. Local `java` is JDK 25 and local `gradle` is 4.4.1, so the build is
JDK- and Gradle-blocked as well as SDK-blocked.

**GitHub Actions is the compiler.** Every build is a push, costing 2–5 minutes. The
whole design optimises for *time-to-failure*, not time-to-success.

```
edit  ->  bash scripts/preflight.sh  ->  commit  ->  push  ->  gh run watch
             (~2s, catches 7 classes)                  (2-5 min, the real compiler)
```

Never skip preflight. It is free; CI is not.

## The verification ladder

Each rung catches something the rung below it cannot.

| Rung | Cost | Catches uniquely |
|---|---|---|
| `scripts/preflight.sh` | ~2 s | structural faults, with no SDK |
| `ci.yml` compile | 2–4 min | types, Compose compiler, KSP/Hilt graph |
| unit tests | +30 s | logic and wire formats |
| R8 / bundle | +90 s | minification, missing keep rules |
| **`emulator.yml`** | 6–12 min | **whether it actually launches** |
| `release.yml` | 3–5 min | signed with *our* key, not merely signed |
| physical device | manual | real hardware, and the truth |

Everything above the emulator rung only ever proves the app **compiles**. Three of
the worst failures in this lineage compiled perfectly and died at launch. Nine
consecutive green builds once shipped an app that crashed before rendering a pixel.

## Reading a failure

```bash
# Pin to the SHA. `gh run list --limit 1` races the push and will happily return the
# PREVIOUS commit's run, which then goes green and tells you nothing. That happened
# here once: a fix was reported as verified by a run that never compiled it.
gh run list --commit $(git rev-parse HEAD)
gh run view <id> --log-failed
```

**`--commit` requires the full 40-character SHA.** A short SHA returns an empty list
with no error and exit 0 — which reads as "the run hasn't appeared yet" and invites
falling back to `--limit 1`, the precise race this rule exists to prevent. Always
`$(git rev-parse HEAD)`, never `$(git rev-parse --short HEAD)`.

Fix the error the log names, lowest line number first. Kotlin reports one broken
import as many errors; only the first is real, and "fixing" a cascade error makes
correct code wrong.

## Irreversible decisions

| Decision | Value |
|---|---|
| `applicationId` | `com.verbalogix.conformance` |
| signing certificate | pinned in `.appfactory/release/cert.sha256` |
| persisted schema | Room v1 |

Changing any of these orphans every existing install. There is no recovery, which is
why `release.yml` verifies the certificate digest before publishing rather than
after.

---

Author: Eyal Nof · License: Apache-2.0
