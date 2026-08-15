package com.verbalogix.conformance.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable

/**
 * The persisted schema version is one of exactly three things about an Android app
 * that can never be changed after the first install -- alongside the applicationId
 * and the signing certificate. Ship version 1, and version 2 must migrate from it or
 * destroy user data.
 *
 * This starts at version 1 deliberately. The v0.0.2 release adds a real 1 -> 2
 * migration, so the schema Room exports for version 1 is genuinely generated rather
 * than hand-written to match.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    // Added in schema version 2. NOT NULL with a DEFAULT, because the migration has
    // to give existing rows a value -- adding a NOT NULL column without one fails on
    // any device that already has data, which is every device that mattered.
    val createdAt: Long = 0L,
)

@Dao
interface ItemDao {
    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("SELECT * FROM items ORDER BY id")
    suspend fun all(): List<Item>

    @Insert
    suspend fun insert(item: Item)
}

@Database(entities = [Item::class], version = 2, exportSchema = true)
abstract class ConformanceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        const val NAME = "conformance.db"

        /**
         * Schema 1 -> 2: adds `createdAt`.
         *
         * The DEFAULT 0 is load-bearing. SQLite cannot add a NOT NULL column without
         * one, so omitting it fails at runtime on precisely the devices that already
         * hold data -- and passes on a fresh install, which is what a developer
         * tests. Room validates the resulting schema against the exported 2.json and
         * throws IllegalStateException on any mismatch, so a migration that runs but
         * produces the wrong shape still fails loudly rather than silently.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

/**
 * A @Serializable DTO exists here to exercise R8.
 *
 * Without the keep rules in proguard-rules.pro, R8 renames these field names and
 * strips the generated serializer. The build stays green. JVM unit tests still pass,
 * because R8 does not run for them. It breaks only at runtime, only in the release
 * variant -- which is why the conformance suite round-trips this in an INSTRUMENTED
 * test against a release build, not a unit test.
 */
@Serializable
data class ItemDto(
    val id: Long,
    val label: String,
)
