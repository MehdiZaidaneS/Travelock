# Visit flow

Scan the destination QR, take a live photo, then complete the visit within 50 metres. Enrolment is optional in the current implementation.

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

LocationPermissionRequired, LocationServicesDisabled and LocationUnavailable need an explanation and retry action. Server errors throw; retain the attempt/path.

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

Development repository/manual emulator checks were reported passed. The physical-device camera/GPS check is deferred. Retry, recreation and reopen test modes cover different paths; one mode passing does not prove all modes.
