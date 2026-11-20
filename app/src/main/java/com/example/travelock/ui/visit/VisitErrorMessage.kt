package com.example.travelock.ui.visit

// SDK exception messages can include request headers and bearer tokens. Never
// render them, even for unknown errors. Recognize only known first-line reasons
// and return application-owned text; no part of the exception is interpolated.
internal fun visitErrorMessage(error: Exception): String {
    if (error is VisitPhotoCleanupException) {
        return "Visit cancelled, but photo cleanup failed. Cancel again to retry cleanup."
    }
    return when (error.message?.lineSequence()?.firstOrNull()?.trim()) {
        "You must be within 50 metres of the destination." ->
            "You must be within 50 metres of the destination. Move closer and retry verification."
        "Invalid QR code.", "QR code matches multiple destinations.",
        "Scan the QR code for the selected destination." ->
            "This QR code is not valid for the selected destination. Please scan its QR code."
        "Finish or cancel your saved visit before starting another destination." ->
            "Finish or cancel your saved visit before starting another destination."
        "This destination is unavailable. Try again later." -> "This destination is unavailable. Try again later."
        "Resume or cancel the current visit first." -> "Resume or cancel the current visit first."
        "Enrol in this destination first." -> "Enroll in this destination first."
        "Allow precise location, then take the photo again or retry." ->
            "Allow precise location, then take the photo again or retry."
        "Turn on location services, then try again." -> "Turn on location services, then try again."
        "Could not get your location. Try again outdoors." -> "Could not get your location. Try again outdoors."
        "Load saved visit progress before starting a visit.", "Load saved visit progress before enrolling." ->
            "Load saved visit progress before continuing."
        "A visit photo is required.", "Upload a photo for this visit before completing it." ->
            "A photo for this visit is required. Take a photo and try again."
        "This visit was completed with a different photo." -> "This visit was already completed with another photo."
        "This visit has been cancelled." -> "This visit has been cancelled. Start a new visit."
        "Visit attempt not found." -> "This visit is no longer available. Please start again."
        "A completed visit cannot be cancelled." -> "A completed visit cannot be cancelled."
        "You must sign in before starting a visit.", "You must sign in before completing a visit.",
        "You must sign in before cancelling a visit.", "You must sign in before enrolling in a destination.",
        "You must sign in before loading your enrolments." -> "Please sign in again to continue your visit."
        "You must sign in before removing an enrolment." -> "Please sign in again to remove your enrollment."
        "Valid location coordinates are required." -> "Could not verify your location. Please try again outdoors."
        "Destination coordinates are invalid." -> "This destination's location needs to be corrected. Please try another destination."
        "Could not save visit progress. Please try again." -> "Could not save visit progress. Please try again."
        "The visit photo must be at most 10 MiB." -> "The photo is too large. Take another photo and try again."
        else -> "Could not finish this visit action. Check your connection and try again."
    }
}
