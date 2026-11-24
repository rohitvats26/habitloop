provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "habitloop"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}
