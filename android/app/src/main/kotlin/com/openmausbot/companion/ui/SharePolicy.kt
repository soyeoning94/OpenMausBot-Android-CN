package com.openmausbot.companion.ui

import com.openmausbot.companion.core.APIError
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionClient
import com.openmausbot.companion.core.CompanionEndpointKind
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.ConnectionAdvice
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.MessageDestination
import com.openmausbot.companion.core.Room
import java.net.URI

/**
 * The portable rules of inbound share — the Android half of
 * `ios/ShareExtension/ShareViewModel.swift` and `ShareItemLoader.swift`.
 *
 * Copying bytes and talking to ContentResolver stay in the sharing package.
 * What may be sent, where it may go, and the words the user sees when it
 * cannot, live here so the tests do not have to construct an Intent.
 */
object SharePolicy {
    const val MAXIMUM_ITEMS = 4
    const val MAXIMUM_TEXT_CHARACTERS = 100_000
    const val MAXIMUM_INSTRUCTION_CHARACTERS = 20_000
    const val MAXIMUM_TOTAL_ATTACHMENT_BYTES = 50 * 1_024 * 1_024
    const val MAXIMUM_IMAGE_BYTES = 10 * 1_024 * 1_024
    const val MAXIMUM_FILE_BYTES = CompanionClient.SHARE_FILE_MAX_BYTES
    const val MAXIMUM_URL_BYTES = 8_192

    val IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
    val DOCUMENT_MIMES = setOf(
        "text/plain", "text/markdown", "text/csv", "text/tab-separated-values",
        "application/json", "application/pdf", "application/rtf", "text/rtf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
    )

    enum class StreamKind { IMAGE, FILE, URL, TEXT, IGNORE }

    data class ShareComputer(
        val id: String,
        val name: String,
        val routeLabel: String,
    )

    data class ShareDestination(
        val id: String,
        val name: String,
        val subtitle: String,
        val kind: Kind,
        val latestActivity: Double,
        val supportsImages: Boolean,
        val destination: MessageDestination,
    ) {
        enum class Kind { BOT, CHANNEL }
    }

    data class SharePreview(
        val textCount: Int = 0,
        val linkCount: Int = 0,
        val attachmentNames: List<String> = emptyList(),
        val imageCount: Int = 0,
        val ignoredCount: Int = 0,
    ) {
        val isEmpty: Boolean
            get() = textCount == 0 && linkCount == 0 && attachmentNames.isEmpty()
    }

    fun classifyStream(mime: String?, fileName: String?): StreamKind {
        val normalized = mime?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (normalized in IMAGE_MIMES || normalized.startsWith("image/")) return StreamKind.IMAGE
        if (normalized in DOCUMENT_MIMES) return StreamKind.FILE
        val inferred = fileName?.substringAfterLast('.', "")?.lowercase()?.let(::mimeForExtension)
        if (inferred in IMAGE_MIMES) return StreamKind.IMAGE
        if (inferred in DOCUMENT_MIMES) return StreamKind.FILE
        if (normalized == "text/x-uri" || normalized == "text/uri-list") return StreamKind.URL
        if (normalized.startsWith("text/")) return StreamKind.TEXT
        return StreamKind.IGNORE
    }

