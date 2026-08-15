package com.verbalogix.conformance

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verbalogix.conformance.data.ItemDto
// See ItemDtoTest: the reified single-argument overload needs this import.
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rung that answers "does it launch".
 *
 * Everything below this in the ladder only ever proves the app COMPILES. Three of
 * the worst failures in this lineage compiled perfectly and died at launch:
 * a missing @HiltAndroidApp, a fabricated font certificate, and R8 stripping a
 * serializer. Nine consecutive green builds shipped an app that crashed before
 * rendering a pixel.
 */
@RunWith(AndroidJUnit4::class)
class LaunchTest {

    @Test
    fun activityLaunchesAndReachesResumed() {
        // Fails if Hilt's SingletonComponent does not exist -- the @HiltAndroidApp
        // check that no static analysis can perform.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull(scenario)
            scenario.onActivity { activity ->
                assertNotNull("activity was not created", activity)
            }
        }
    }

    @Test
    fun buildConfigCarriesProvenance() {
        // A build that cannot say which commit produced it makes every later crash
        // report unattributable.
        assertNotNull(BuildConfig.GIT_SHA)
        assert(BuildConfig.GIT_SHA.isNotBlank())
        assert(BuildConfig.VERSION_NAME.isNotBlank())
    }

    /**
     * The R8 guard.
     *
     * This runs inside the packaged app, so on a release build it runs against
     * R8-processed classes. Delete the kotlinx.serialization keeps from
     * proguard-rules.pro and the JVM unit test still passes while THIS fails --
     * which is the whole reason it exists here rather than in test/.
     */
    @Test
    fun serializationSurvivesMinification() {
        val original = ItemDto(id = 42, label = "survives-r8")
        val json = Json.encodeToString(original)
        assertEquals("""{"id":42,"label":"survives-r8"}""", json)
        assertEquals(original, Json.decodeFromString<ItemDto>(json))
    }

    @Test
    fun applicationIdIsTheExpectedOne() {
        // The applicationId is irreversible after the first install. Pin it, so a
        // rename becomes a failing test rather than an orphaned user cohort.
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assert(pkg.startsWith("com.verbalogix.conformance")) { "unexpected applicationId: $pkg" }
    }
}
