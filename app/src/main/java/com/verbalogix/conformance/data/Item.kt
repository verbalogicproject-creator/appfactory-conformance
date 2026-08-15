package com.verbalogix.conformance.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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

@Database(entities = [Item::class], version = 1, exportSchema = true)
abstract class ConformanceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        const val NAME = "conformance.db"
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
