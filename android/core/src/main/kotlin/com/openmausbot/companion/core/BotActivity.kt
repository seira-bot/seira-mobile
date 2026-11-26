package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

/** One line of a bot's activity log: what ran, in words, and how it ended. Read-only. */
@Serializable
data class ActivityRow(
    /** ISO 8601: when a tool started or an approval was asked. */
    val at: String,
    val threadId: String,
    val turnId: String? = null,
    val requestId: String? = null,
    val tool: String,
    val app: String? = null,
    val label: String,
    val labelKey: String? = null,
    val appKey: String? = null,
    val summary: String? = null,
    val summaryCode: String? = null,
    /** ran, failed, running, allowed, denied or waiting. */
    val outcome: String,
)

@Serializable
internal data class ActivityPage(val rows: List<ActivityRow>)
