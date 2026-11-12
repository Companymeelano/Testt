// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MeelanoPreinvoice",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "MeelanoPreinvoice", targets: ["MeelanoPreinvoice"]),
    ],
    targets: [
        .target(name: "MeelanoPreinvoice", path: "Sources/MeelanoPreinvoice"),
        .target(name: "MeelanoApp", dependencies: ["MeelanoPreinvoice"], path: "Sources/MeelanoApp"),
        .testTarget(name: "MeelanoPreinvoiceTests", dependencies: ["MeelanoPreinvoice"], path: "Tests"),
    ]
)
