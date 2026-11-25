variable "name" { type = string }
variable "vpc_id" { type = string }
variable "public_subnet_ids" { type = list(string) }
variable "services" {
  description = "map: service name -> { port, path_patterns, priority }"
  type = map(object({
    port              = number
    path_patterns     = list(string)
    priority          = number
    health_check_path = string
  }))
}
variable "certificate_arn" {
  type        = string
  default     = ""
  description = "ACM certificate ARN. When set, an HTTPS:443 listener is created and HTTP:80 redirects to it (PDF: HTTPS/TLS). Requires a domain + ACM cert."
}
variable "tags" {
  type    = map(string)
  default = {}
}

locals {
  https_enabled = var.certificate_arn != ""
  target_group_names = {
    for service_name in keys(var.services) : service_name =>
    length("${var.name}-${service_name}") > 32
    ? "${var.name}-${trimsuffix(service_name, "-service")}"
    : "${var.name}-${service_name}"
  }
}

resource "aws_security_group" "alb" {
  name_prefix = "${var.name}-alb-"
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name}-alb-sg" })

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb" "this" {
  name               = "${var.name}-alb"
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
  tags               = var.tags
}

resource "aws_lb_target_group" "svc" {
  for_each    = var.services
  name        = local.target_group_names[each.key]
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = each.value.health_check_path
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = merge(var.tags, { Name = "${var.name}-${each.key}-tg" })
}

# HTTP listener. When a certificate is provided it redirects to HTTPS; otherwise it serves the rules.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = local.https_enabled ? [1] : []
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
  dynamic "default_action" {
    for_each = local.https_enabled ? [] : [1]
    content {
      type = "fixed-response"
      fixed_response {
        content_type = "text/plain"
        message_body = "Not Found"
        status_code  = "404"
      }
    }
  }
}

# HTTPS listener (only when an ACM cert is supplied). PDF: "HTTPS/TLS encryption".
resource "aws_lb_listener" "https" {
  count             = local.https_enabled ? 1 : 0
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
}

locals {
  svc_listener_arn = local.https_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
}

resource "aws_lb_listener_rule" "svc" {
  for_each     = var.services
  listener_arn = local.svc_listener_arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.svc[each.key].arn
  }

  condition {
    path_pattern {
      values = each.value.path_patterns
    }
  }
}

output "alb_sg_id" { value = aws_security_group.alb.id }
output "alb_dns_name" { value = aws_lb.this.dns_name }
output "alb_arn_suffix" { value = aws_lb.this.arn_suffix }
output "target_group_arns" {
  value = { for k, tg in aws_lb_target_group.svc : k => tg.arn }
}
