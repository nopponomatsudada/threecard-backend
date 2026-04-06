# =============================================================================
# Staging Environment Outputs
# =============================================================================

# VPC
output "vpc_id" {
  description = "ID of the VPC"
  value       = module.networking.vpc_id
}

output "public_subnet_ids" {
  description = "IDs of public subnets"
  value       = module.networking.public_subnet_ids
}

output "private_subnet_ids" {
  description = "IDs of private subnets"
  value       = module.networking.private_subnet_ids
}

# Database
output "db_endpoint" {
  description = "Database endpoint"
  value       = module.database.db_endpoint
}

output "db_jdbc_url" {
  description = "JDBC URL for the database"
  value       = module.database.jdbc_url
}

# ECS
output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = module.compute.cluster_name
}

output "ecs_service_name" {
  description = "Name of the ECS service"
  value       = module.compute.service_name
}

# ALB
output "alb_dns_name" {
  description = "DNS name of the ALB"
  value       = module.compute.alb_dns_name
}

output "app_url" {
  description = "URL of the application"
  value       = module.compute.app_url
}

# Logs
output "log_group_name" {
  description = "Name of the CloudWatch log group"
  value       = module.compute.log_group_name
}
