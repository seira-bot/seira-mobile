import Foundation

extension CompanionClient {
    // MARK: - Bots

    /// Say who a new bot is and how it looks. Anything left nil is the computer's to choose.
    public func createBot(_ spec: NewBot) async throws -> Bot {
        try await send(try makeRequest("POST", "/api/bots", body: spec.wireBody), as: CreatedBot.self).bot
    }

    /// Remove one bot. The computer refuses its default bot.
    public func deleteBot(botId: String) async throws {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        try await send(try makeRequest("DELETE", "/api/bots/\(botId)"))
    }

    /// Change how much one conversation may do before it asks. Custom is made on the desktop.
    /// Send `acknowledgeLocalAuto` after the person confirmed the warning for Auto on the computer itself.
    public func setApprovalMode(
        botId: String,
        threadId: String,
        mode: ApprovalMode,
        acknowledgeLocalAuto: Bool = false
    ) async throws -> Bot {
        guard mode.phoneCanSet else { throw APIError.transport("Custom approval is set on the desktop.") }
        guard Self.validRouteID(botId), Self.validRouteID(threadId) else { throw APIError.badURL }
        var body: [String: Any] = ["approvalMode": mode.rawValue]
        if acknowledgeLocalAuto { body["acknowledgeLocalAuto"] = true }
        return try await send(
            try makeRequest("PATCH", "/api/bots/\(botId)/tasks/\(threadId)", body: body),
            as: TaskPatchResponse.self
        ).bot
    }

    /// Send a held queued message into the turn that is running, without stopping it.
    /// Matched on the harness's own wording, like `cancelQueued`.
    public func steerQueued(queueId: String, to destination: MessageDestination) async throws -> SteerResult {
        guard Self.validRouteID(queueId) else { throw APIError.badURL }
        let route: String
        let threadId: String
        switch destination {
        case let .bot(id, thread):
            guard Self.validRouteID(id), Self.validRouteID(thread) else { throw APIError.badURL }
            route = "/api/bots/\(id)/queue/\(queueId)/steer"
            threadId = thread
        case let .room(id, thread):
            guard Self.validRouteID(id), Self.validRouteID(thread) else { throw APIError.badURL }
            route = "/api/groups/\(id)/queue/\(queueId)/steer"
            threadId = thread
        }
        do {
            return try await send(try makeRequest("POST", route, body: ["threadId": threadId]), as: SteerBody.self).result
        } catch let APIError.status(code, message) where code == 404 {
            if message?.localizedCaseInsensitiveContains(Self.alreadyDrainedQueueMessage) == true { return .gone }
            throw APIError.status(
                code: 404,
                message: "This computer is too old to send a queued message into a running turn. Update Seira on it."
            )
        }
    }

    public func skills(botId: String) async throws -> [BotSkill] {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        return try await send(try makeRequest("GET", "/api/bots/\(botId)/skills"), as: BotSkillsResponse.self).skills ?? []
    }

    public func botData(botId: String) async throws -> BotData {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        return try await send(try makeRequest("GET", "/api/bots/\(botId)/data"), as: BotData.self)
    }

    public func dataPage(botId: String, request: DataPageRequest) async throws -> DataPage {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        guard (request.table != nil) != (request.cardId != nil) else {
            throw APIError.transport("Ask for a table or a card, not both.")
        }
        guard request.offset >= 0, (1...DataPageRequest.maxLimit).contains(request.limit) else {
            throw APIError.transport("That page size is not allowed.")
        }
        var body: [String: Any] = ["offset": request.offset, "limit": request.limit]
        if let table = request.table { body["table"] = table }
        if let cardId = request.cardId { body["cardId"] = cardId }
        if let column = request.sortColumn {
            body["sort"] = ["column": column, "direction": request.sortDescending ? "desc" : "asc"]
        }
        if let filter = request.filter { body["filter"] = filter }
        if let filterColumn = request.filterColumn { body["filterColumn"] = filterColumn }
        return try await send(try makeRequest("POST", "/api/bots/\(botId)/data/page", body: body), as: DataPage.self)
    }