    fun mimeForExtension(extension: String): String? = when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        "rtf" -> "application/rtf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        else -> null
    }

    fun validWebUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrEmpty() &&
            value.toByteArray().size <= MAXIMUM_URL_BYTES
    }

    fun tooManyItems(providerCount: Int): Boolean = providerCount > MAXIMUM_ITEMS

    fun instructionValidationMessage(instruction: String): String? =
        if (instruction.length > MAXIMUM_INSTRUCTION_CHARACTERS) {
            "请将可选指令控制在 20,000 个字符以内。"
        } else {
            null
        }

    fun imageCompatibilityMessage(preview: SharePreview, destination: ShareDestination?): String? {
        if (preview.imageCount == 0 || destination == null || destination.supportsImages) return null
        return if (destination.kind == ShareDestination.Kind.CHANNEL) {
            "此频道中可能回复的每个智能体都必须使用支持图片的模型。"
        } else {
            "${destination.name} 当前的模型不支持图片。请选择其他智能体，或不带图片进行分享。"
        }
    }

    fun canSend(
        ready: Boolean,
        preview: SharePreview,
        destination: ShareDestination?,
        instruction: String,
    ): Boolean = ready &&
        destination != null &&
        !preview.isEmpty &&
        imageCompatibilityMessage(preview, destination) == null &&
        instructionValidationMessage(instruction) == null

    fun computers(connections: List<Connection>, selected: Connection?): List<ShareComputer> =
        connections.map { connection ->
            ShareComputer(
                id = connection.id,
                name = connection.name,
                routeLabel = routeLabel(if (connection.id == selected?.id) selected else connection),
            )
        }

    fun routeLabel(connection: Connection): String {
        // Until a client actually works this session, iOS shows "Automatic".
        val kind = connection.activeEndpoint?.kind ?: return "自动"
        return when (kind) {
            CompanionEndpointKind.HOSTED -> "安全 HTTPS"
            CompanionEndpointKind.TAILNET -> "Tailscale"
            CompanionEndpointKind.LAN, CompanionEndpointKind.BONJOUR -> "局域网"
        }
    }

    fun destinations(fleet: Fleet, imageCapableInstances: Set<String>): List<ShareDestination> {
        val botById = fleet.bots.associateBy(Bot::id)
        val bots = fleet.bots.filter { it.hidden != true }.map { bot ->
            val task = bot.tasks?.firstOrNull { it.threadId == bot.threadId }?.title
            ShareDestination(
                id = "bot:${bot.id}",
                name = bot.name,
                subtitle = task?.let { "智能体 · $it" } ?: "智能体",
                kind = ShareDestination.Kind.BOT,
                latestActivity = bot.messages?.maxOfOrNull { it.at } ?: bot.createdAt,
                supportsImages = bot.modelSelection.instanceId in imageCapableInstances,
                destination = MessageDestination.Bot(bot.id, bot.threadId),
            )
        }
        val channels = fleet.groups.map { room ->
            val task = room.tasks?.firstOrNull { it.threadId == room.threadId }?.title
            ShareDestination(
                id = "channel:${room.id}",
                name = room.name,
                subtitle = task?.let { "频道 · $it" } ?: "频道",
                kind = ShareDestination.Kind.CHANNEL,
                latestActivity = room.messages?.maxOfOrNull { it.at } ?: room.createdAt,
                supportsImages = roomSupportsImages(room, botById, imageCapableInstances),
                destination = MessageDestination.Room(room.id, room.threadId),
            )
        }
        return (bots + channels).sortedWith(
            compareByDescending<ShareDestination> { it.latestActivity }.thenBy { it.name.lowercase() },
        )
    }

    private fun roomSupportsImages(
        room: Room,
        botById: Map<String, Bot>,
        imageCapableInstances: Set<String>,
    ): Boolean = room.memberIds.isNotEmpty() && room.memberIds.all { id ->
        val instance = botById[id]?.modelSelection?.instanceId ?: return@all false
        instance in imageCapableInstances
    }

    fun rememberedSelection(destinations: List<ShareDestination>, rememberedId: String?): String? =
        rememberedId?.takeIf { id -> destinations.any { it.id == id } }
            ?: destinations.firstOrNull()?.id

    fun shouldPreservePreparedDelivery(error: Throwable): Boolean {
        val api = generateSequence(error) { it.cause }.filterIsInstance<APIError>().firstOrNull()
            ?: return true
        return when (api) {
            is APIError.Transport -> true
            is APIError.Status -> api.code == 408 || api.code == 429 || api.code >= 500
            APIError.BadUrl -> false
        }
    }

    fun isImageSupportUnavailable(error: Throwable): Boolean {
        val api = generateSequence(error) { it.cause }.filterIsInstance<APIError.Status>().firstOrNull()
        return api?.code == 404
    }

    fun destinationKey(connectionId: String): String = "share.last-destination.$connectionId"

    fun friendlyMessage(error: Throwable, computerName: String?): String {
        when (error) {
            is ShareLoadException -> return error.message ?: generic()
        }
        val api = generateSequence(error) { it.cause }.filterIsInstance<APIError>().firstOrNull()
        if (api?.isUnauthorized == true) return pairingExpired()
        // withPairedShareClient's three human Transport strings arrive as
        // APIError.Transport (core is out of scope). Pass those through at the
        // border instead of collapsing them into the generic offline line.
        if (api is APIError.Transport && isKnownShareTransport(api.detail)) {
            return api.detail
        }
        if (api != null && isAmbiguousTransport(api)) {
            return computerName?.let(::offline) ?: "无法连接到你的电脑。请保持 OpenMausBot 打开并开启手机访问权限，然后重试。"
        }
        // Mirror ShareViewModel: raw exception text never reaches the sheet.
        return generic()
    }

    private fun isKnownShareTransport(detail: String): Boolean =
        detail == "Unlock this phone, then try sharing again." ||
            detail == "请先解锁此手机，然后重试分享。" ||
            detail == "This saved connection is no longer available on this phone. Remove it and pair again." ||
            detail == "此已保存的连接在本手机上已不可用。请移除并重新配对。" ||
            (
                detail.startsWith("Couldn't reach ") &&
                    detail.endsWith(". Keep OpenMausBot open and Phone access on, then try again.")
                ) ||
            (
                detail.startsWith("无法连接到 ") &&
                    detail.endsWith("。请保持 OpenMausBot 打开并开启手机访问权限，然后重试。")
                )

    private fun isAmbiguousTransport(error: APIError): Boolean = when (error) {
        is APIError.Transport -> true
        is APIError.Status -> ConnectionAdvice.shouldRetryPairingOnAnotherRoute(error)
        APIError.BadUrl -> false
    }

    fun notPaired(): String =
        "更新后请先打开一次 OpenMausBot 应用。如果此手机仍未连接，请先配对后再分享。"

    fun noDestinations(): String =
        "还没有可发送到的智能体或频道。请先在电脑上创建一个。"

    fun imageSupportUnavailable(): String =
        "请先更新此电脑上的 OpenMausBot，然后再分享图片。"

    fun offline(name: String): String =
        "无法连接到 $name。请保持 OpenMausBot 打开并开启手机访问权限，然后重试。"

    fun nothingSupported(): String =
        "没有可发送的文本、链接、图片或受支持的文档。"

    fun tooManyItems(): String = "每次最多发送 4 项内容。"

    fun tooMuchText(): String = "文本内容过大，无法分享。请发送较短的内容。"

    fun tooLarge(name: String, limitMb: Int): String = "$name 超过 $limitMb MB 限制。"

    fun unsupportedDocument(name: String): String =
        "$name 不是受支持的文档格式。请尝试 PDF、纯文本、Word、Excel 或 PowerPoint。"

    fun unreadable(name: String): String =
        "OpenMausBot 无法读取 $name。请先尝试将其导出到“文件”应用。"

    fun sendTimedOut(): String = "发送耗时过长，请检查网络连接后重试。"

    fun generic(): String = "OpenMausBot 无法发送此内容，请重试。"

    fun pairingExpired(): String =
        "此手机的配对已过期。请打开 OpenMausBot 并重新配对。"

    fun ignoredCaption(count: Int): String =
        "已忽略 $count 个不支持的项目。"

    fun previewChip(preview: SharePreview): List<String> = buildList {
        if (preview.linkCount > 0) add(if (preview.linkCount == 1) "链接" else "${preview.linkCount} 个链接")
        if (preview.textCount > 0) add(if (preview.textCount == 1) "文本" else "${preview.textCount} 段文本")
        addAll(preview.attachmentNames)
    }
}

class ShareLoadException(message: String) : Exception(message)
