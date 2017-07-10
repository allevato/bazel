# The basic terminal-based Skylark debugger
# Open-sourced as part of Bazel.

package(default_visibility = ["//src:__subpackages__"])

java_binary(
    name = "debugger",
    srcs = glob(["*.java"]),
    main_class = "com.google.devtools.skylark.debugger.BasicDebugger",
    deps = [
        "//src/main/java/com/google/devtools/build/lib:events",
        "//src/main/java/com/google/devtools/build/lib:packages-internal",
        "//src/main/java/com/google/devtools/build/lib:skylarkinterface",
        "//src/main/java/com/google/devtools/build/lib/syntax/debugprotocol/protos:debug_protocol_java_proto",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:option_filters_java_proto",
        "//third_party:guava",
        "//third_party/protobuf/3.2.0:protobuf_java",
    ],
)

filegroup(
    name = "srcs",
    testonly = 0,  # All srcs should be not test only, overwrite package default.
    srcs = glob(["**"]),
)
