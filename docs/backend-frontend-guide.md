# Backend to frontend guide

Use the repositories in app/src/main/java/com/example/travelock/data/repository/. Several screens still use mock adapters and need separate integration. Call suspend functions from a coroutine/ViewModel and handle loading, errors and success.

The old todos starter instructions are outdated. Auth, models and Storage are already implemented; do not recreate the demo table for normal setup.

## Authentication

AuthRepository:

- registerWithEmail(email, password) = requests registration. Returns ConfirmationRequired; confirm the email before login. The neutral response can also be returned for an existing email.
- loginWithEmail(email, password) = signs in and returns AuthUser.
- signInWithGoogle(idToken, nonce) = exchanges a Google ID token for a Supabase session. Create a fresh GoogleSignInNonce, pass its hashedNonceForGoogle to Google, and pass that same object here.
- getCurrentUser() = returns the locally restored user after SDK initialization, or null. This does not validate the session with the server.
- refreshSession() = refreshes the session; returns null if absent or confirmed invalid. Network/temporary refresh failures remain retryable errors.
- logout() = signs out this device and clears its stored session.
- sendPasswordResetEmail(email) = requests a recovery email.
- resetPasswordFromLink(email, recoveryLink, newPassword) = verifies the recovery link, changes the password and clears the recovery session.

Passwords stay in Supabase Auth. Production Google-button wiring, automatic email-link routing and the new-password form remain UI work. Live session refresh/restart checks are still unverified.

## Profiles

An Auth user is the login account. A Profile holds the username, name and profile photo.

ProfileOnboardingRepository:

- getState() = returns SignedOut, NeedsProfile(user), or Ready(user, profile).
- completeProfile(username, name, profileImage = "") = creates a missing profile; cannot overwrite an existing one.

ProfileRepository:

- getMyProfile() = returns the current user's profile, or null if missing.
- createMyProfile(username, name, profileImage = "") = creates it. Username/name are required; username is unique.
- updateMyProfile(username, name, profileImage) = saves all editable fields. Pass the current image path to keep the photo.
- updateMyProfilePhoto(photoPath) = changes only the photo; an empty string clears it.
- searchProfiles(query) = searches username/name, excluding self. Up to 20 results; blank input returns none.
- getProfileSummary(userId) = returns basic profile information for a signed-in viewer.
- getFriendTravelProfile(userId) = returns an accepted friend's profile, earned achievements and unlocks. Self, unrelated users and pending requests are denied.

ProfilePhotoRepository.uploadProfilePhoto(jpegBytes) = uploads a JPEG up to 10 MiB and returns a private path. Save it through the profile update.

New paths must belong to the current user. Signed-in users can read an attached profile photo; guests cannot. Old uploads remain owner-readable; cleanup is not implemented. Unchanged legacy image values can be preserved.

Discovery exposes basic profiles, not email/private visit evidence. Custom profile visibility settings are not implemented.

## Catalogue

These reads work before login; client writes are blocked.

LocationRepository:

- getLocations() = lists locations.
- searchLocations(query) = searches names without case sensitivity; blank input lists all.
- getLocation(locationId) = returns one location, or null.

DestinationRepository:

- getDestinationsByLocation(locationId) = lists its destinations.
- getDestination(destinationId) = returns one destination, or null.

Fetch destinations separately; Location.destinations is not automatically populated. Destination includes coordinates, image, locationId, discount and qrCode alongside its name/description.

Add catalogue rows in Supabase for now. For destination pictures, store a public image URL in destinations.image. A separate public destination-images bucket was suggested but its setup is not verified. Keep profile, post and visit buckets private.

Discount is a percentage field; redemption is not implemented. Public catalogue QR values are not secret proof of a visit.

## Nearby places and maps

NearbyDestinationRepository.getNearbyDestinations(latitude, longitude, limit = 20) = returns destination/distanceMeters pairs ordered by straight-line distance. Limit 1–100; no radius filter.

NearbyDestinationsCoordinator(context).getNearbyDestinations(limit = 20) = reads a fresh device fix and returns Ready(location, destinations), LocationPermissionRequired, LocationServicesDisabled or LocationUnavailable.

Request permission from the screen. Approximate location is usable for browsing. Call again to refresh; there is no update timer.

DestinationMapRepository.getMapData(locationId = null) = returns real destinations and the user's own unlock status, optionally filtered by location.

Statuses are SIGN_IN_REQUIRED for guests, LOCKED, or UNLOCKED. Invalid catalogue coordinates are skipped. Marker reads need no GPS permission; use DeviceLocationProvider separately for the user's position.

The existing map screen still uses mock data. Real map rendering, marker styles and GPS display need UI integration.

## Routes

RoutePlanningRepository:

- planRoute(destinationIds, start = null) = loads real destinations in the selected order and returns RoutePlan.
- addStop(route, destinationId) = loads and appends a stop; an existing stop leaves the plan unchanged.

RoutePlanner:

- createRoute(destinations, start = null) = builds a plan from destination objects.
- addStop(route, destination) = appends a stop.
- removeStop(route, destinationId) = removes one.
- moveStop(route, destinationId, toIndex) = moves it to a zero-based index.
- withStart(route, start) = changes/clears the starting coordinate.
- clearRoute(route) = removes stops and keeps the start.

