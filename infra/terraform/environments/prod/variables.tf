# =============================================================================
# Production Environment Variables
# =============================================================================

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "myapp"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.2.0.0/16"
}

# Database
variable "db_name" {
  description = "Name of the database"
  type        = string
  default     = "myapp"
}

variable "db_username" {
  description = "Master username for the database"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Master password for the database"
  type        = string
  sensitive   = true
}

variable "db_credentials_secret_arn" {
  description = "ARN of the secret containing database credentials"
  type        = string
}

# Application
variable "container_image" {
  description = "Docker image for the application"
  type        = string
}

variable "jwt_secret_arn" {
  description = "ARN of the secret containing JWT secret"
  type        = string
}

# SSL
variable "certificate_arn" {
  description = "ARN of ACM certificate for HTTPS"
  type        = string
}

# Tags
variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
