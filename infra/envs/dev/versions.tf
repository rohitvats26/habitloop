terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Remote state (uncomment and configure before use):
  # backend "s3" {
  #   bucket         = "habitloop-tfstate"
  #   key            = "dev/terraform.tfstate"
  #   region         = "ap-south-1"
  #   dynamodb_table = "habitloop-tf-locks"
  #   encrypt        = true
  # }
}
