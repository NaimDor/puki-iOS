import Photos
import os

/**
 * Saves a drawing (a PNG) where the family finds it: the photo library. Only add access is asked for (the system asks
 * the parent once); Puki never reads the photos. Nothing leaves the device.
 */
func saveDrawing(_ png: Data) {
    PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
        guard status == .authorized || status == .limited else { return }
        PHPhotoLibrary.shared().performChanges({
            PHAssetCreationRequest.forAsset().addResource(with: .photo, data: png, options: nil)
        }) { _, error in
            if let error { Logger(subsystem: "puki", category: "canvas").warning("couldn't save the drawing: \(String(describing: type(of: error)), privacy: .public)") }
        }
    }
}
