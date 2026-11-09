// swift-tools-version: 5.9
import PackageDescription

// Foundation-only contracts and persistence. Does not compile the iOS app on Linux.
let package = Package(
    name: "TradutorCore",
    platforms: [.iOS("18.0"), .macOS(.v13)],
    products: [.library(name: "TradutorCore", targets: ["TradutorCore"])],
    targets: [
        .target(name: "TradutorCore", path: "Sources", exclude: ["App", "Features", "Services", "Domain/ServiceContracts.swift"],
                sources: ["Domain", "Persistence"]),
        .testTarget(name: "TradutorCoreTests", dependencies: ["TradutorCore"], path: "Tests/Unit")
    ]
)
