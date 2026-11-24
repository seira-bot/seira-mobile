package com.openmausbot.companion.core

data class WorksOnOffer(val withoutComputer: Boolean, val ownServer: Boolean)

sealed interface WorksOnRefusal {
    data object Forbidden : WorksOnRefusal
    data class Server(val sentence: String) : WorksOnRefusal
    data object Failed : WorksOnRefusal
}

sealed interface WorksOnResult {
    data class Changed(val bot: Bot) : WorksOnResult
    data class Refused(val reason: WorksOnRefusal) : WorksOnResult
}

/** Where a phone that may administer can move a bot from the cloud-computer notice. */
object WorksOnChoice {
    private const val NOT_SET_UP = "cc-not-set-up"

    fun isNotice(tool: ToolActivity): Boolean =
        (tool.notice == true && tool.place?.state == NOT_SET_UP) || CloudComputerNotice.matches(tool)

    /** The newest notice row the transcript draws. */
    fun noticeMessageId(messages: List<Message>): String? {
        val repeated = CloudComputerNotice.repeatedIds(messages)
        return messages.lastOrNull {
            it.kind == Message.Kind.ACTIVITY && it.id !in repeated && it.tool?.let(::isNotice) == true
        }?.id
    }

    fun ownServerReady(bot: Bot?, config: ConfigStatus?): Boolean =
        !bot?.vpsAlias.isNullOrBlank() || config?.vps?.configured == true

    fun offer(
        canAdminister: Boolean,
        running: Boolean,
        computer: String?,
        cloudBackend: String?,
        ownServerReady: Boolean,
        phoneWorksOn: Boolean?,
    ): WorksOnOffer? {
        if (!canAdminister || running || phoneWorksOn != true) return null
        val onOwnServer = computer == "cloud" && cloudBackend == "vps"
        val offer = WorksOnOffer(
            withoutComputer = computer != "off",
            ownServer = ownServerReady && !onOwnServer,
        )
        return offer.takeIf { it.withoutComputer || it.ownServer }
    }

    fun refusal(error: Throwable): WorksOnRefusal {
        val status = error as? APIError.Status ?: return WorksOnRefusal.Failed
        if (status.code == 403) return WorksOnRefusal.Forbidden
        val sentence = status.serverMessage?.takeIf { it.isNotBlank() } ?: return WorksOnRefusal.Failed
        return WorksOnRefusal.Server(sentence)
    }
}
