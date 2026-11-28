data "aws_caller_identity" "current" {}

locals {
  # Backend microservices only. The frontend is hosted on S3 (static), not ECS (PDF Session 3).
  svc_names = [
    "user-service", "activity-service", "challenge-service",
    "integration-service", "analytics-service", "recommendation-service"
  ]
  images         = { for s in local.svc_names : s => "${module.ecr.repository_urls[s]}:${var.image_tag}" }
  rds_host       = module.rds.endpoint
  db_pwd_ref     = "${module.rds.master_secret_arn}:password::"
  alb_dns        = "http://${module.alb.alb_dns_name}"
  secrets_prefix = "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.name}/*"

  # ALB rules route the same API prefixes that the local frontend calls on service ports.
  alb_services = {
    "user-service"           = { port = 8081, priority = 10, health_check_path = "/actuator/health", path_patterns = ["/api/auth/*", "/api/users/*", "/api/tenants", "/api/tenants/*"] }
    "activity-service"       = { port = 8082, priority = 20, health_check_path = "/actuator/health", path_patterns = ["/api/habits", "/api/habits/*", "/api/activity", "/api/activity/*"] }
    "challenge-service"      = { port = 8083, priority = 30, health_check_path = "/actuator/health", path_patterns = ["/api/challenges", "/api/challenges/*", "/api/invitations", "/api/invitations/*"] }
    "integration-service"    = { port = 8084, priority = 40, health_check_path = "/actuator/health", path_patterns = ["/api/integrations/*", "/api/webhooks", "/api/webhooks/*"] }
    "analytics-service"      = { port = 8085, priority = 50, health_check_path = "/actuator/health", path_patterns = ["/api/analytics/*"] }
    "recommendation-service" = { port = 8086, priority = 60, health_check_path = "/actuator/health", path_patterns = ["/api/recommendations", "/api/recommendations/*"] }
  }

  common_backend_env = {
    EVENTS_TRANSPORT = "eventbridge" # EventBridge + SQS transport on AWS (kafka is for local Compose)
    EVENT_BUS_NAME   = module.eventbridge.bus_name
  }

  ecs_services = {
    "user-service" = {
      image            = local.images["user-service"], port = 8081, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { USER_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_user", USER_DB_USER = "habitloop", SEED_DEMO = "false" })
      secrets          = { USER_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["user-service"]
    }
    "activity-service" = {
      image            = local.images["activity-service"], port = 8082, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { ACTIVITY_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_activity", ACTIVITY_DB_USER = "habitloop", EVENT_QUEUE_URL = module.eventbridge.queue_urls["activity"] })
      secrets          = { ACTIVITY_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["activity-service"]
    }
    "challenge-service" = {
      image            = local.images["challenge-service"], port = 8083, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { CHALLENGE_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_challenge", CHALLENGE_DB_USER = "habitloop", USER_SERVICE_URL = local.alb_dns, EVENT_QUEUE_URL = module.eventbridge.queue_urls["challenge"] })
      secrets          = { CHALLENGE_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["challenge-service"]
    }
    "integration-service" = {
      image            = local.images["integration-service"], port = 8084, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { INTEGRATION_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_integration", INTEGRATION_DB_USER = "habitloop", WEBHOOK_BASE_URL = local.alb_dns, AWS_REGION = var.region, SECRET_STORE_BACKEND = "aws", SECRET_STORE_PREFIX = var.name })
      secrets          = { INTEGRATION_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["integration-service"]
    }
    "analytics-service" = {
      image            = local.images["analytics-service"], port = 8085, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { ANALYTICS_STORE = "dynamodb", ANALYTICS_TABLE = module.dynamodb.table_name, AWS_REGION = var.region, EVENT_QUEUE_URL = module.eventbridge.queue_urls["analytics"] })
      secrets          = {}
      target_group_arn = module.alb.target_group_arns["analytics-service"]
    }
    "recommendation-service" = {
      image            = local.images["recommendation-service"], port = 8086, cpu = 256, memory = 512, desired_count = 1
      environment      = merge(local.common_backend_env, { ANALYTICS_SERVICE_URL = local.alb_dns, EVENT_QUEUE_URL = module.eventbridge.queue_urls["recommendation"] })
      secrets          = {}
      target_group_arn = module.alb.target_group_arns["recommendation-service"]
    }
  }
}

module "network" {
  source = "../../modules/network"
  name   = var.name
  azs    = var.azs
}

module "secrets" {
  source     = "../../modules/secrets"
  name       = var.name
  jwt_secret = var.jwt_secret
}

module "dynamodb" {
  source = "../../modules/dynamodb"
  name   = var.name
}

module "ecr" {
  source       = "../../modules/ecr"
  name         = var.name
  repositories = local.svc_names
}

module "alb" {
  source            = "../../modules/alb"
  name              = var.name
  vpc_id            = module.network.vpc_id
  public_subnet_ids = module.network.public_subnet_ids
  services          = local.alb_services
  certificate_arn   = var.certificate_arn
}

# Frontend: static SvelteKit build hosted on S3 (PDF Session 3).
module "s3_frontend" {
  source                     = "../../modules/s3"
  name                       = "${var.name}-${data.aws_caller_identity.current.account_id}"
  api_origin_domain_name     = var.api_origin_domain_name != "" ? var.api_origin_domain_name : module.alb.alb_dns_name
  api_origin_protocol_policy = var.api_origin_domain_name != "" && var.certificate_arn != "" ? "https-only" : "http-only"
  api_path_patterns          = flatten([for service in values(local.alb_services) : service.path_patterns])
}

# ECS tasks security group — created here (not in the ecs module) so RDS can reference it without a cycle.
resource "aws_security_group" "ecs" {
  name_prefix = "${var.name}-ecs-"
  vpc_id      = module.network.vpc_id

  ingress {
    description     = "App ports from ALB"
    from_port       = 3000
    to_port         = 9000
    protocol        = "tcp"
    security_groups = [module.alb.alb_sg_id]
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

module "rds" {
  source          = "../../modules/rds"
  name            = var.name
  vpc_id          = module.network.vpc_id
  data_subnet_ids = module.network.data_subnet_ids
  ecs_sg_id       = aws_security_group.ecs.id
}

module "ecs" {
  source             = "../../modules/ecs"
  name               = var.name
  region             = var.region
  app_subnet_ids     = module.network.app_subnet_ids
  ecs_sg_id          = aws_security_group.ecs.id
  services           = local.ecs_services
  jwt_secret_arn     = module.secrets.jwt_secret_arn
  extra_secret_arns  = [module.rds.master_secret_arn]
  dynamodb_table_arn = module.dynamodb.table_arn
  secrets_arn_prefix = local.secrets_prefix
  event_bus_arn      = module.eventbridge.bus_arn
  event_queue_arns   = module.eventbridge.queue_arns
}

module "eventbridge" {
  source          = "../../modules/eventbridge"
  name            = var.name
  lambda_jar_path = var.lambda_jar_path
}

module "observability" {
  source                     = "../../modules/observability"
  name                       = var.name
  region                     = var.region
  alb_arn_suffix             = module.alb.alb_arn_suffix
  lambda_name                = module.eventbridge.lambda_name
  dlq_name                   = module.eventbridge.dlq_name
  internal_dlq_name          = module.eventbridge.internal_dlq_name
  consumer_queue_names       = module.eventbridge.queue_names
  activity_log_group_name    = module.ecs.activity_log_group_name
  integration_log_group_name = module.ecs.integration_log_group_name
  rds_identifier             = "${var.name}-postgres"
  alarm_email                = var.alarm_email
}
