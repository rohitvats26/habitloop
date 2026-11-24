variable "name" { type = string }
variable "table_name" {
  type    = string
  default = "habitloop-analytics"
}
variable "tags" {
  type    = map(string)
  default = {}
}

# Single-table analytics store with GSI1 for cohort queries (docs/04 §5).
resource "aws_dynamodb_table" "analytics" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
  attribute {
    name = "gsi1pk"
    type = "S"
  }
  attribute {
    name = "gsi1sk"
    type = "S"
  }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "gsi1pk"
    range_key       = "gsi1sk"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = merge(var.tags, { Name = var.table_name })
}

output "table_name" { value = aws_dynamodb_table.analytics.name }
output "table_arn" { value = aws_dynamodb_table.analytics.arn }
