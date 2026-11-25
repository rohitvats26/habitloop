# HabitLoop AWS Infrastructure (Terraform) — Session 3

Terraform for the AWS deployment in [docs/06](../docs/06-aws-infrastructure.md), aligned to the case-study
PDF's resource list and sized for the **AWS Free Tier**: VPC (3 subnet tiers × 2 AZs) with **VPC endpoints
instead of a NAT Gateway**, Network ACLs, ALB → ECS Fargate (6 backend services, auto-scaling), ECR,
RDS PostgreSQL (single-AZ `db.t3.micro`), DynamoDB, EventBridge + Lambda + SQS DLQ, Secrets Manager,
IAM least-privilege, CloudWatch dashboard + alarms, and an **S3** bucket hosting the static frontend.

> **Not deployed.** No AWS account was used. The configuration is verified with `terraform init` +
> `terraform validate` (**valid**) and `terraform fmt`. `terraform plan`/`apply` need real AWS credentials
> and were not run — nothing here is claimed as a live deployment (brief rule #9).

## Layout
```
infra/
├── modules/
│   ├── network/        VPC, public/app/data subnets, IGW, routes, Network ACLs, VPC endpoints (no NAT)
│   ├── alb/            ALB, SG, per-service target groups + path rules, optional HTTPS listener
│   ├── ecs/            Fargate cluster, 6 backend task defs + services (for_each), autoscaling, IAM roles
│   ├── rds/            PostgreSQL single-AZ db.t3.micro, SG (ECS-only), RDS-managed master secret
│   ├── dynamodb/       analytics single table + GSI1, PITR, SSE
│   ├── ecr/            one repo per backend service
│   ├── s3/             static website bucket for the SvelteKit frontend
│   ├── secrets/        JWT signing secret
│   ├── eventbridge/    custom bus, normalizer Lambda (publishes back to the bus), SQS DLQ, rule + target
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

## Deploy (with AWS credentials)
```bash
cd infra/envs/dev
cp terraform.tfvars.example terraform.tfvars    # set jwt_secret, alarm_email, region

# 1. Create the registries/infra first
terraform apply

# 2. Build + push backend images to the ECR repos this stack created (tag = var.image_tag, default "latest").
#    Login: aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin \
#           <acct>.dkr.ecr.<region>.amazonaws.com
#    Per service: docker build -t <ecr_url>:latest backend/<service> && docker push <ecr_url>:latest
#    (ecr_url values: terraform output ecr_repository_urls). Then force a new ECS deployment to pull images.

# 3. Build + upload the static frontend to S3
#    The bucket name includes the AWS account ID to ensure global S3 name uniqueness.
cd ../../../frontend
#   set every VITE_*_SERVICE_URL in .env to the ALB URL: terraform output -raw alb_dns_name
npm install && npm run build
aws s3 sync build/ s3://$(cd ../infra/envs/dev && terraform output -raw frontend_bucket)/ --delete

# App URL:   terraform output -raw frontend_url      (S3 website, HTTP)
# API URL:   terraform output -raw alb_dns_name
```

## Tear down (stop all charges)
```bash
cd infra/envs/dev
terraform destroy          # the frontend bucket empties itself (force_destroy = true)
```
Fargate and the ALB bill by the hour, so for a demo: `apply` → record → `destroy`. RDS single-AZ
`db.t3.micro`, DynamoDB on-demand, Lambda, EventBridge and S3 stay within the 12-month/always-free tiers;
Fargate (~7 small tasks) and the ALB have no free tier, so a short-lived demo keeps the cost to a few dollars.

## Known gaps (documented)
- **HTTPS/TLS:** the ALB listener is HTTP:80 by default; set `certificate_arn` (needs a domain + ACM cert)
  to add an HTTPS:443 listener with an 80→443 redirect. The **S3 website endpoint is HTTP-only** — true
  HTTPS for the static site would need **CloudFront**, which is outside the PDF's resource list.
- **Internal event chain on AWS:** implemented over **EventBridge + SQS** (`EVENTS_TRANSPORT=eventbridge`).
  Services PutEvents to the bus; each consumer long-polls its own SQS queue (EventBridge rule → SQS), and the
  Lambda normalizer publishes `partner.activity.normalized` back to the bus — so the deployed
  activity→analytics→recommendation flow runs end-to-end (no MSK needed). Kafka is only the local transport.
- **Service-to-service calls** use the public ALB DNS for simplicity; production would use an internal ALB or
  ECS Service Connect / Cloud Map and keep `/internal/*` off the public listener.
- **CORS** defaults to `*` (`habitloop.cors.allowed-origins`); tighten to the S3 website origin for production.
