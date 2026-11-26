package com.openmausbot.companion.core

import kotlinx.serialization.Serializable

/** Two bots that talk in a private room of their own. */
@Serializable
data class TeamCollaboration(
    val groupId: String,
    val botIds: List<String>,
    val lastAt: Double,
)

/** Work one bot handed to another that has not finished. */
@Serializable
data class TeamHandoff(
    val sourceBotId: String,
    val targetBotId: String,
    val threadId: String,
    val groupId: String? = null,
)

/** Who works with whom. It carries ids, links and times, never message text. */
@Serializable
data class TeamMap(
    val collaborations: List<TeamCollaboration> = emptyList(),
    val queued: List<TeamHandoff> = emptyList(),
    val running: List<TeamHandoff> = emptyList(),
)
