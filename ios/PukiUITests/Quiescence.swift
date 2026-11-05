import XCTest
import ObjectiveC

/**
 * XCUITest waits for the app to sit still before each touch. Puki never sits still for long (a glowing hint
 * pulses until it's tapped, Puki bobs in the corner), so on a slow simulator every touch waited many seconds and
 * landed late, and a round with a glowing answer could hang the run for hours. The tests pause on their own
 * instead (Driver.pause), so the wait is switched off.
 */
enum Quiescence {
    static let off: Void = {
        guard let cls = NSClassFromString("XCUIApplicationProcess") else {
            print("note: XCUIApplicationProcess not found; touches still wait for the app to settle")
            return
        }
        let one: @convention(block) (AnyObject, Bool) -> Void = { _, _ in }
        let two: @convention(block) (AnyObject, Bool, Bool) -> Void = { _, _, _ in }
        let swaps: [(String, IMP)] = [
            ("waitForQuiescenceIncludingAnimationsIdle:", imp_implementationWithBlock(one)),
            ("waitForQuiescenceIncludingAnimationsIdle:isPreEvent:", imp_implementationWithBlock(two)),
        ]
        var done = 0
        for (name, imp) in swaps {
            if let m = class_getInstanceMethod(cls, NSSelectorFromString(name)) { method_setImplementation(m, imp); done += 1 }
        }
        print("note: touches don't wait for the app to settle (\(done) of \(swaps.count) waits switched off)")
    }()
}
