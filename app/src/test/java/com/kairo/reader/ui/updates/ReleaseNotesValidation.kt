package com.kairo.reader.ui.updates

/** Entry point for :app:validateChangelog; kept out of the shipped application. */
fun main(args: Array<String>) {
    validatePublishedReleaseNotes(args[0], args[1].toInt(), releaseNotes)
}

internal fun validatePublishedReleaseNotes(
    version: String,
    versionCode: Int,
    notes: List<ReleaseNotes>,
) {
    check(notes.count { it.version == version && it.versionCode == versionCode } == 1) {
        "Android versionName=$version, versionCode=$versionCode must have exactly one matching published " +
            "entry in ReleaseNotes.kt. Finalise the Upcoming entry's version and versionCode before shipping; " +
            "unpublished notes cannot trigger the What's New invitation."
    }
}
