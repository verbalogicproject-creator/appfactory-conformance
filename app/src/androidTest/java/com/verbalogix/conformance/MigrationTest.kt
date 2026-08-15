package com.verbalogix.conformance

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verbalogix.conformance.data.ConformanceDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The persisted schema version is one of exactly three things about an Android app
 * that cannot be changed after the first install. A broken migration does not crash
 * the build; it destroys data on devices that already have some, while a fresh
 * install — what a developer usually tests — works perfectly.
 *
 * This test creates a real version-1 database from the schema that actually shipped
 * in v0.0.1, puts a row in it, runs the migration, and checks the row survived with
 * its original values intact.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ConformanceDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingRows() {
        // Version 1, built from the exported 1.json -- the schema that shipped.
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO items (label) VALUES ('pre-existing')")
        }

        // Room validates the post-migration schema against the exported 2.json and
        // throws on any mismatch, so a migration that runs but produces the wrong
        // shape still fails here rather than in the field.
        val db = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            ConformanceDatabase.MIGRATION_1_2,
        )

        db.query("SELECT id, label, createdAt FROM items").use { c ->
            assertEquals("the pre-existing row was lost", 1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("pre-existing", c.getString(1))
            // The DEFAULT 0 in the ALTER TABLE. Without it SQLite refuses to add a
            // NOT NULL column to a table that already has rows.
            assertEquals(0L, c.getLong(2))
        }
        db.close()
    }
}
