// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SimplifiedFitCompanion",
    platforms: [.macOS(.v14)],
    products: [.executable(name: "SimplifiedFitCompanion", targets: ["SimplifiedFitCompanion"])],
    targets: [.executableTarget(name: "SimplifiedFitCompanion")]
)
