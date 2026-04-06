# =============================================================================
# Development Environment
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # S3 Backend configuration (uncomment and configure for team use)
  # backend "s3" {
  #   bucket         = "your-project-terraform-state"
  #   key            = "dev/terraform.tfstate"
  #   region         = "ap-northeast-1"
  #   dynamodb_table = "terraform-state-lock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# -----------------------------------------------------------------------------
# Networking Module
# -----------------------------------------------------------------------------
module "networking" {
  source = "../../modules/networking"

  project_name       = var.project_name
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  az_count           = 2
  enable_nat_gateway = true # Required for ECS to pull images
  app_port           = 8080

  tags = var.tags
}

# -----------------------------------------------------------------------------
# Database Module
# -----------------------------------------------------------------------------
module "database" {
  source = "../../modules/database"

  project_name      = var.project_name
  environment       = var.environment
  subnet_ids        = module.networking.private_subnet_ids
  security_group_id = module.networking.db_security_group_id

  db_name     = var.db_name
  db_username = var.db_username
  db_password = var.db_password

  instance_class          = "db.t4g.micro"
  allocated_storage       = 20
  max_allocated_storage   = 50
  multi_az                = false
  backup_retention_period = 7

  tags = var.tags
}

# -----------------------------------------------------------------------------
# Compute Module
# -----------------------------------------------------------------------------
module "compute" {
  source = "../../modules/compute"

  project_name          = var.project_name
  environment           = var.environment
  aws_region            = var.aws_region
  vpc_id                = module.networking.vpc_id
  public_subnet_ids     = module.networking.public_subnet_ids
  private_subnet_ids    = module.networking.private_subnet_ids
  alb_security_group_id = module.networking.alb_security_group_id
  app_security_group_id = module.networking.app_security_group_id

  container_name  = "${var.project_name}-backend"
  container_image = var.container_image
  container_port  = 8080

  task_cpu    = 512
  task_memory = 1024

  desired_count      = 1
  enable_autoscaling = false

  environment_variables = {
    APP_ENV      = "development"
    SERVER_HOST  = "0.0.0.0"
    SERVER_PORT  = "8080"
    DATABASE_URL = module.database.jdbc_url
    JWT_ISSUER   = "${var.project_name}-backend"
    JWT_AUDIENCE = "${var.project_name}-client"
  }

  secrets = {
    DATABASE_USER     = var.db_credentials_secret_arn
    DATABASE_PASSWORD = var.db_credentials_secret_arn
    JWT_SECRET        = var.jwt_secret_arn
  }

  certificate_arn    = ""
  log_retention_days = 14

  tags = var.tags
}
