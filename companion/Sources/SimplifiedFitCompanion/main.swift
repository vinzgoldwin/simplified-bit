import AppKit
import CryptoKit
import Foundation
import Network

@main
struct SimplifiedFitCompanion {
    static func main() {
        let app = NSApplication.shared
        let delegate = CompanionDelegate()
        app.delegate = delegate
        app.setActivationPolicy(.accessory)
        app.run()
    }
}

@MainActor
final class CompanionDelegate: NSObject, NSApplicationDelegate, @unchecked Sendable {
    private let controller = CompanionController()
    private var statusItem: NSStatusItem!
    private let statusMenu = NSMenu()

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem.button?.title = "SF"
        statusItem.button?.toolTip = "Simplified Fit Coach"
        statusItem.menu = statusMenu
        rebuildMenu()
        controller.onStatusChange = { [weak self] in DispatchQueue.main.async { self?.rebuildMenu() } }
        controller.start()
    }

    @objc private func toggle() {
        controller.running ? controller.stop() : controller.start()
        rebuildMenu()
    }

    @objc private func copyPairing() {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(controller.pairingText, forType: .string)
    }

    @objc private func quit() {
        controller.stop()
        NSApplication.shared.terminate(nil)
    }

    private func rebuildMenu() {
        statusMenu.removeAllItems()
        let status = NSMenuItem(title: controller.statusText, action: nil, keyEquivalent: "")
        status.isEnabled = false
        statusMenu.addItem(status)
        statusMenu.addItem(.separator())
        statusMenu.addItem(NSMenuItem(title: controller.running ? "Turn Coach Off" : "Turn Coach On", action: #selector(toggle), keyEquivalent: ""))
        statusMenu.addItem(NSMenuItem(title: "Copy Pairing Details", action: #selector(copyPairing), keyEquivalent: ""))
        statusMenu.addItem(.separator())
        statusMenu.addItem(NSMenuItem(title: "Quit", action: #selector(quit), keyEquivalent: "q"))
    }
}

final class CompanionController: @unchecked Sendable {
    private let queue = DispatchQueue(label: "simplified-fit.coach")
    private var listener: NWListener?
    private let port: UInt16 = 7447
    private let token: String
    var onStatusChange: (() -> Void)?

    init() {
        let defaults = UserDefaults.standard
        if let saved = defaults.string(forKey: "pairingToken") {
            token = saved
        } else {
            let generated = Self.randomToken()
            defaults.set(generated, forKey: "pairingToken")
            token = generated
        }
    }

    var running: Bool { listener != nil }
    var statusText: String { running ? "Coach on · port \(port)" : "Coach off" }
    var pairingText: String { "http://\(Self.tailscaleAddress() ?? "TAILSCALE-IP"):\(port)|\(token)" }

    func start() {
        guard listener == nil else { return }
        do {
            let newListener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: port)!)
            newListener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
            newListener.stateUpdateHandler = { [weak self] state in
                if case .failed = state { self?.listener = nil }
                self?.onStatusChange?()
            }
            newListener.start(queue: queue)
            listener = newListener
        } catch {
            listener = nil
        }
        onStatusChange?()
    }

    func stop() {
        listener?.cancel()
        listener = nil
        onStatusChange?()
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveAll(connection, data: Data())
    }

    private func receiveAll(_ connection: NWConnection, data: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1_048_576) { [weak self] chunk, _, complete, error in
            var accumulated = data
            if let chunk { accumulated.append(chunk) }
            if complete || error != nil || Self.requestComplete(accumulated) {
                self?.respond(to: connection, requestData: accumulated)
            } else {
                self?.receiveAll(connection, data: accumulated)
            }
        }
    }

    private func respond(to connection: NWConnection, requestData: Data) {
        guard let request = HTTPRequest(data: requestData) else {
            send(connection, status: 400, json: ["error": "Invalid request"])
            return
        }
        guard request.headers["authorization"] == "Bearer \(token)" else {
            send(connection, status: 401, json: ["error": "Pairing token rejected"])
            return
        }
        if request.method == "GET" && request.path == "/health" {
            send(connection, status: 200, json: ["status": "ok"])
            return
        }
        guard request.method == "POST", request.path == "/chat",
              let body = try? JSONSerialization.jsonObject(with: request.body) as? [String: Any],
              let message = body["message"] as? String,
              let health = body["healthContext"] as? String else {
            send(connection, status: 404, json: ["error": "Unknown request"])
            return
        }
        let previousQuestion = body["previousQuestion"] as? String
        let previousAnswer = body["previousAnswer"] as? String
        queue.async { [weak self] in
            do {
                let answer = try self?.runCodex(
                    message: message,
                    health: health,
                    previousQuestion: previousQuestion,
                    previousAnswer: previousAnswer
                ) ?? CoachAnswer(
                    response: "Coach unavailable.",
                    reasoning: [],
                    suggestions: []
                )
                self?.send(connection, status: 200, json: [
                    "response": answer.response,
                    "reasoning": answer.reasoning,
                    "suggestions": answer.suggestions,
                ])
            } catch {
                self?.send(connection, status: 500, json: ["error": error.localizedDescription])
            }
        }
    }

    private func runCodex(
        message: String,
        health: String,
        previousQuestion: String?,
        previousAnswer: String?
    ) throws -> CoachAnswer {
        let previousExchange = if let previousQuestion, let previousAnswer {
            """
            PREVIOUS EXCHANGE
            Question: \(previousQuestion)
            Answer: \(previousAnswer)
            """
        } else {
            "PREVIOUS EXCHANGE\nNone"
        }
        let prompt = """
        You are a warm, attentive personal wellness coach inside Simplified Fit. The supplied health summary is the sole source of personal facts. You may apply general wellness knowledge, but never invent measurements, history, symptoms, or causes. Treat unavailable fields as unknown. Do not use tools, inspect files, or seek external data.

        Answer the current question directly. Use the previous exchange only when it is relevant or resolves a follow-up reference. Ground conclusions in the supplied signals and favor personal baselines and multi-day trends over generic ranges or a single reading. Separate observation from inference and acknowledge stale, sparse, conflicting, or missing data. The response field must contain only the direct answer and any useful actions or monitoring advice. Do not repeat the reasoning summary or follow-up questions inside the response field.

        When a recommendation would help, give one or two low-risk actions for today. Make each action specific and realistic, cite the signals that motivate it, and say what to monitor next. Avoid generic filler, alarmist interpretations, and pretending that correlation proves a cause.

        This is general wellness guidance, not medical diagnosis or treatment. Do not prescribe medication or claim medical certainty. For urgent or severe symptoms, advise seeking appropriate local medical or emergency care.

        COACHING VOICE
        - Sound like a trusted coach in a real conversation: warm, natural, calm, and encouraging.
        - Speak directly to the user as "you" and use natural contractions.
        - Translate measurements into what they mean for the user's day instead of sounding like a data report.
        - Briefly celebrate genuine progress or acknowledge a concern, but only when the supplied context supports it.
        - Offer recommendations as supportive, practical choices rather than commands.
        - Never shame, lecture, exaggerate, or use generic praise.
        - Avoid canned openings such as "Great question" and robotic phrases such as "based on the provided data."

        RESPONSE STYLE
        - Lead with the main takeaway in one short sentence.
        - Use plain language and short sentences.
        - Default to no more than 120 words. Exceed this only for essential safety or accuracy.
        - Keep only facts that affect the conclusion, caveat, or next action.
        - When presenting three or more related facts, use a Markdown bullet list with one fact per line.
        - Bold only important numbers and recommended actions with **double asterisks**.
        - Do not repeat, add a long introduction, or explain every available metric.

        Provide a concise reasoning summary as two to four short steps explaining how the supplied signals support the answer. This is a user-facing summary, not private chain-of-thought.

        Also provide exactly three follow-up questions, each under 60 characters. Write them exactly as the user would send them, using first-person wording such as I, me, or my. Each must naturally continue this specific question and answer and be answerable only from the supplied health summary and conversation. Never suggest or ask about food, meals, drinks, or hydration because those details are not supplied. Do not put actions, monitoring instructions, schema labels, placeholders, or field names in the follow-up list.

        HEALTH SUMMARY
        \(health)

        \(previousExchange)

        CURRENT QUESTION
        \(message)
        """
        let codex = Self.codexPath()
        let schemaURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("simplified-fit-coach-\(UUID().uuidString).json")
        try Data(Self.outputSchema.utf8).write(to: schemaURL, options: .atomic)
        defer { try? FileManager.default.removeItem(at: schemaURL) }
        let process = Process()
        let input = Pipe()
        let output = Pipe()
        let error = Pipe()
        process.executableURL = URL(fileURLWithPath: codex)
        process.arguments = [
            "exec",
            "--ephemeral",
            "--model", "gpt-5.6-luna",
            "--config", "model_reasoning_effort=\"high\"",
            "--skip-git-repo-check",
            "--sandbox", "read-only",
            "--output-schema", schemaURL.path,
            "--color", "never",
            "-",
        ]
        process.standardInput = input
        process.standardOutput = output
        process.standardError = error
        try process.run()
        input.fileHandleForWriting.write(Data(prompt.utf8))
        try? input.fileHandleForWriting.close()
        process.waitUntilExit()
        let text = String(data: output.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if process.terminationStatus != 0 || text.isEmpty {
            let details = String(data: error.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? "Codex failed"
            throw NSError(domain: "SimplifiedFitCoach", code: Int(process.terminationStatus), userInfo: [NSLocalizedDescriptionKey: details])
        }
        do {
            return try JSONDecoder().decode(CoachAnswer.self, from: Data(text.utf8))
        } catch {
            throw NSError(
                domain: "SimplifiedFitCoach",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Coach returned an invalid structured response."]
            )
        }
    }

    private func send(_ connection: NWConnection, status: Int, json: [String: Any]) {
        let payload = (try? JSONSerialization.data(withJSONObject: json)) ?? Data("{}".utf8)
        let reason = status == 200 ? "OK" : "Error"
        let header = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: application/json\r\nContent-Length: \(payload.count)\r\nConnection: close\r\n\r\n"
        connection.send(content: Data(header.utf8) + payload, completion: .contentProcessed { _ in connection.cancel() })
    }

    private static func requestComplete(_ data: Data) -> Bool {
        let separator = Data("\r\n\r\n".utf8)
        guard let range = data.range(of: separator),
              let header = String(data: data[..<range.lowerBound], encoding: .utf8) else { return false }
        let length = header.split(separator: "\r\n").first { $0.lowercased().hasPrefix("content-length:") }
            .flatMap { Int($0.split(separator: ":", maxSplits: 1)[1].trimmingCharacters(in: .whitespaces)) } ?? 0
        return data.count >= range.upperBound + length
    }

    private static func codexPath() -> String {
        let candidates = [
            FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".local/bin/codex").path,
            "/opt/homebrew/bin/codex",
            "/usr/local/bin/codex",
        ]
        return candidates.first(where: FileManager.default.isExecutableFile(atPath:)) ?? "/usr/bin/false"
    }

    private static func tailscaleAddress() -> String? {
        let process = Process()
        let output = Pipe()
        let candidates = ["/usr/local/bin/tailscale", "/opt/homebrew/bin/tailscale", "/Applications/Tailscale.app/Contents/MacOS/Tailscale"]
        if let executable = candidates.first(where: FileManager.default.isExecutableFile(atPath:)) {
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = ["ip", "-4"]
        } else {
            process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
            process.arguments = ["tailscale", "ip", "-4"]
        }
        process.standardOutput = output
        process.standardError = FileHandle.nullDevice
        try? process.run()
        process.waitUntilExit()
        return String(data: output.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "\n").first.map(String.init)
    }

    private static func randomToken() -> String {
        let bytes = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }
        return bytes.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    private static let outputSchema = """
    {
      "type": "object",
      "properties": {
        "response": { "type": "string" },
        "reasoning": {
          "type": "array",
          "items": { "type": "string" },
          "minItems": 2,
          "maxItems": 4
        },
        "suggestions": {
          "type": "array",
          "items": { "type": "string" },
          "minItems": 3,
          "maxItems": 3
        }
      },
      "required": ["response", "reasoning", "suggestions"],
      "additionalProperties": false
    }
    """
}

private struct CoachAnswer: Codable {
    let response: String
    let reasoning: [String]
    let suggestions: [String]
}

private struct HTTPRequest {
    let method: String
    let path: String
    let headers: [String: String]
    let body: Data

    init?(data: Data) {
        guard let marker = data.range(of: Data("\r\n\r\n".utf8)),
              let head = String(data: data[..<marker.lowerBound], encoding: .utf8) else { return nil }
        let lines = head.components(separatedBy: "\r\n")
        let first = lines[0].split(separator: " ")
        guard first.count >= 2 else { return nil }
        method = String(first[0])
        path = String(first[1])
        headers = Dictionary(uniqueKeysWithValues: lines.dropFirst().compactMap { line in
            guard let split = line.firstIndex(of: ":") else { return nil }
            return (line[..<split].lowercased(), line[line.index(after: split)...].trimmingCharacters(in: .whitespaces))
        })
        body = data[marker.upperBound...]
    }
}
