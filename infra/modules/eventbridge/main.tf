variable "name" { type = string }
variable "lambda_jar_path" {
  type        = string
  description = "Path to the normalizer Lambda deployment jar (built by Maven)"
  default     = "../../backend/lambda-normalizer/target/lambda-normalizer-0.0.1-SNAPSHOT.jar"
}
variable "lambda_handler" {
  type    = string
  default = "com.habitloop.normalizer.PartnerEventLambdaHandler::handleRequest"
}
variable "tags" {
  type    = map(string)
  default = {}
}

data "aws_caller_identity" "current" {}

# Custom event bus for all HabitLoop domain + integration events (PDF: EventBridge for event-driven
# automation). docs/06 §6, docs/07.
resource "aws_cloudwatch_event_bus" "this" {
  name = "${var.name}-bus"
  tags = var.tags
}

# Dead-letter queue for the Lambda target.
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.name}-partner-events-dlq"
  message_retention_seconds = 1209600
  tags                      = var.tags
}

# Shared DLQ for the internal consumer queues (redrive after repeated failures).
resource "aws_sqs_queue" "internal_dlq" {
  name                      = "${var.name}-internal-dlq"
  message_retention_seconds = 1209600
  tags                      = var.tags
}

# --- Consumer queues: EventBridge rules deliver events here; each service long-polls its own queue. ---
locals {
  consumer_queues = ["activity", "challenge", "analytics", "recommendation"]
}

resource "aws_sqs_queue" "consumer" {
  for_each                   = toset(local.consumer_queues)
  name                       = "${var.name}-${each.value}-events"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.internal_dlq.arn
    maxReceiveCount     = 5
  })
  tags = var.tags
}

# Allow EventBridge (same account) to send to each consumer queue.
resource "aws_sqs_queue_policy" "consumer" {
  for_each  = aws_sqs_queue.consumer
  queue_url = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = each.value.arn
      Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id } }
    }]
  })
}

# --- EventBridge rules → targets ---

# activity.logged → analytics + challenge
resource "aws_cloudwatch_event_rule" "activity_logged" {
  name           = "${var.name}-activity-logged"
  event_bus_name = aws_cloudwatch_event_bus.this.name
  event_pattern = jsonencode({
    source        = ["habitloop.activity"]
    "detail-type" = ["activity.logged"]
  })
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "activity_logged_analytics" {
  rule           = aws_cloudwatch_event_rule.activity_logged.name
  event_bus_name = aws_cloudwatch_event_bus.this.name
  target_id      = "analytics"
  arn            = aws_sqs_queue.consumer["analytics"].arn
}

resource "aws_cloudwatch_event_target" "activity_logged_challenge" {
  rule           = aws_cloudwatch_event_rule.activity_logged.name
  event_bus_name = aws_cloudwatch_event_bus.this.name
  target_id      = "challenge"
  arn            = aws_sqs_queue.consumer["challenge"].arn
}

# partner.activity.normalized (Lambda output) → activity
resource "aws_cloudwatch_event_rule" "partner_activity_normalized" {
  name           = "${var.name}-partner-activity-normalized"
  event_bus_name = aws_cloudwatch_event_bus.this.name
  event_pattern = jsonencode({
    source        = ["habitloop.normalizer"]
    "detail-type" = ["partner.activity.normalized"]
  })
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "partner_normalized_activity" {
  rule           = aws_cloudwatch_event_rule.partner_activity_normalized.name
  event_bus_name = aws_cloudwatch_event_bus.this.name
  target_id      = "activity"
  arn            = aws_sqs_queue.consumer["activity"].arn
}

# analytics.updated → recommendation
resource "aws_cloudwatch_event_rule" "analytics_updated" {
  name           = "${var.name}-analytics-updated"
  event_bus_name = aws_cloudwatch_event_bus.this.name
  event_pattern = jsonencode({
    source        = ["habitloop.analytics"]
    "detail-type" = ["analytics.updated"]
  })
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "analytics_updated_recommendation" {
  rule           = aws_cloudwatch_event_rule.analytics_updated.name
  event_bus_name = aws_cloudwatch_event_bus.this.name
  target_id      = "recommendation"
  arn            = aws_sqs_queue.consumer["recommendation"].arn
}

# --- Normalizer Lambda (triggered by partner.event.received) ---

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  name               = "${var.name}-normalizer-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
  tags               = var.tags
}

# Least-privilege: logs, publish the normalized event back onto the bus, and the DLQ.
resource "aws_iam_role_policy" "lambda" {
  name = "normalizer-permissions"
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect   = "Allow"
        Action   = ["events:PutEvents"]
        Resource = [aws_cloudwatch_event_bus.this.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = [aws_sqs_queue.dlq.arn]
      }
    ]
  })
}

resource "aws_lambda_function" "normalizer" {
  function_name = "${var.name}-partner-event-normalizer"
  role          = aws_iam_role.lambda.arn
  runtime       = "java17"
  handler       = var.lambda_handler
  filename      = var.lambda_jar_path
  memory_size   = 512
  timeout       = 30

  environment {
    variables = {
      EVENT_BUS_NAME = aws_cloudwatch_event_bus.this.name
    }
  }

  tags = var.tags
}

resource "aws_lambda_function_event_invoke_config" "normalizer" {
  function_name                = aws_lambda_function.normalizer.function_name
  maximum_event_age_in_seconds = 3600
  maximum_retry_attempts       = 2
  destination_config {
    on_failure {
      destination = aws_sqs_queue.dlq.arn
    }
  }
}

resource "aws_cloudwatch_event_rule" "partner_received" {
  name           = "${var.name}-partner-event-received"
  event_bus_name = aws_cloudwatch_event_bus.this.name
  event_pattern = jsonencode({
    source        = ["habitloop.integration"]
    "detail-type" = ["partner.event.received"]
  })
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "to_lambda" {
  rule           = aws_cloudwatch_event_rule.partner_received.name
  event_bus_name = aws_cloudwatch_event_bus.this.name
  arn            = aws_lambda_function.normalizer.arn

  retry_policy {
    maximum_retry_attempts       = 2
    maximum_event_age_in_seconds = 3600
  }
  dead_letter_config {
    arn = aws_sqs_queue.dlq.arn
  }
}

resource "aws_lambda_permission" "events" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.normalizer.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.partner_received.arn
}

# --- Outputs ---
output "bus_name" { value = aws_cloudwatch_event_bus.this.name }
output "bus_arn" { value = aws_cloudwatch_event_bus.this.arn }
output "lambda_name" { value = aws_lambda_function.normalizer.function_name }
output "dlq_name" { value = aws_sqs_queue.dlq.name }
output "internal_dlq_name" { value = aws_sqs_queue.internal_dlq.name }
output "queue_names" {
  value = { for k, q in aws_sqs_queue.consumer : k => q.name }
}
output "queue_urls" {
  value = { for k, q in aws_sqs_queue.consumer : k => q.id }
}
output "queue_arns" {
  value = [for q in aws_sqs_queue.consumer : q.arn]
}
