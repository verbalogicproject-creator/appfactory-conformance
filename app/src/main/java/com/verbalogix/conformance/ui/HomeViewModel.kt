package com.verbalogix.conformance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verbalogix.conformance.data.Item
import com.verbalogix.conformance.data.ItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    init {
        viewModelScope.launch {
            // Write on first run so the Room path is genuinely exercised -- an empty
            // database proves the schema compiled, not that it works.
            if (itemDao.count() == 0) {
                itemDao.insert(Item(label = "first run"))
            }
            _itemCount.value = itemDao.count()
        }
    }
}
