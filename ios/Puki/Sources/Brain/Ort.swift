import Foundation
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings

/** ONNX Runtime, as the Android app uses it (ai.onnxruntime), for Puki's models. */
enum Ort {
    static let env: ORTEnv? = try? ORTEnv(loggingLevel: .warning)

    struct Failure: Error { let what: String }

    static func session(_ url: URL, threads: Int, optimize: Bool = true) throws -> ORTSession {
        guard let env else { throw Failure(what: "no ONNX Runtime") }
        let opts = try ORTSessionOptions()
        try opts.setIntraOpNumThreads(Int32(threads))
        try opts.setGraphOptimizationLevel(optimize ? .all : .none)
        return try ORTSession(env: env, modelPath: url.path, sessionOptions: opts)
    }

    static func floats(_ data: [Float], _ shape: [Int]) throws -> ORTValue {
        // (never an empty buffer: a tensor with a 0 dimension still wants a pointer)
        let d = data.isEmpty ? NSMutableData(length: 4)! : data.withUnsafeBufferPointer { NSMutableData(bytes: $0.baseAddress!, length: $0.count * 4) }
        return try ORTValue(tensorData: d, elementType: .float, shape: shape.map { NSNumber(value: $0) })
    }

    static func int64s(_ data: [Int64], _ shape: [Int]) throws -> ORTValue {
        let d = data.isEmpty ? NSMutableData(length: 8)! : data.withUnsafeBufferPointer { NSMutableData(bytes: $0.baseAddress!, length: $0.count * 8) }
        return try ORTValue(tensorData: d, elementType: .int64, shape: shape.map { NSNumber(value: $0) })
    }

    static func floatsOf(_ v: ORTValue) throws -> [Float] {
        let d = try v.tensorData() as Data
        return d.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
    }

    static func shapeOf(_ v: ORTValue) throws -> [Int] {
        try v.tensorTypeAndShapeInfo().shape.map { $0.intValue }
    }

    /** Runs `session` for all its outputs. */
    static func run(_ session: ORTSession, _ inputs: [String: ORTValue]) throws -> [String: ORTValue] {
        let names = Set(try session.outputNames())
        return try session.run(withInputs: inputs, outputNames: names, runOptions: nil)
    }

    /** The first output (by the model's order). */
    static func first(_ session: ORTSession, _ out: [String: ORTValue]) throws -> ORTValue {
        guard let name = try session.outputNames().first, let v = out[name] else { throw Failure(what: "no output") }
        return v
    }
}
#endif
