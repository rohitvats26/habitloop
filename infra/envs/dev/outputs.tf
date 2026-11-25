output "alb_dns_name" {
  description = "Public entry point for the backend API (path-routed to services)"
  value       = module.alb.alb_dns_name
}

output "frontend_bucket" {
  description = "S3 bucket holding the static frontend build"
  value       = module.s3_frontend.bucket_name
}

output "frontend_url" {
  description = "CloudFront HTTPS URL for the static frontend"
  value       = "https://${module.s3_frontend.distribution_domain_name}"
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "dynamodb_table" {
  value = module.dynamodb.table_name
}

output "event_bus" {
  value = module.eventbridge.bus_name
}

output "normalizer_lambda" {
  value = module.eventbridge.lambda_name
}

output "alarms_topic_arn" {
  value = module.observability.alarms_topic_arn
}
