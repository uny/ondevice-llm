import Foundation
import FoundationModels

@objc public class OnDeviceLlmBridge: NSObject {
    private var session: LanguageModelSession?

    @objc public static func isAvailable() -> Bool {
        SystemLanguageModel.default.isAvailable
    }

    @objc public func createSession(_ instructions: String?) {
        if let instructions {
            session = LanguageModelSession {
                Instructions(instructions)
            }
        } else {
            session = LanguageModelSession()
        }
    }

    @objc public func generate(
        _ prompt: String,
        temperature: Double,
        maxTokens: Int32
    ) async throws -> String {
        guard let session else {
            throw NSError(
                domain: "dev.ynagai.ondevice",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Session not initialized"]
            )
        }
        let response = try await session.respond(
            to: prompt,
            options: Self.options(temperature: temperature, maxTokens: maxTokens)
        )
        return response.content
    }

    @objc public func streamGenerate(
        _ prompt: String,
        temperature: Double,
        maxTokens: Int32,
        callback: @escaping (String) -> Void
    ) async throws {
        guard let session else {
            throw NSError(
                domain: "dev.ynagai.ondevice",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Session not initialized"]
            )
        }
        let stream = session.streamResponse(
            to: prompt,
            options: Self.options(temperature: temperature, maxTokens: maxTokens)
        )
        for try await partial in stream {
            callback(partial.content)
        }
    }

    // Kotlin cannot pass optional primitives across the @objc boundary, so a
    // negative temperature / non-positive maxTokens encodes "unset" → use the
    // Foundation Models default for that field.
    private static func options(temperature: Double, maxTokens: Int32) -> GenerationOptions {
        GenerationOptions(
            temperature: temperature >= 0 ? temperature : nil,
            maximumResponseTokens: maxTokens > 0 ? Int(maxTokens) : nil
        )
    }

    @objc public func close() {
        session = nil
    }
}