RoutePlan contains stops, start, legs and totalDistanceMeters. Distances are straight-line estimates. Without a start, only inter-stop distances count; one stop has distance zero.

Initial routes require valid destinations. Removing the last stop can leave an empty draft. Routes are not stored in Supabase. Road directions, optimization and map drawing are not implemented.

## Enrolment and visits

UserEnrolmentRepository:

- enrolInDestination(destinationId) = saves an enrolment without duplicates.
- getMyEnrolments() = lists the user's enrolments.
- getMyEnrolmentsWithDestinations() = includes destination details.

Enrolment does not unlock anything and is optional in the current completion flow.

UserUnlockRepository:

- startVisit(qrCode) = validates the QR and starts an attempt; no unlock yet.
- completeVisit(visitId, photoPath, latitude, longitude) = validates the owned attempt, uploaded photo and reported location within 50 metres; returns UserUnlock.
- cancelVisit(visitId) = cancels an unfinished attempt.
- getMyUnlocks() = lists own unlock records.
- getMyUnlocksWithDestinations() = includes destination details.

Use VisitCompletionCoordinator for normal camera/upload/location handling. Show success only after server acceptance. Refresh unlocks, map status and achievements afterward.

## Posts

Posts require an owned completed, uncancelled visit. Sharing is optional.

PostPhotoRepository.uploadPostPhoto(postId, jpegBytes) = uploads a JPEG up to 10 MiB and returns its private path. Generate a post UUID first.

PostRepository:

- createPost(postId, visitId, imagePath, imageDescription = "", visibility = PostVisibility.FRIENDS) = creates the post; the server derives the destination from the visit.
- getMyPosts() = lists own posts.
- getFriendsFeed(limit = 20) = lists accepted friends' posts, newest first; excludes own posts.
- getPublicFeed(limit = 20) = lists public posts for signed-in viewers, including their own.

Feed limits are 1–100. Older-page loading, single-post lookup, editing/deleting posts and changing visibility afterward are not implemented.

FRIENDS = author and accepted friends.
PUBLIC = any signed-in user. Guests cannot read either.

The attached image follows post visibility. Visit evidence stays private. Frontend image display needs authenticated storage access or suitable short-lived signed URLs; there is no production image-display helper. Previously downloaded images can remain cached after access changes.

## Likes and comments

PostInteractionRepository:

- likePost(postId) = ensures the user's like exists; returns true.
- unlikePost(postId) = ensures it is removed; returns false.
- getSummary(postId) = returns likeCount, commentCount and isLikedByMe.
- addComment(postId, content) = adds a trimmed, nonempty comment; repeating it creates another.
- getComments(postId, limit = 50) = lists newest comments first. Limit 1–100; inaccessible posts return an empty list.

Post visibility controls access. Counts come from the server. Fetch comments/counts separately; model defaults do not load them. Comment editing/deletion is not implemented.

## Friends

FriendshipRepository:

- sendFriendRequest(targetUserId) = sends a request with duplicate prevention.
- getIncomingFriendRequests() = lists pending requests received.
- getOutgoingFriendRequests() = lists pending requests sent.
- acceptFriendRequest(requestId) = accepts an incoming request.
- rejectFriendRequest(requestId) = declines one.
- getMyFriendships() = lists own friendship records.
- getMyFriends() = lists accepted friends with basic profile information.
- removeFriend(friendshipId) = removes that specific accepted friendship.

Removal revokes friends-only posts/photos and travel-summary access. Use the current friendship ID; an old retry cannot remove a newer friendship. Cached downloads cannot be withdrawn from another device.

## Achievements

AchievementRepository:

- getMyProgress() = lists location progress: verifiedDestinations, totalDestinations, isComplete and isEarned.
- getMyAchievements() = lists earned awards with location details, newest first.
- refreshMyAchievements() = asks the server to recheck the current user's eligibility.

All destinations in a nonempty location need the user's unlock plus a completed, uncancelled visit with a photo path. Legacy/manual unlocks alone do not qualify.

The server awards once per user/location. Awards remain earned if the catalogue grows, so isEarned can stay true while isComplete becomes false. Extra achievement tiers are not implemented.

## Notifications

NotificationRepository:

- getNotifications(limit = 50) = lists own available notices, newest first; limit 1–100.
- getNotification(notificationId) = returns one available notice, or null.
- getUnreadCount() = returns the full unread count.
- markAsRead(notificationId) = marks one read; safe to repeat.
- markAllAsRead() = marks own notices read.

Server triggers create like/comment/request notices. Self-interactions do not notify. Unlike removes its notice; resolving a request removes its pending notice.

Like targets use postId; comments use postId/commentId; requests use friendshipId/senderId. Check target availability before opening.

Navigation and a single-post lookup still need implementation. Device push and realtime subscriptions are not implemented.

## Shared setup

Each teammate needs SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in their own local.properties. SupabaseProvider installs Auth, Postgrest and Storage. Never include a service-role key or Google client secret in Android.

Keep migrations in Git. Do not rerun baseline migrations over existing shared tables; apply only intended unapplied changes.

Keep this guide and the visit-flow documentation updated when backend functions or access rules change.
