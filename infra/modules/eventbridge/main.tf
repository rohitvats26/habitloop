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

# Custom event bus for partner integration events (PDF: "EventBridge and Lambda for event-driven
# automation such as integration event processing"). docs/06 §6.
resource "aws_cloudwatch_event_bus" "this" {
  name = "${var.name}-bus"
  tags = var.tags
}

# Dead-letter queue for events the Lambda could not process (PDF CloudWatch alert: "queue depth";
# trade-offs: "requires idempotency and retry handling").
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.name}-partner-events-dlq"
  message_retention_seconds = 1209600
  tags                      = var.tags
}

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

# Least-privilege: logs + publish the normalized event back onto the same EventBridge bus
# (PDF step 5: "valid events are stored as activity logs" — consumed by activity-service). No SNS.
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

output "bus_name" { value = aws_cloudwatch_event_bus.this.name }
output "bus_arn" { value = aws_cloudwatch_event_bus.this.arn }
output "lambda_name" { value = aws_lambda_function.normalizer.function_name }
output "dlq_name" { value = aws_sqs_queue.dlq.name }
