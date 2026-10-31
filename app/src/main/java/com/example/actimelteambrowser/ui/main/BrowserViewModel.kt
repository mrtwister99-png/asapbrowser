package com.example.actimelteambrowser.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class BrowserViewModel : ViewModel() {
    val currentUrl = MutableStateFlow("https://www.youtube.com")
    val isLoading = MutableStateFlow(false)

    fun search(query: String) {
        // TODO: logika vyhledávání
    }
}
