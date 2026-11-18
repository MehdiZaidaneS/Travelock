package com.example.travelock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.ui.auth.AuthFlowController
import com.example.travelock.ui.auth.SupabaseAuthFlowGateway

class AuthViewModel : ViewModel() {
    val flow = AuthFlowController(viewModelScope, SupabaseAuthFlowGateway())
    init { flow.restore() }
}
