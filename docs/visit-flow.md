# Visit flow

Enroll in a destination, scan its QR, take a live photo, then complete the visit within 50 metres. The app requires enrolment before a new scan; the existing server RPCs do not enforce enrolment.

## App screens

The destination sheet shows unlocking requirements and an Enroll button. Enroll saves the enrolment through UserEnrolmentRepository and stays on the details sheet, showing Enrolled. It does not open the scanner or create a visit attempt. A separate Verify visit action opens verification when the user is ready. Home lists unfinished enrolled destinations under Your visits; selecting one opens its details and requirements.

VisitFlowHost = keeps app content in place and hosts one destination sheet for details and verification. VisitFlowController = serializes enrolment, scan, completion, retry, cancellation and unenrolment actions for the signed-in user. MainActivity is unchanged.

Verify visit expands verification inside the same destination sheet, retaining the destination header. A QR / Photo / Unlock indicator shows progress, with one main action at a time: Scan QR, Take photo, or Verify and unlock when a photo was already uploaded. Errors appear inline beside the actions. QR and camera are consecutive steps; the system scanner/camera still opens externally. Only a matching destination QR starts the attempt.

Continue later keeps the checkpoint; Resume visit reopens it. Reopening the app loads the current user's pending attempt. Finish or cancel it before starting a different destination. Location failures retain the attempt; an upload followed by failed verification retains its path for retry. A photo discarded before upload requires a new capture.

Successful completion refreshes home unlocks and enrolments. The map still uses its existing mock catalogue/status; its Enroll action now uses the real visit flow, so a demo destination missing from Supabase cannot be enrolled or verified. Live map data and optional sharing remain separate integration work.

## Unenroll

Unenroll asks for confirmation, then removes the selected user's destination plan. For a matching unfinished attempt, the app first cancels it and deletes its uploaded evidence. Cleanup failure retains the plan/checkpoint for retry. If deletion fails after successful cancellation, the plan remains and Unenroll can retry without cancelling again. Cancellation is completed before deletion; these network operations are not atomic.

Cancel verification asks for confirmation and cancels/cleans up the attempt but keeps the enrolment. Completed visits, unlocks and achievements are not deleted by unenrolment. The sheet shows completed destinations as unlocked rather than offering Unenroll; completed plans can still be removed using the repository without changing their history.

## Calls in order

1. VisitQrScanButton returns the exact QR text. Pass it to UserUnlockRepository.startVisit(qrCode).
2. Keep the VisitAttempt. Scanning alone does not award an unlock.
3. VisitCameraButton captures a new photo; there is no gallery picker in this flow.
4. Call VisitCompletionCoordinator(context).completeCapturedVisit(visit, jpegBytes, onPhotoUploaded).
5. Save the uploaded path in onPhotoUploaded so retries can reuse it.
6. Show success only for VisitCompletionResult.Completed(unlock). Refresh unlocks, map data and achievements.

The server checks ownership, attempt state, uploaded private photo presence and reported coordinates within 50 metres. JPEG uploads are limited to 10 MiB.

The camera flow requires a new capture. Server checks do not establish genuine camera/GPS provenance or recognize the scene; client-reported coordinates can be spoofed.

## Permission and retry

Request foreground precise location from the screen. The Google scanner/system camera handle their capture interfaces.

VisitLocationAccess = shows Precise location allowed when permission is already granted. Otherwise Allow precise location requests it, and Open app settings lets the user change blocked/approximate access. If device location services are off, Turn on location services opens Android location settings. Permission/service state refreshes after returning from Settings or the camera. Permission grant alone does not complete a visit; take the photo or retry verification afterward.

LocationPermissionRequired, LocationServicesDisabled and LocationUnavailable need an explanation and retry action. Server errors throw; retain the attempt/path.

VisitErrorMessage = maps known error reasons to fixed app-written text and uses a generic fallback for unknown failures. Never display SDK exception messages, request URLs, headers or credentials. A rejected distance check keeps the uploaded path and Verify and unlock available for retry. The radius remains 50 metres.

After upload succeeds, use completeUploadedVisit(visit, photoPath). It obtains a fresh location and reuses the photo.

Lower-level calls are VisitPhotoRepository.uploadPhoto(visit, jpegBytes) and UserUnlockRepository.completeVisit(visitId, photoPath, latitude, longitude). Prefer the coordinator for screen integration.

## Save and restore

VisitProgress holds visit, uploadedPhotoPath and completed.

- rememberVisitProgress(userId, initialProgress) = keeps metadata through Compose recreation.
- VisitProgressStore(context).save(userId, progress) = writes a valid owned attempt/path to disk.
- VisitProgressStore(context).load(userId) = returns that user's checkpoint, or empty VisitProgress.

JPEG bytes are not saved. Completed/cancelled/empty progress clears the checkpoint. Keep checkpoints separated by user; the server still validates every completion.

## Cancel

VisitCancellationCoordinator(context).cancel(visit) = cancels the server attempt, deletes cancelled-visit photos through Storage, then clears the checkpoint.

The app requests deletion; users do not need to remove files in the dashboard. If cleanup fails, VisitPhotoCleanupException is thrown. Keep the checkpoint and retry cancellation. Completed visits cannot be cancelled.

There is no historical orphan sweep or automatic recovery of an upload with an unknown response.

## After completion

Completion is retry-safe and does not duplicate destination unlocks. Achievements require the completed photo-backed visit as well as the unlock; manually inserted unlocks alone do not qualify.

Visit evidence remains private. Publishing a post is a separate optional step with a separate photo upload. PUBLIC posts still require signed-in viewers.

The completed destination sheet offers Share photo and Done. Share photo also works when reopening an unlocked destination with a completed photo-backed visit. It previews the latest completed visit's same camera image, with an optional caption and Friends selected by default. Public means all signed-in Travelock users. Only Post publishes; Done/Not now leave the visit completed without creating a post.

The existing feed and post card display the result. Friends & you includes the author's own posts; View in feed opens the matching audience feed and highlights the requested own post at the start. If the latest completed visit already has a post, sharing shows Already shared and View in feed instead of creating another.

Verification and sharing disable sheet swipe dismissal while keeping content scrollable. Back or Close on an unposted sharing preview asks Leave sharing?; Keep editing retains the current caption and audience. Explicitly leaving discards unsent edits, while an attempted publish request remains available for confirmation or retry.

After an attempted publish, retries keep the same post ID, caption and audience. The request is saved per account/visit before uploading, so reopening can confirm a response that was lost. Photo bytes stay in memory; previewing again downloads the private visit image. Errors are fixed app-written messages and never expose SDK request details. Sharing failure leaves the unlock and completed evidence intact. Unsent caption edits before tapping Post are not persisted, and interrupted uploads can leave an unattached private post image until retried.

Development repository/manual emulator checks were reported passed. The physical-device camera/GPS check is deferred. Retry, recreation and reopen test modes cover different paths; one mode passing does not prove all modes.
