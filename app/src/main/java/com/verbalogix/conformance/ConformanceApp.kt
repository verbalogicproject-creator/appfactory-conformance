package com.verbalogix.conformance

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

/**
 * @HiltAndroidApp is what generates the SingletonComponent. Without it, any
 * @AndroidEntryPoint Activity throws at onCreate:
 *
 *     IllegalStateException: Hilt Activity must be attached to an @HiltAndroidApp Application
 *
 * That failure compiles perfectly and is invisible to every static check that does
 * not look for it specifically. Nine consecutive green builds shipped it once.
 *
 * The AndroidManifest must also point android:name at this class, or the annotation
 * is present on a class the system never instantiates -- same crash, and the
 * annotation being right makes it harder to spot.
 */
@HiltAndroidApp
class ConformanceApp : Application() {

    // Deliberately attachBaseContext, not onCreate. Hilt's component is built inside
    // super.onCreate(); a handler installed after that point cannot catch a failure
    // during graph construction, which is the most likely startup crash in this app.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(base)
    }

    // NEGATIVE TEST for the launch smoke rung. Compiles clean, passes preflight,
    // passes every unit test -- and kills the app at launch, which is exactly the
    // shape of the three worst failures in this lineage.
    //
    // Deliberately in onCreate, AFTER CrashLog is installed in attachBaseContext,
    // so this also proves the crash handler captures it and crash.txt is retrievable.
    override fun onCreate() {
        super.onCreate()
        error("deliberate launch crash -- conformance negative test for the launch smoke rung")
    }
}
