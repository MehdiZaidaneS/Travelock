package com.example.travelock.data.model

import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EntitySerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun locationDecodesRelatedDestinationAndPercentageDiscount() {
        val location = json.decodeFromString<Location>(
            """
            {
              "location_id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "Helsinki city centre",
              "description": "Places to explore in central Helsinki",
              "latitude": 60.169857,
              "longitude": 24.938379,
              "image": "locations/centre.jpg",
              "destinations": [{
                "destination_id": "550e8400-e29b-41d4-a716-446655440001",
                "location_id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Example restaurant",
                "description": "A sample destination",
                "discount": 10,
                "latitude": 60.169858,
                "longitude": 24.938380,
                "image": "destinations/restaurant.jpg",
                "qr_code": "example-restaurant-code"
              }]
            }
            """.trimIndent()
        )

        val destination = location.destinations.single()
        assertEquals(location.locationId, destination.locationId)
        assertEquals(10f, destination.discount, 0f)
        assertEquals(60.169858, destination.latitude, 0.000000001)
        assertEquals("example-restaurant-code", destination.qrCode)
        assertEquals(location, json.decodeFromString<Location>(json.encodeToString(location)))
    }

    @Test
    fun userGraphRoundTripsWithCommentsUnlocksAchievementsAndNotifications() {
        val user = json.decodeFromString<User>(
            """
            {
              "user_id": "user-1", "username": "explorer", "name": "Example User",
              "email": "explorer@example.com", "profile_image": "avatars/user.jpg",
              "created_at": "2026-11-05T12:00:00.123456+02:00",
              "unlocked_destinations": [{
                "unlock_id": "unlock-1", "user_id": "user-1",
                "destination_id": "destination-1", "location": "location-1",
                "unlocked_at": "2026-11-05T10:01:00Z"
              }],
              "posts": [{
                "post_id": "post-1", "user_id": "user-1", "image": "posts/visit.jpg",
                "image_description": "A Helsinki visit", "destination_id": "destination-1",
                "created_at": "2026-11-05T10:02:00+00:00",
                "comments": [{
                  "comment_id": "comment-1", "post_id": "post-1", "user_id": "user-2",
                  "content": "Great place!", "created_at": "2026-11-05T10:03:00Z"
                }]
              }],
              "achievements": [{
                "achievement_id": "achievement-1", "user_id": "user-1", "level": 1,
                "unlocked_at": "2026-11-05T10:01:00Z",
                "location": {
                  "location_id": "location-1", "name": "Helsinki centre",
                  "description": "Central Helsinki", "latitude": 60.169857,
                  "longitude": 24.938379, "image": "locations/centre.jpg"
                }
              }],
              "notifications": [{
                "notification_id": "notification-1", "sender_id": "user-2",
                "receiver_id": "user-1", "type": "friend_request",
                "message": "A friend request", "is_read": false,
                "created_at": "2026-11-05T10:04:00Z"
              }]
            }
            """.trimIndent()
        )

        assertEquals(Instant.parse("2026-11-05T10:00:00.123456Z"), user.createdAt)
        assertEquals("Great place!", user.posts.single().comments.single().content)
        assertEquals("destination-1", user.unlockedDestinations.single().destinationId)
        assertEquals(emptyList<Destination>(), user.achievements.single().location.destinations)
        assertNull(user.notifications.single().postId)
        val encoded = json.encodeToString(user)
        assertFalse(json.parseToJsonElement(encoded).jsonObject.containsKey("password"))
        assertEquals(user, json.decodeFromString<User>(encoded))
    }

    @Test
    fun feedEventAllowsOnlyItsRelevantReference() {
        val activity = json.decodeFromString<Activity>(
            """
            {
              "activity_id": "activity-1", "user_id": "user-1", "type": "achievement",
              "achievement_id": "achievement-1", "created_at": "2026-11-05T10:00:00Z"
            }
            """.trimIndent()
        )

        assertEquals("achievement-1", activity.achievementId)
        assertNull(activity.postId)
        assertNull(activity.destinationId)
        assertEquals(activity, json.decodeFromString<Activity>(json.encodeToString(activity)))
    }
}
