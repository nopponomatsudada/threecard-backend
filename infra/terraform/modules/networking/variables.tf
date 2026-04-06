# =============================================================================
# Networking Module Variables
# =============================================================================

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to use"
  type        = number
  default     = 2
}

variable "enable_nat_gateway" {
  description = "Whether to create NAT Gateway for private subnet outbound access"
  type        = bool
  default     = true
}

variable "app_port" {
  description = "Port the application listens on"
  type        = number
  default     = 8080
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
