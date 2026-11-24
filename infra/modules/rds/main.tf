variable "name" { type = string }
variable "vpc_id" { type = string }
variable "data_subnet_ids" { type = list(string) }
variable "ecs_sg_id" { type = string }
variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}
variable "allocated_storage" {
  type    = number
  default = 20
}
variable "multi_az" {
  type    = bool
  default = true
}
variable "tags" {
  type    = map(string)
  default = {}
}

# RDS PostgreSQL in the private data subnets, reachable only from the ECS security group (docs/06 §4, §7).
resource "aws_security_group" "rds" {
  name_prefix = "${var.name}-rds-"
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name}-rds-sg" })

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ecs_sg_id]
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

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-db-subnets"
  subnet_ids = var.data_subnet_ids
  tags       = var.tags
}

resource "aws_db_instance" "this" {
  identifier        = "${var.name}-postgres"
  engine            = "postgres"
  engine_version    = "16.4"
  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage
  storage_encrypted = true
  storage_type      = "gp3"

  db_name  = "habitloop_user"
  username = "habitloop"
  # RDS manages the master password in Secrets Manager — no plaintext password in state (docs/08 §5).
  manage_master_user_password = true

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  backup_retention_period = 7
  deletion_protection     = false
  skip_final_snapshot     = true

  tags = merge(var.tags, { Name = "${var.name}-postgres" })
}

output "endpoint" { value = aws_db_instance.this.address }
output "port" { value = aws_db_instance.this.port }
output "sg_id" { value = aws_security_group.rds.id }
output "master_secret_arn" { value = aws_db_instance.this.master_user_secret[0].secret_arn }
