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
    fun friendTravelProfileDecodesEmptyHistoryAndMissingProfile() {
        val payload = """{"profile":{"user_id":"friend","username":"friend","name":"Friend","profile_image":""},"achievements":[],"unlocks":[]}"""
        val profile = json.decodeFromString<FriendTravelProfile>(payload)
        assertEquals("friend", profile.profile.userId)
        assertEquals(emptyList<UserAchievement>(), profile.achievements)
        assertEquals(emptyList<UserUnlock>(), profile.unlocks)
        assertEquals(profile, json.decodeFromString<FriendTravelProfile>(json.encodeToString(profile)))
        assertNull(json.decodeFromString<FriendTravelProfile?>("null"))
    }

    @Test
    fun locationProgressDecodesLockedAndEarnedStates() {
        val base = """{"location_id":"l","location_name":"Helsinki","total_destinations":2,"verified_destinations":1,"is_complete":false,"is_earned":false,"achievement_name":"Explore Helsinki","achievement_description":"Verify every destination in Helsinki."}"""
        val locked = json.decodeFromString<LocationProgress>(base)
        assertEquals(2L, locked.totalDestinations)
        assertEquals(1L, locked.verifiedDestinations)
        assertNull(locked.achievementId)
        assertNull(locked.unlockedAt)
        val earned = locked.copy(isEarned = true, achievementId = "a", unlockedAt = Instant.parse("2026-11-10T00:00:00Z"))
        assertEquals(earned, json.decodeFromString<LocationProgress>(json.encodeToString(earned)))
        assertFalse(earned.isComplete)
    }

    @Test
    fun notificationDecodesCommentAndRequestTargetsWithLegacyDefaults() {
        val base = """{"notification_id":"n","sender_id":"a","receiver_id":"b","type":"like","message":"Liked your post.","is_read":false,"created_at":"2026-11-10T00:00:00Z","post_id":"p"}"""
        val like = json.decodeFromString<Notification>(base)
        assertNull(like.commentId)
        assertNull(like.friendshipId)
        val comment = like.copy(type = "comment", commentId = "c")
        val request = like.copy(type = "friend_request", postId = null, friendshipId = "f")
        assertEquals(comment, json.decodeFromString<Notification>(json.encodeToString(comment)))
        assertEquals(request, json.decodeFromString<Notification>(json.encodeToString(request)))
        assertEquals("c", comment.commentId)
        assertEquals("f", request.friendshipId)
    }

    @Test
    fun interactionSummaryDecodesBigintCountsAndCurrentActorState() {
        val summary = json.decodeFromString<PostInteractionSummary>(
            """{"like_count":123456789012,"comment_count":42,"is_liked_by_me":true}""",
        )
        assertEquals(123456789012L, summary.likeCount)
        assertEquals(42L, summary.commentCount)
        assertEquals(true, summary.isLikedByMe)
        assertEquals(summary, json.decodeFromString<PostInteractionSummary>(json.encodeToString(summary)))
    }

    @Test
    fun postDecodesVisibilityAndVisitWhileSupportingLegacyRows() {
        val legacy = """{"post_id":"p","user_id":"u","destination_id":"d","image":"u/p/photo.jpg","image_description":"Visit","created_at":"2026-11-10T00:00:00Z"}"""
        val oldPost = json.decodeFromString<Post>(legacy)
        assertEquals(PostVisibility.FRIENDS, oldPost.visibility)
        assertNull(oldPost.visitId)
        val shared = json.decodeFromString<Post>(legacy.dropLast(1) + """, "visibility":"public", "visit_id":"visit-1"}""")
        assertEquals(PostVisibility.PUBLIC, shared.visibility)
        assertEquals("visit-1", shared.visitId)
        assertEquals(shared, json.decodeFromString<Post>(json.encodeToString(shared)))
        assertEquals("\"friends\"", json.encodeToString(PostVisibility.FRIENDS))
        assertEquals("\"public\"", json.encodeToString(PostVisibility.PUBLIC))
    }

    @Test
    fun profileDiscoveryDecodesTableResultsWithoutFullProfileFields() {
        val results = json.decodeFromString<List<ProfileSummary>>(
            """[{"user_id":"user-b","username":"bob","name":"Bob","profile_image":""}]""",
        )
        assertEquals(listOf(ProfileSummary("user-b", "bob", "Bob", "")), results)
        assertEquals(results, json.decodeFromString<List<ProfileSummary>>(json.encodeToString(results)))
        assertEquals(emptyList<ProfileSummary>(), json.decodeFromString<List<ProfileSummary>>("[]"))
    }

    @Test
    fun friendshipDecodesPendingAndAnsweredRequests() {
        val pending = json.decodeFromString<Friendship>(
            """
            {
              "friendship_id": "request-1", "requester_id": "user-a", "recipient_id": "user-b",
              "status": "pending", "created_at": "2026-11-10T00:45:00.123456+02:00",
              "responded_at": null
            }
            """.trimIndent(),
        )
        assertEquals("request-1", pending.friendshipId)
        assertEquals("user-a", pending.requesterId)
        assertEquals("user-b", pending.recipientId)
        assertEquals("pending", pending.status)
        assertEquals(Instant.parse("2026-11-09T22:45:00.123456Z"), pending.createdAt)
        assertNull(pending.respondedAt)
        assertEquals(pending, json.decodeFromString<Friendship>(json.encodeToString(pending)))
        for (status in listOf("accepted", "rejected")) {
            val answered = pending.copy(status = status, respondedAt = Instant.parse("2026-11-09T22:46:00Z"))
            assertEquals(answered, json.decodeFromString<Friendship>(json.encodeToString(answered)))
        }
    }

    @Test
    fun postDecodesDatabaseRowWithoutSocialRelations() {
        val post = json.decodeFromString<Post>(
            """
            {
              "post_id": "post-1", "user_id": "user-1", "destination_id": "destination-1",
              "image": "user-1/post-1/photo.jpg", "image_description": "A library visit",
              "created_at": "2026-11-10T00:45:00.123456+02:00"
            }
            """.trimIndent(),
        )
        assertEquals("post-1", post.postId)
        assertEquals("user-1/post-1/photo.jpg", post.image)
        assertEquals("A library visit", post.imageDescription)
        assertEquals(Instant.parse("2026-11-09T22:45:00.123456Z"), post.createdAt)
        assertEquals(emptyList<User>(), post.likes)
        assertEquals(emptyList<Comment>(), post.comments)
        assertEquals(post, json.decodeFromString<Post>(json.encodeToString(post)))
    }

    @Test
    fun unlockDecodesJoinedDestinationAndPreservesPlainResponses() {
        val unlock = json.decodeFromString<UserUnlock>(
            """
            {
              "unlock_id": "unlock-1", "user_id": "user-1", "destination_id": "destination-1",
              "unlocked_at": "2026-11-10T00:15:00.123456+02:00",
              "destination": {
                "destination_id": "destination-1", "location_id": "location-1",
                "name": "Oodi Library", "description": "Library visit", "discount": 0,
                "latitude": 60.1738, "longitude": 24.9381, "image": "oodi.jpg",
                "qr_code": "TEST-OODI-001"
              }
            }
            """.trimIndent(),
        )
        val destination = checkNotNull(unlock.destination)
        assertEquals(unlock.destinationId, destination.destinationId)
        assertEquals("Oodi Library", destination.name)
        assertEquals("Library visit", destination.description)
        assertEquals("oodi.jpg", destination.image)
        assertEquals(Instant.parse("2026-11-09T22:15:00.123456Z"), unlock.unlockedAt)
        assertEquals(unlock, json.decodeFromString<UserUnlock>(json.encodeToString(unlock)))
        val plain = unlock.copy(destination = null)
        assertEquals(plain, json.decodeFromString<UserUnlock>(json.encodeToString(plain)))
    }

    @Test
    fun enrolmentDecodesDatabaseColumnsAndTimestamp() {
        val enrolment = json.decodeFromString<UserEnrolment>(
            """
            {
              "enrolment_id": "enrolment-1", "user_id": "user-1",
              "destination_id": "destination-1", "enrolled_at": "2026-11-10T00:15:00.123456+02:00"
            }
            """.trimIndent(),
        )
        assertEquals("enrolment-1", enrolment.enrolmentId)
        assertEquals("user-1", enrolment.userId)
        assertEquals("destination-1", enrolment.destinationId)
        assertEquals(Instant.parse("2026-11-09T22:15:00.123456Z"), enrolment.enrolledAt)
        assertNull(enrolment.destination)
        assertEquals(enrolment, json.decodeFromString<UserEnrolment>(json.encodeToString(enrolment)))
    }

    @Test
    fun enrolmentDecodesJoinedDestinationObject() {
        val enrolment = json.decodeFromString<UserEnrolment>(
            """
            {
              "enrolment_id": "enrolment-1", "user_id": "user-1",
              "destination_id": "destination-1", "enrolled_at": "2026-11-10T00:15:00Z",
              "destination": {
                "destination_id": "destination-1", "location_id": "location-1",
                "name": "Oodi Library", "description": "Library visit", "discount": 0,
                "latitude": 60.1738, "longitude": 24.9381, "image": "oodi.jpg",
                "qr_code": "TEST-OODI-001"
              }
            }
            """.trimIndent(),
        )
        val destination = checkNotNull(enrolment.destination)
        assertEquals(enrolment.destinationId, destination.destinationId)
        assertEquals("Oodi Library", destination.name)
        assertEquals("Library visit", destination.description)
        assertEquals("oodi.jpg", destination.image)
        assertEquals(enrolment, json.decodeFromString<UserEnrolment>(json.encodeToString(enrolment)))
    }

    @Test
    fun visitAttemptDecodesPendingAndCompletedDatabaseResponses() {
        val pending = json.decodeFromString<VisitAttempt>(
            """
            {
              "visit_id": "visit-1", "user_id": "user-1", "destination_id": "destination-1",
              "started_at": "2026-11-09T15:00:00.123456+00:00",
              "completed_at": null, "photo_path": null
            }
            """.trimIndent(),
        )
        assertEquals(Instant.parse("2026-11-09T15:00:00.123456Z"), pending.startedAt)
        assertNull(pending.completedAt)
        assertNull(pending.photoPath)
        assertNull(pending.cancelledAt)
        val cancelled = pending.copy(cancelledAt = Instant.parse("2026-11-10T12:00:00Z"))
        assertEquals(cancelled, json.decodeFromString<VisitAttempt>(json.encodeToString(cancelled)))

        val completed = json.decodeFromString<VisitAttempt>(
            """
            {
              "visit_id": "visit-1", "user_id": "user-1", "destination_id": "destination-1",
              "started_at": "2026-11-09T15:00:00.123456+00:00",
              "completed_at": "2026-11-09T15:01:00+00:00",
              "photo_path": "user-1/visit-1/photo.jpg"
            }
            """.trimIndent(),
        )
        assertEquals(Instant.parse("2026-11-09T15:01:00Z"), completed.completedAt)
        assertEquals("user-1/visit-1/photo.jpg", completed.photoPath)
        assertEquals(completed, json.decodeFromString<VisitAttempt>(json.encodeToString(completed)))
    }

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
                "destination_id": "destination-1",
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
