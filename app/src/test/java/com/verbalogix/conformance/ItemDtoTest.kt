package com.verbalogix.conformance

import com.verbalogix.conformance.data.ItemDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These pass on the JVM whether or not proguard-rules.pro contains the
 * kotlinx.serialization keeps, because R8 does not run for unit tests.
 *
 * That is precisely the point, and precisely the trap: deleting those keep rules
 * leaves this file green while the shipped release build fails to parse JSON at
 * runtime. The instrumented counterpart in androidTest is what actually guards the
 * keep rules -- this only guards the wire format.
 */
class ItemDtoTest {

    @Test
    fun `round trips through json`() {
        val original = ItemDto(id = 7, label = "hello")
        val decoded = Json.decodeFromString<ItemDto>(Json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `json field names are exactly id and label`() {
        // Pinned deliberately. These names are the wire contract; R8 renaming them
        // is the failure the keep rules exist to prevent, and this documents what
        // "correct" looks like.
        assertEquals("""{"id":7,"label":"hello"}""", Json.encodeToString(ItemDto(7, "hello")))
    }
}
