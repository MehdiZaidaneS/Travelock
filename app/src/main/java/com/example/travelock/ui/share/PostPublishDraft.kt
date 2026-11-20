package com.example.travelock.ui.share

import com.example.travelock.data.model.PostVisibility
import kotlinx.serialization.Serializable

// Written only after an explicit Post tap. Keep the exact request across retries;
// image bytes and credentials never enter saved state or preferences.
@Serializable
data class PostPublishDraft(
    val userId: String,
    val visitId: String,
    val postId: String,
    val caption: String,
    val visibility: PostVisibility,
) {
    val imagePath: String get() = "$userId/$postId/visit.jpg"
}
