package com.example.travelock.data.model

import kotlinx.serialization.Serializable

// Starter connection-check model; not one of the Travelock domain entities.
@Serializable
data class TodoItem(val id: Int, val name: String)
