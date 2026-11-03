package com.smartspend.app.ui.components.island

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DynamicIslandState {
    object Hidden : DynamicIslandState()
    data class Alert(
        val title: String,
        val amount: Long,
        val type: String,
        val accountName: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : DynamicIslandState()
    object Expanded : DynamicIslandState()
}

object DynamicIslandManager {

    private val _state = MutableStateFlow<DynamicIslandState>(DynamicIslandState.Hidden)
    val state: StateFlow<DynamicIslandState> = _state.asStateFlow()

    fun showTransactionEvent(
        context: Context? = null,
        title: String,
        amount: Long,
        type: String,
        accountName: String? = null
    ) {
        // Trigger system-level overlay outside the app
        context?.let {
            SystemIslandOverlayManager.showAlert(it, title, amount, type, accountName)
        }
    }

    fun expand() {
        _state.value = DynamicIslandState.Expanded
    }

    fun collapse() {
        _state.value = DynamicIslandState.Hidden
    }
}
