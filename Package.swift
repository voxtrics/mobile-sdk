// swift-tools-version:5.9
import PackageDescription

// At the repository's root so Swift Package Manager can add the SDK by the repository's URL. The Android SDK
// (android/) lives in the same repository and is not part of this package.
let package = Package(
    name: "Voxtrics",
    // 16.4: the survey page is built for Safari 16.4 and later.
    platforms: [.iOS("16.4")],
    products: [
        .library(name: "Voxtrics", targets: ["Voxtrics"]),
    ],
    targets: [
        .target(
            name: "Voxtrics",
            path: "ios/Sources/Voxtrics",
            resources: [.copy("PrivacyInfo.xcprivacy")]
        ),
        .testTarget(
            name: "VoxtricsTests",
            dependencies: ["Voxtrics"],
            path: "ios/Tests/VoxtricsTests"
        ),
    ]
)
