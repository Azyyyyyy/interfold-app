#!/usr/bin/env sh
# Build a local Interfold WASM Docker image tagged :dev.
#
# Compiles :webApp:wasmJsBrowserDistribution via Dockerfile.wasm and loads
# the result into Docker Desktop as interfold-wasm:dev so local builds are
# easy to tell apart from GHCR :latest / :sha-* images.
#
# Native platform only (no multi-arch). First run is a full Gradle/Wasm
# compile inside the builder stage and can take several minutes; later
# runs reuse Docker BuildKit cache mounts.
#
# Usage:
#   ./scripts/build-wasm-dev.sh
#   ./scripts/build-wasm-dev.sh --run

set -eu

IMAGE="interfold-wasm:dev"
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
RUN_AFTER=0

for arg in "$@"; do
  case "$arg" in
    --run|-r) RUN_AFTER=1 ;;
    --help|-h)
      echo "Usage: $0 [--run]"
      echo "  --run    Start the image on http://localhost:8080 after the build"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: $0 [--run]" >&2
      exit 1
      ;;
  esac
done

cd "$REPO_ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not on PATH. Start Docker Desktop and try again." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not reachable. Start Docker Desktop and try again." >&2
  exit 1
fi

if [ ! -f Dockerfile.wasm ]; then
  echo "Dockerfile.wasm not found in $REPO_ROOT" >&2
  exit 1
fi

echo "Building $IMAGE from Dockerfile.wasm (native platform)..."
START=$(date +%s)

export DOCKER_BUILDKIT=1
docker build -f Dockerfile.wasm -t "$IMAGE" .

END=$(date +%s)
ELAPSED=$((END - START))
MINUTES=$((ELAPSED / 60))
SECONDS=$((ELAPSED % 60))

echo
printf "Built %s in %02d:%02d.\n" "$IMAGE" "$MINUTES" "$SECONDS"
echo "Run with:  docker run --rm -p 8080:8080 $IMAGE"

if [ "$RUN_AFTER" -eq 1 ]; then
  echo "Starting $IMAGE on http://localhost:8080 ..."
  docker run --rm -p 8080:8080 "$IMAGE"
fi
