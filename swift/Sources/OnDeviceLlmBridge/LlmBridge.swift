import Foundation
import FoundationModels

@objc public class OnDeviceLlmBridge: NSObject {
    private var session: LanguageModelSession?

    // The in-flight generation, retained so cancel()/close() can stop it. Foundation
    // Models honors Task cancellation (respond/streamResponse throw CancellationError),
    // so cancelling frees the device instead of running an abandoned generation to
    // completion. Manual completion handlers (rather than `async throws`) are used so we
    // own this Task and can cancel it from outside the Kotlin coroutine.
    private var task: Task<Void, Never>?

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
        maxTokens: Int32,
        completion: @escaping (String?, Error?) -> Void
    ) {
        guard let session else {
            completion(nil, Self.notInitialized)
            return
        }
        let options = Self.options(temperature: temperature, maxTokens: maxTokens)
        task = Task {
            do {
                let response = try await session.respond(to: prompt, options: options)
                completion(response.content, nil)
            } catch {
                completion(nil, error)
            }
        }
    }

    @objc public func streamGenerate(
        _ prompt: String,
        temperature: Double,
        maxTokens: Int32,
        onPartial: @escaping (String) -> Void,
        completion: @escaping (Error?) -> Void
    ) {
        guard let session else {
            completion(Self.notInitialized)
            return
        }
        let options = Self.options(temperature: temperature, maxTokens: maxTokens)
        task = Task {
            do {
                let stream = session.streamResponse(to: prompt, options: options)
                for try await partial in stream {
                    onPartial(partial.content)
                }
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    // Cancels the in-flight generation. Foundation Models stops at the next token
    // boundary and the pending generate()/streamGenerate() completes with a
    // CancellationError, which the Kotlin side discards on an already-cancelled call.
    @objc public func cancel() {
        task?.cancel()
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

    private static var notInitialized: NSError {
        NSError(
            domain: "dev.ynagai.ondevice",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Session not initialized"]
        )
    }

    @objc public func close() {
        task?.cancel()
        task = nil
        session = nil
    }
}
