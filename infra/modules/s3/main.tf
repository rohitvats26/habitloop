variable "name" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}

# S3 static website hosting for the SvelteKit frontend (adapter-static build).
# PDF Session 3: "Amazon S3 for hosting and globally distributing the frontend application."
# NOTE: an S3 website endpoint is HTTP-only; HTTPS/global CDN would need CloudFront (out of brief scope).
resource "aws_s3_bucket" "frontend" {
  bucket        = "${var.name}-frontend"
  force_destroy = true # demo: allow `terraform destroy` to empty the bucket
  tags          = merge(var.tags, { Name = "${var.name}-frontend" })
}

resource "aws_s3_bucket_website_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  index_document { suffix = "index.html" }
  # SPA fallback: unknown routes return index.html so client-side routing works.
  error_document { key = "index.html" }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket     = aws_s3_bucket.frontend.id
  depends_on = [aws_s3_bucket_public_access_block.frontend]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadGetObject"
      Effect    = "Allow"
      Principal = "*"
      Action    = ["s3:GetObject"]
      Resource  = ["${aws_s3_bucket.frontend.arn}/*"]
    }]
  })
}

output "bucket_name" { value = aws_s3_bucket.frontend.id }
output "website_endpoint" { value = aws_s3_bucket_website_configuration.frontend.website_endpoint }
