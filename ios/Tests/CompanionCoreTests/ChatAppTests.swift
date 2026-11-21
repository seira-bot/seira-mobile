import CryptoKit
import Foundation
import XCTest
@testable import CompanionCore

private final class ChatAppRequestStub: URLProtocol {
    static var capturedRequest: URLRequest?
    static var responseBody = Data()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseBody)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

final class ChatAppTests: XCTestCase {
    private func fixture(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: name, withExtension: "json")
        else {
            XCTFail("missing fixture \(name).json")
            throw CocoaError(.fileNoSuchFile)
        }
        return try Data(contentsOf: url)
    }

    private func messages() throws -> [String: Message] {
        let page = try JSONDecoder().decode(ThreadPage.self, from: try fixture("app-messages"))
        return Dictionary(uniqueKeysWithValues: page.messages.map { ($0.id, $0) })
    }

    private func documents() throws -> [String: ChatAppDocument] {
        let raw = try XCTUnwrap(try JSONSerialization.jsonObject(with: try fixture("app-documents")) as? [String: Any])
        return try Dictionary(uniqueKeysWithValues: raw.map { key, value in
            (key, ChatAppDocument(data: try JSONSerialization.data(withJSONObject: value)))
        })
    }

    func testAppMessagesDecodeWithTheirPayload() throws {
        let all = try messages()
        let counter = try XCTUnwrap(all["app-counter"])
        XCTAssertEqual(counter.kind, .app)
        let app = try XCTUnwrap(counter.app)
        XCTAssertEqual(app.title, "Coffee counter")
        XCTAssertEqual(app.source, "html")
        XCTAssertEqual(app.rev, 0)
        XCTAssertEqual(app.height, 200)
        XCTAssertEqual(try XCTUnwrap(all["app-report"]?.app).rev, 1)
        let mcp = try XCTUnwrap(all["app-mcp-basic"]?.app)
        XCTAssertEqual(mcp.source, "mcp")
        XCTAssertEqual(mcp.mcp?.tool, "get-time")
        XCTAssertEqual(all["app-too-large"]?.app?.blocked, "too-large")
    }

    func testAnUnreadablePayloadLeavesTheTextAndTheRestOfThePage() throws {
        let all = try messages()
        let broken = try XCTUnwrap(all["app-broken"])
        XCTAssertEqual(broken.text, "An app this build cannot read")
        XCTAssertEqual(broken.app?.isOpenablePage, false)
        XCTAssertEqual(all["app-sent"]?.kind, .text)
        XCTAssertNotNil(all["app-future"]?.app)
    }

    func testOnlyAStoredHtmlPageOffersOpen() throws {
        let all = try messages()
        for id in ["app-counter", "app-chart", "app-report", "app-fragment", "app-game"] {
            XCTAssertEqual(all[id]?.app?.isOpenablePage, true, id)
        }
        for id in ["app-mcp-basic", "app-mcp-excalidraw", "app-mcp-debugError", "app-too-large", "app-future", "app-broken"] {
            XCTAssertEqual(all[id]?.app?.isOpenablePage, false, id)
        }
        XCTAssertFalse(ChatApp(source: "html", doc: "abc", live: true).isOpenablePage)
        XCTAssertFalse(ChatApp(source: "html", doc: "abc", blocked: "unavailable").isOpenablePage)
    }

    func testViewOnlyKeepsOnlyOpenLinks() throws {
        let document = try XCTUnwrap(try documents()["app-counter"])
        XCTAssertEqual(document.capabilities["message"], true)
        let limited = try document.viewOnly()
        XCTAssertEqual(
            limited.capabilities,
            ["serverTools": false, "serverResources": false, "message": false, "modelContext": false, "openLinks": true]
        )
        let before = try XCTUnwrap(try JSONSerialization.jsonObject(with: document.data) as? [String: Any])
        let after = try XCTUnwrap(try JSONSerialization.jsonObject(with: limited.data) as? [String: Any])
        XCTAssertEqual(before["srcdoc"] as? String, after["srcdoc"] as? String)
        let mcp = try XCTUnwrap(try documents()["app-mcp-basic"]).viewOnly()
        XCTAssertEqual(mcp.capabilities["serverTools"], false)
    }

    func testFoldersAndMcpAppsAreRecognisedFromTheirDocument() throws {
        let all = try documents()
        for id in ["app-counter", "app-chart", "app-report", "app-fragment", "app-game"] {
            let document = try XCTUnwrap(all[id])
            XCTAssertFalse(document.isMcpApp, id)
            XCTAssertFalse(document.isFolderPage, id)
            XCTAssertTrue(document.canOpenOnPhone, id)
        }
        for id in ["app-mcp-basic", "app-mcp-excalidraw", "app-mcp-debugError"] {
            XCTAssertTrue(try XCTUnwrap(all[id]).isMcpApp, id)
            XCTAssertFalse(try XCTUnwrap(all[id]).canOpenOnPhone, id)
        }
        let folder = ChatAppDocument(data: Data(#"{"srcdoc":"<base>","csp":"default-src 'none'; base-uri https://pc.example/files/"}"#.utf8))
        XCTAssertTrue(folder.isFolderPage)
        XCTAssertFalse(folder.canOpenOnPhone)
    }

    func testAllowedOriginsAreTheNetworkTheDocumentNames() throws {
        XCTAssertEqual(
            try XCTUnwrap(try documents()["app-counter"]).allowedOrigins,
            ["https://cdnjs.cloudflare.com", "https://cdn.jsdelivr.net", "https://unpkg.com",
             "https://fonts.googleapis.com", "https://fonts.gstatic.com"]
        )
    }

    func testBundledHostPageMatchesTheHashTheFixturesName() throws {
        let file = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("App/Resources/app-host.html")
        let html = try String(contentsOf: file, encoding: .utf8)
        let marker = try XCTUnwrap(html.range(of: #"^<!-- seira app-host v1 sha256:[0-9a-f]{64} -->\n"#, options: .regularExpression))
        let line = String(html[marker])
        let named = String(line.dropFirst("<!-- seira app-host v1 sha256:".count).prefix(64))
        let body = String(html[marker.upperBound...])
        let digest = SHA256.hash(data: Data(body.utf8)).map { String(format: "%02x", $0) }.joined()
        let expectations = try XCTUnwrap(try JSONSerialization.jsonObject(with: try fixture("app-expectations")) as? [String: Any])
        XCTAssertEqual(expectations["appHostSha256"] as? String, named)
        XCTAssertEqual(digest, named)
    }

    func testClientFetchesTheDocumentWithItsTokenAndNoQuery() async throws {
        ChatAppRequestStub.capturedRequest = nil
        ChatAppRequestStub.responseBody = Data(#"{"v":1,"messageId":"m-1","rev":0,"srcdoc":"<p>hi</p>","csp":"","network":{"connect":[],"resources":[]},"can":{}}"#.utf8)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ChatAppRequestStub.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let client = CompanionClient(
            connection: Connection(name: "Mac", host: "127.0.0.1", port: 8810),
            token: "paired-token",
            session: session
        )
        let document = try await client.appDocument(threadId: "thread-1", messageId: "m-1")
        XCTAssertTrue(document.canOpenOnPhone)
        let request = try XCTUnwrap(ChatAppRequestStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "GET")
        XCTAssertEqual(request.url?.path, "/api/threads/thread-1/messages/m-1/app")
        XCTAssertNil(request.url?.query)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer paired-token")
        do {
            _ = try await client.appDocument(threadId: "../x", messageId: "m-1")
            XCTFail("a path in a route id must be refused")
        } catch {}
    }
}
