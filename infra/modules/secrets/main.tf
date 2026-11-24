variable "name" { type = string }
variable "jwt_secret" {
  type      = string
  sensitive = true
}
variable "tags" {
  type    = map(string)
  default = {}
}

# JWT signing secret. Per-connection HMAC secrets are created at runtime by integration-service
# (one Secrets Manager entry per connection); only the reference is stored in RDS (docs/08 §5).
resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name}/jwt-secret"
  recovery_window_in_days = 0
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = var.jwt_secret
}

output "jwt_secret_arn" { value = aws_secretsmanager_secret.jwt.arn }
