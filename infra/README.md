# HabitLoop AWS Infrastructure (Terraform) — Session 3

Terraform for the AWS deployment described in [docs/06](../docs/06-aws-infrastructure.md): VPC (3 subnet
tiers × 2 AZs), ALB with path routing, ECS Fargate services, ECR, RDS PostgreSQL (Multi-AZ), DynamoDB,
EventBridge + Lambda + SQS DLQ, Secrets Manager, IAM least-privilege roles, and CloudWatch
dashboard + alarms.

> **Not deployed.** No AWS account was used. The configuration was verified with
> `terraform init` + `terraform validate` (**valid**) and `terraform fmt`. `terraform plan`/`apply` need
> real AWS credentials and were not run — nothing here is claimed as a live deployment (brief rule #9).

## Layout
```
infra/
├── modules/
│   ├── network/        VPC, public/app/data subnets, IGW, NAT, routes, DynamoDB VPC endpoint
│   ├── alb/            ALB, security group, per-service target groups + path-based listener rules
│   ├── ecs/            Fargate cluster, task defs + services (for_each), autoscaling, IAM roles
│   ├── rds/            PostgreSQL Multi-AZ, SG (ECS-only), RDS-managed master secret
│   ├── dynamodb/       analytics single table + GSI1, PITR, SSE
│   ├── ecr/            one repo per service
│   ├── secrets/        JWT signing secret
│   ├── eventbridge/    custom bus, normalizer Lambda, SQS DLQ, SNS output topic, rule + target
│   └── observability/  SNS alarm topic, CloudWatch dashboard, 5 alarms
└── envs/dev/           composition (providers, variables, main, outputs, tfvars.example)
```

## Verify locally
```bash
cd infra/envs/dev
terraform init -backend=false
terraform validate      # -> Success! The configuration is valid.
terraform fmt -recursive ..
```

## Deploy (when you have AWS credentials)
```bash
# 1. Build + push images to ECR (repos are created by this stack; push after first apply of ecr,
#    or create repos first). Tag with the value of var.image_tag.
# 2. Build the normalizer Lambda artifact: mvn -pl backend/lambda-normalizer -am package
cd infra/envs/dev
cp terraform.tfvars.example terraform.tfvars    # set jwt_secret, alarm_email, region
terraform plan
terraform apply
```

## Known gaps (documented, intentionally out of this scope)
- **Internal async (Kafka/MSK):** services run with `KAFKA_ENABLED=false` here; the event-driven chain
  (`activity.logged`, etc.) needs an **Amazon MSK** cluster + an `msk` module, then set `KAFKA_ENABLED=true`
  and `KAFKA_BOOTSTRAP`. The Lambda currently publishes normalized events to an **SNS** topic as the AWS
  stand-in for the MSK topic.
- **Service-to-service `/internal` routing:** challenge→user `/internal/users/*` calls use the public ALB
  DNS for simplicity; production should use an **internal ALB** or **ECS Service Connect / Cloud Map**, and
  `/internal/*` must not be exposed on the public listener.
- **HTTPS:** the listener is HTTP:80 for validation; add an **ACM cert** + HTTPS:443 listener with an
  HTTP→HTTPS redirect before real use.
- **DB password injection** is wired from the RDS-managed Secrets Manager secret (`*_DB_PASSWORD` via the
  container `secrets` block); the ECS service start-order may need an explicit `depends_on` the ALB
  listener on first apply.
- **Frontend hosting:** deployed here as an ECS/Fargate SSR container (adapter-node) behind the ALB; an
  S3 + CloudFront static variant is the alternative noted in docs/06.
