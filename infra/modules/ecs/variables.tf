variable "name" { type = string }
variable "region" { type = string }
variable "app_subnet_ids" { type = list(string) }
variable "ecs_sg_id" {
  type        = string
  description = "ECS tasks security group (created in the env to avoid a cycle with RDS)"
}

variable "services" {
  description = "map: service name -> task + service settings"
  type = map(object({
    image            = string
    port             = number
    cpu              = number
    memory           = number
    desired_count    = number
    environment      = map(string)
    secrets          = map(string) # env name -> Secrets Manager valueFrom ARN
    target_group_arn = string
  }))
}

variable "jwt_secret_arn" { type = string }
variable "extra_secret_arns" {
  type        = list(string)
  description = "Additional secret ARNs the execution role may read (e.g. RDS master secret)"
  default     = []
}
variable "dynamodb_table_arn" { type = string }
variable "secrets_arn_prefix" {
  type        = string
  description = "ARN pattern the task role may read (per-connection HMAC secrets)"
}
variable "log_retention_days" {
  type    = number
  default = 14
}
variable "tags" {
  type    = map(string)
  default = {}
}