    // MARK: - Team

    public func teamMap() async throws -> TeamMap {
        try await send(try makeRequest("GET", "/api/team-map"), as: TeamMap.self)
    }

    /// Add an entry by hand. `section` is a sidebar heading's name; the empty string is General.
    public func addTeamMemory(
        section: String,
        kind: TeamMemoryKind,
        name: String,
        detail: String = "",
        aliases: [String]? = nil
    ) async throws -> TeamMemoryEdit {
        var body: [String: Any] = ["kind": kind.rawValue, "name": name, "detail": detail]
        if let aliases { body["aliases"] = aliases }
        return try await send(
            try makeRequest("POST", "/api/team-memory", query: [URLQueryItem(name: "section", value: section)], body: body),
            as: TeamMemoryEdit.self
        )
    }

    /// Edit one fact, or accept a proposed one. `revision` is the entry's own, from the last read.
    public func updateTeamMemory(
        section: String,
        entryId: String,
        revision: String,
        name: String? = nil,
        detail: String? = nil,
        aliases: [String]? = nil,
        kind: TeamMemoryKind? = nil,
        pinned: Bool? = nil,
        accept: Bool = false
    ) async throws -> TeamMemoryEdit {
        guard Self.validRouteID(entryId) else { throw APIError.badURL }
        var body: [String: Any] = ["expectedRevision": revision]
        if accept { body["accept"] = true }
        if let name { body["name"] = name }
        if let detail { body["detail"] = detail }
        if let aliases { body["aliases"] = aliases }
        if let kind { body["kind"] = kind.rawValue }
        if let pinned { body["pinned"] = pinned }
        return try await send(
            try makeRequest(
                "PATCH", "/api/team-memory/\(entryId)",
                query: [URLQueryItem(name: "section", value: section)], body: body
            ),
            as: TeamMemoryEdit.self
        )
    }

    public func deleteTeamMemory(section: String, entryId: String, revision: String) async throws -> TeamMemoryEdit {
        guard Self.validRouteID(entryId) else { throw APIError.badURL }
        let query = [URLQueryItem(name: "section", value: section), URLQueryItem(name: "revision", value: revision)]
        return try await send(try makeRequest("DELETE", "/api/team-memory/\(entryId)", query: query), as: TeamMemoryEdit.self)
    }

    // MARK: - Connected apps and computer

    /// Remove one signed-in account of a connected app. The app's other accounts stay.
    public func deleteConnectorAccount(connectorId: String, accountId: String) async throws {
        guard Self.validRouteID(connectorId), Self.validRouteID(accountId) else { throw APIError.badURL }
        try await send(try makeRequest("DELETE", "/api/connectors/\(connectorId)/accounts/\(accountId)"))
    }

    public func computerControl(botId: String, threadId: String? = nil) async throws -> ComputerControlState {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        return try await send(
            try makeRequest("GET", "/api/bots/\(botId)/computer/control", query: controlQuery(threadId)),
            as: ComputerControlState.self
        )
    }

    /// Take, give back or check the computer. `leaseId` ties the hold to this phone so it lapses when the phone goes away.
    public func controlComputer(
        botId: String,
        action: ComputerControlAction,
        leaseId: String? = nil,
        threadId: String? = nil,
        target: String? = nil
    ) async throws -> ComputerControlState {
        guard Self.validRouteID(botId) else { throw APIError.badURL }
        var body: [String: Any] = ["action": action.rawValue]
        if let leaseId { body["controlLeaseId"] = leaseId }
        if let target { body["target"] = target }
        return try await send(
            try makeRequest("POST", "/api/bots/\(botId)/computer/control", query: controlQuery(threadId), body: body),
            as: ComputerControlState.self
        )
    }

    private func controlQuery(_ threadId: String?) -> [URLQueryItem] {
        threadId.map { [URLQueryItem(name: "threadId", value: $0)] } ?? []
    }
}
