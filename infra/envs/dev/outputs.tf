output "alb_dns_name" {
  description = "Public entry point (API + frontend)"
  value       = module.alb.alb_dns_name
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
