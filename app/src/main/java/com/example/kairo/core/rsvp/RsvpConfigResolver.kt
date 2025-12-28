package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig

object RsvpConfigResolver {
    fun resolve(
        baseConfig: RsvpConfig,
        languageTag: String?,
    ): RsvpConfig = baseConfig
}
