package com.example.travelock.data.mock

import com.example.travelock.data.model.Comment
import com.example.travelock.data.model.Post
import java.time.Instant

/**
 * Sample social feed posts, likes, and comments for PBI-16 and PBI-19.
 */
object MockPosts {

    val initialPosts: List<Post> = listOf(
        Post(
            postId = "post-001",
            userId = MockUsers.amir.userId,
            image = "suomenlinna_fortress",
            imageDescription = "Completed the Suomenlinna Sea Fortress route today! The autumn breeze across the islands was unforgettable.",
            destinationId = MockLocations.suomenlinnaFortress.destinationId,
            createdAt = Instant.parse("2026-11-08T14:20:00Z"),
            likes = listOf(MockUsers.currentUser, MockUsers.jesper, MockUsers.osama),
            comments = listOf(
                Comment(
                    commentId = "comment-001",
                    postId = "post-001",
                    userId = MockUsers.currentUser.userId,
                    content = "Awesome shot Amir! Did you also check out King's Gate?",
                    createdAt = Instant.parse("2026-11-08T14:35:00Z")
                ),
                Comment(
                    commentId = "comment-002",
                    postId = "post-001",
                    userId = MockUsers.amir.userId,
                    content = "Yes! Unlocked all 3 destinations on the island!",
                    createdAt = Instant.parse("2026-11-08T14:40:00Z")
                )
            )
        ),
        Post(
            postId = "post-002",
            userId = MockUsers.jesper.userId,
            image = "metropolia_campus",
            imageDescription = "Scanned the QR code at Myllypuro Metropolia Campus and grabbed a 20% student coffee discount!",
            destinationId = MockLocations.metropoliaMyllypuro.destinationId,
            createdAt = Instant.parse("2026-11-07T16:10:00Z"),
            likes = listOf(MockUsers.amir),
            comments = listOf(
                Comment(
                    commentId = "comment-003",
                    postId = "post-002",
                    userId = MockUsers.osama.userId,
                    content = "See you at campus tomorrow!",
                    createdAt = Instant.parse("2026-11-07T17:00:00Z")
                )
            )
        ),
        Post(
            postId = "post-003",
            userId = MockUsers.osama.userId,
            image = "kallio_church",
            imageDescription = "Evening walk up to Kallio Church. Next stop: Kallio Library and Bear Park!",
            destinationId = MockLocations.kallioChurch.destinationId,
            createdAt = Instant.parse("2026-11-07T11:05:00Z"),
            likes = listOf(MockUsers.currentUser, MockUsers.amir),
            comments = emptyList()
        ),
        Post(
            postId = "post-004",
            userId = MockUsers.currentUser.userId,
            image = "oodi_library",
            imageDescription = "Unlocked Oodi Central Library in Kluuvi! The 3rd floor Citizens' Balcony view over Helsinki is incredible.",
            destinationId = MockLocations.oodiLibrary.destinationId,
            createdAt = Instant.parse("2026-11-06T14:35:00Z"),
            likes = listOf(MockUsers.amir, MockUsers.jesper),
            comments = listOf(
                Comment(
                    commentId = "comment-004",
                    postId = "post-004",
                    userId = MockUsers.jesper.userId,
                    content = "One of the best study spots in Helsinki!",
                    createdAt = Instant.parse("2026-11-06T15:10:00Z")
                )
            )
        )
    )
}

