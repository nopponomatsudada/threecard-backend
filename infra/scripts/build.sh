#!/bin/bash
# =============================================================================
# Docker Build Script
# Builds the application Docker image
# =============================================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DOCKERFILE="${PROJECT_ROOT}/infra/docker/Dockerfile"

# Default values (uses PROJECT_NAME env var if set)
IMAGE_NAME="${IMAGE_NAME:-${PROJECT_NAME:-app-master}-backend}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo 'dev')}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -n, --name NAME     Image name (default: \${PROJECT_NAME}-backend or app-master-backend)"
    echo "  -t, --tag TAG       Image tag (default: git short SHA or 'dev')"
    echo "  -p, --push          Push to registry after build"
    echo "  -r, --registry URL  Registry URL for push"
    echo "  --no-cache          Build without cache"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  PROJECT_NAME        Project name used for image naming"
    echo "  IMAGE_NAME          Override full image name"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Build with defaults"
    echo "  PROJECT_NAME=app-master $0             # Build as app-master-backend"
    echo "  $0 -t latest                          # Build with 'latest' tag"
    echo "  $0 -p -r 123456789.dkr.ecr.ap-northeast-1.amazonaws.com"
}

# Parse arguments
PUSH=false
NO_CACHE=""
REGISTRY=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--name)
            IMAGE_NAME="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -p|--push)
            PUSH=true
            shift
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main
print_info "Building Docker image..."
print_info "  Image: ${IMAGE_NAME}:${IMAGE_TAG}"
print_info "  Context: ${PROJECT_ROOT}"
print_info "  Dockerfile: ${DOCKERFILE}"

cd "${PROJECT_ROOT}"

# Build
docker build \
    ${NO_CACHE} \
    -f "${DOCKERFILE}" \
    -t "${IMAGE_NAME}:${IMAGE_TAG}" \
    -t "${IMAGE_NAME}:latest" \
    .

print_info "Build completed successfully!"

# Push if requested
if [ "$PUSH" = true ]; then
    if [ -z "$REGISTRY" ]; then
        print_error "Registry URL required for push. Use -r option."
        exit 1
    fi

    FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
    FULL_IMAGE_LATEST="${REGISTRY}/${IMAGE_NAME}:latest"

    print_info "Tagging and pushing to ${REGISTRY}..."

    docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${FULL_IMAGE}"
    docker tag "${IMAGE_NAME}:latest" "${FULL_IMAGE_LATEST}"

    docker push "${FULL_IMAGE}"
    docker push "${FULL_IMAGE_LATEST}"

    print_info "Push completed: ${FULL_IMAGE}"
fi

# Summary
echo ""
echo "============================================"
echo "Build Summary"
echo "============================================"
echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo "Size:  $(docker images ${IMAGE_NAME}:${IMAGE_TAG} --format '{{.Size}}')"
echo ""
echo "Run locally:"
echo "  docker run -p 8080:8080 ${IMAGE_NAME}:${IMAGE_TAG}"
echo ""
