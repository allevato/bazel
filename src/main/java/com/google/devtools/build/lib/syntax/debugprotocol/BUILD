package(default_visibility = ["//src:__subpackages__"])

load("//tools/build_rules:utilities.bzl", "java_library_srcs")

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)

java_library_srcs(
    name = "dist_jars",
    deps = [":debug_protocol_java_proto"],
)

java_library(
    name = "debugprotocol",
    srcs = glob(["*.java"]),
    deps = [":debug_protocol_java_proto"],
)

java_proto_library(
    name = "debug_protocol_java_proto",
    deps = ["debug_protocol_proto"],
)

proto_library(
    name = "debug_protocol_proto",
    srcs = ["debug_protocol.proto"],
)
