package com.verbalogix.conformance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verbalogix.conformance.data.Item
import com.verbalogix.conformance.data.ItemDao
import com.verbalogix.conformance.data.ItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @HiltViewModel with a constructor-injected DAO. This is the second half of the
 * Hilt wiring check: if the SingletonComponent does not exist, resolving this throws
 * before the screen composes.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemDao: ItemDao,
) : ViewModel() {

    private val _itemCount = MutableStateFlow(0)
    val itemCount: StateFlow<Int> = _itemCount.asStateFlow()

    private val _serializationOk = MutableStateFlow<Boolean?>(null)
    val serializationOk: StateFlow<Boolean?> = _serializationOk.asStateFlow()

    init {
        viewModelScope.launch {
            // Write on first run so the Room path is genuinely exercised -- an empty
            // database proves the schema compiled, not that it works.
            if (itemDao.count() == 0) {
                itemDao.insert(Item(label = "first run", createdAt = System.currentTimeMillis()))
            }
            _itemCount.value = itemDao.count()

            // Exercise serialization during STARTUP, deliberately un-caught.
            //
            // This is how R8 correctness gets behavioural coverage without running
            // instrumented tests against the release variant (which hangs -- see
            // build.gradle.kts). If minification strips the serializer or renames the
            // fields, this throws on the release build, the app dies at launch,
            // CrashLog records why, and the emulator's launch smoke goes red.
            //
            // A try/catch here would convert a caught runtime failure into a silent
            // one, which is the opposite of what this exists for.
            val probe = ItemDto(id = 1, label = "r8-probe")
            val restored = Json.decodeFromString<ItemDto>(Json.encodeToString(probe))
            check(restored == probe) { "serialization round-trip mismatch: $restored != $probe" }
            _serializationOk.value = true
        }
    }
}
