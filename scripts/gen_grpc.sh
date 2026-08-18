#!/usr/bin/env bash
# Regenerates the committed Java gRPC stubs (ragify.v1) from
# src/main/proto/ragify.proto. The stubs are checked in under
# src/main/java/ragify/v1 so the module is always present without a build step.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROTO="$ROOT/src/main/proto/ragify.proto"
OUT="$ROOT/src/main/java"
TOOLS="$ROOT/build/tools"

PROTOC_VERSION="3.25.5"
GRPC_JAVA_VERSION="1.68.1"

mkdir -p "$TOOLS"

if [ ! -x "$TOOLS/bin/protoc" ]; then
    curl -fsSL \
        "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-linux-x86_64.zip" \
        -o "$TOOLS/protoc.zip"
    unzip -o "$TOOLS/protoc.zip" -d "$TOOLS"
fi

if [ ! -x "$TOOLS/protoc-gen-grpc-java" ]; then
    curl -fsSL \
        "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/${GRPC_JAVA_VERSION}/protoc-gen-grpc-java-${GRPC_JAVA_VERSION}-linux-x86_64.exe" \
        -o "$TOOLS/protoc-gen-grpc-java"
    chmod +x "$TOOLS/protoc-gen-grpc-java"
fi

rm -rf "$OUT/ragify/v1"

"$TOOLS/bin/protoc" -I "$ROOT/src/main/proto" \
    --plugin=protoc-gen-grpc-java="$TOOLS/protoc-gen-grpc-java" \
    --java_out="$OUT" \
    --grpc-java_out="$OUT" \
    "$PROTO"

echo "Generated gRPC stubs in $OUT/ragify/v1"
