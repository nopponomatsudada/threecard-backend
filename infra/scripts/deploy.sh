#!/bin/bash
# =============================================================================
# Deploy Script
# Deploys the application to AWS ECS
# =============================================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Default values
AWS_REGION="${AWS_REGION:-ap-northeast-1}"
PROJECT_NAME="${PROJECT_NAME:-app-master}"
ECR_REPOSITORY="${ECR_REPOSITORY:-${PROJECT_NAME}-backend}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

usage() {
    echo "Usage: $0 <environment> [OPTIONS]"
    echo ""
    echo "Arguments:"
    echo "  environment         Target environment (dev, staging, prod)"
    echo ""
    echo "Options:"
    echo "  -t, --tag TAG       Image tag to deploy (default: git short SHA)"
    echo "  --skip-build        Skip Docker build step"
    echo "  --skip-push         Skip ECR push step"
    echo "  --dry-run           Show what would be done without executing"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  PROJECT_NAME        Project name (default: app-master)"
    echo "  AWS_REGION          AWS region (default: ap-northeast-1)"
    echo "  ECR_REPOSITORY      ECR repository name (default: \${PROJECT_NAME}-backend)"
    echo ""
    echo "Examples:"
    echo "  $0 dev                              # Deploy to dev"
    echo "  PROJECT_NAME=app-master $0 staging   # Deploy app-master to staging"
    echo "  $0 staging -t v1.0.0                # Deploy specific tag to staging"
    echo "  $0 prod --dry-run                   # Preview prod deployment"
}

check_prerequisites() {
    print_step "Checking prerequisites..."

    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI is not installed"
        exit 1
    fi

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi

    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured"
        exit 1
    fi

    print_info "Prerequisites OK"
}

get_ecr_registry() {
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    echo "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
}

login_ecr() {
    print_step "Logging in to ECR..."
    aws ecr get-login-password --region "${AWS_REGION}" | \
        docker login --username AWS --password-stdin "$(get_ecr_registry)"
}

build_image() {
    print_step "Building Docker image..."
    "${SCRIPT_DIR}/build.sh" -t "${IMAGE_TAG}"
}

push_image() {
    print_step "Pushing image to ECR..."
    local registry=$(get_ecr_registry)
    local local_image="${PROJECT_NAME}-backend:${IMAGE_TAG}"
    local full_image="${registry}/${ECR_REPOSITORY}:${IMAGE_TAG}"
    local env_image="${registry}/${ECR_REPOSITORY}:${ENVIRONMENT}-latest"

    docker tag "${local_image}" "${full_image}"
    docker tag "${local_image}" "${env_image}"

    docker push "${full_image}"
    docker push "${env_image}"

    print_info "Pushed: ${full_image}"
}

update_ecs_service() {
    print_step "Updating ECS service..."

    local cluster="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    local service="${PROJECT_NAME}-${ENVIRONMENT}-service"
    local task_family="${PROJECT_NAME}-${ENVIRONMENT}"

    # Get current task definition
    local current_task_def=$(aws ecs describe-services \
        --cluster "${cluster}" \
        --services "${service}" \
        --query 'services[0].taskDefinition' \
        --output text)

    print_info "Current task definition: ${current_task_def}"

    # Get task definition details
    local task_def_json=$(aws ecs describe-task-definition \
        --task-definition "${current_task_def}" \
        --query 'taskDefinition')

    # Update container image
    local registry=$(get_ecr_registry)
    local new_image="${registry}/${ECR_REPOSITORY}:${IMAGE_TAG}"

    local new_task_def=$(echo "${task_def_json}" | \
        jq --arg IMAGE "${new_image}" \
        '.containerDefinitions[0].image = $IMAGE |
         del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy)')

    # Register new task definition
    local new_task_def_arn=$(aws ecs register-task-definition \
        --cli-input-json "${new_task_def}" \
        --query 'taskDefinition.taskDefinitionArn' \
        --output text)

    print_info "New task definition: ${new_task_def_arn}"

    # Update service
    aws ecs update-service \
        --cluster "${cluster}" \
        --service "${service}" \
        --task-definition "${new_task_def_arn}" \
        --force-new-deployment \
        > /dev/null

    print_info "Service update initiated"
}

wait_for_deployment() {
    print_step "Waiting for deployment to complete..."

    local cluster="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    local service="${PROJECT_NAME}-${ENVIRONMENT}-service"

    aws ecs wait services-stable \
        --cluster "${cluster}" \
        --services "${service}"

    print_info "Deployment completed successfully!"
}

# Parse arguments
if [ $# -lt 1 ]; then
    print_error "Environment is required"
    usage
    exit 1
fi

ENVIRONMENT="$1"
shift

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    print_error "Invalid environment: ${ENVIRONMENT}"
    print_error "Must be one of: dev, staging, prod"
    exit 1
fi

IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo 'latest')}"
SKIP_BUILD=false
SKIP_PUSH=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-push)
            SKIP_PUSH=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
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
echo "============================================"
echo "Deployment Configuration"
echo "============================================"
echo "Environment: ${ENVIRONMENT}"
echo "Image Tag:   ${IMAGE_TAG}"
echo "Region:      ${AWS_REGION}"
echo "Repository:  ${ECR_REPOSITORY}"
echo ""

if [ "$DRY_RUN" = true ]; then
    print_warn "DRY RUN - No changes will be made"
    echo ""
    echo "Would execute:"
    [ "$SKIP_BUILD" = false ] && echo "  1. Build Docker image"
    echo "  2. Login to ECR"
    [ "$SKIP_PUSH" = false ] && echo "  3. Push image to ECR"
    echo "  4. Update ECS service"
    echo "  5. Wait for deployment"
    exit 0
fi

# Production confirmation
if [ "$ENVIRONMENT" = "prod" ]; then
    print_warn "You are about to deploy to PRODUCTION!"
    read -p "Are you sure? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        print_info "Deployment cancelled"
        exit 0
    fi
fi

# Execute deployment
check_prerequisites

if [ "$SKIP_BUILD" = false ]; then
    build_image
fi

login_ecr

if [ "$SKIP_PUSH" = false ]; then
    push_image
fi

update_ecs_service
wait_for_deployment

echo ""
echo "============================================"
echo "Deployment Summary"
echo "============================================"
echo "Environment: ${ENVIRONMENT}"
echo "Image Tag:   ${IMAGE_TAG}"
echo "Status:      SUCCESS"
echo ""

# Get ALB DNS
ALB_DNS=$(aws elbv2 describe-load-balancers \
    --names "${PROJECT_NAME}-${ENVIRONMENT}-alb" \
    --query 'LoadBalancers[0].DNSName' \
    --output text 2>/dev/null || echo "N/A")

if [ "$ALB_DNS" != "N/A" ]; then
    echo "Application URL: http://${ALB_DNS}"
fi
