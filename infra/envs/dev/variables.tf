variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "name" {
  type    = string
  default = "habitloop-dev"
}

variable "azs" {
  type    = list(string)
  default = ["ap-south-1a", "ap-south-1b"]
}

variable "jwt_secret" {
  type      = string
  sensitive = true
  default   = "change-me-in-tfvars-habitloop-jwt-secret-32bytes-min"
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "alarm_email" {
  type    = string
  default = ""
}

variable "lambda_jar_path" {
  type    = string
  default = "../../../backend/lambda-normalizer/target/lambda-normalizer-0.0.1-SNAPSHOT.jar"
}
