data "aws_caller_identity" "current" {}

locals {
  svc_names = [
    "user-service", "activity-service", "challenge-service",
    "integration-service", "analytics-service", "recommendation-service", "frontend"
  ]
  images         = { for s in local.svc_names : s => "${module.ecr.repository_urls[s]}:${var.image_tag}" }
  rds_host       = module.rds.endpoint
  db_pwd_ref     = "${module.rds.master_secret_arn}:password::"
  alb_dns        = "http://${module.alb.alb_dns_name}"
  secrets_prefix = "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.name}/*"

  # ALB path routing (mirrors the local Docker Compose / gateway routing).
  alb_services = {
    "user-service"           = { port = 8081, priority = 10, health_check_path = "/actuator/health", path_patterns = ["/api/auth/*", "/api/users/*", "/api/tenants/*"] }
    "activity-service"       = { port = 8082, priority = 20, health_check_path = "/actuator/health", path_patterns = ["/api/habits/*", "/api/activity/*"] }
    "challenge-service"      = { port = 8083, priority = 30, health_check_path = "/actuator/health", path_patterns = ["/api/challenges/*", "/api/invitations/*"] }
    "integration-service"    = { port = 8084, priority = 40, health_check_path = "/actuator/health", path_patterns = ["/api/integrations/*", "/api/webhooks/*"] }
    "analytics-service"      = { port = 8085, priority = 50, health_check_path = "/actuator/health", path_patterns = ["/api/analytics/*"] }
    "recommendation-service" = { port = 8086, priority = 60, health_check_path = "/actuator/health", path_patterns = ["/api/recommendations/*"] }
    "frontend"               = { port = 3000, priority = 100, health_check_path = "/", path_patterns = ["/*"] }
  }

  common_backend_env = {
    KAFKA_ENABLED = "false" # set true + KAFKA_BOOTSTRAP when an MSK cluster is added (see README)
  }

  ecs_services = {
    "user-service" = {
      image            = local.images["user-service"], port = 8081, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { USER_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_user", USER_DB_USER = "habitloop", SEED_DEMO = "false" })
      secrets          = { USER_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["user-service"]
    }
    "activity-service" = {
      image            = local.images["activity-service"], port = 8082, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { ACTIVITY_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_activity", ACTIVITY_DB_USER = "habitloop" })
      secrets          = { ACTIVITY_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["activity-service"]
    }
    "challenge-service" = {
      image            = local.images["challenge-service"], port = 8083, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { CHALLENGE_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_challenge", CHALLENGE_DB_USER = "habitloop", USER_SERVICE_URL = local.alb_dns })
      secrets          = { CHALLENGE_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["challenge-service"]
    }
    "integration-service" = {
      image            = local.images["integration-service"], port = 8084, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { INTEGRATION_DB_URL = "jdbc:postgresql://${local.rds_host}:5432/habitloop_integration", INTEGRATION_DB_USER = "habitloop", WEBHOOK_BASE_URL = local.alb_dns })
      secrets          = { INTEGRATION_DB_PASSWORD = local.db_pwd_ref }
      target_group_arn = module.alb.target_group_arns["integration-service"]
    }
    "analytics-service" = {
      image            = local.images["analytics-service"], port = 8085, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { ANALYTICS_STORE = "dynamodb", ANALYTICS_TABLE = module.dynamodb.table_name, AWS_REGION = var.region })
      secrets          = {}
      target_group_arn = module.alb.target_group_arns["analytics-service"]
    }
    "recommendation-service" = {
      image            = local.images["recommendation-service"], port = 8086, cpu = 512, memory = 1024, desired_count = 2
      environment      = merge(local.common_backend_env, { ANALYTICS_SERVICE_URL = local.alb_dns })
      secrets          = {}
      target_group_arn = module.alb.target_group_arns["recommendation-service"]
    }
    "frontend" = {
      image = local.images["frontend"], port = 3000, cpu = 256, memory = 512, desired_count = 2
      environment = {
        USER_SERVICE_URL        = local.alb_dns, ACTIVITY_SERVICE_URL = local.alb_dns, CHALLENGE_SERVICE_URL = local.alb_dns,
        INTEGRATION_SERVICE_URL = local.alb_dns, ANALYTICS_SERVICE_URL = local.alb_dns, RECOMMENDATION_SERVICE_URL = local.alb_dns
      }
      secrets          = {}
      target_group_arn = module.alb.target_group_arns["frontend"]
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
}

module "eventbridge" {
  source          = "../../modules/eventbridge"
  name            = var.name
  lambda_jar_path = var.lambda_jar_path
}

module "observability" {
  source         = "../../modules/observability"
  name           = var.name
  region         = var.region
  alb_arn_suffix = module.alb.alb_arn_suffix
  lambda_name    = module.eventbridge.lambda_name
  dlq_name       = module.eventbridge.dlq_name
  rds_identifier = "${var.name}-postgres"
  alarm_email    = var.alarm_email
}
