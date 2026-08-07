resource "aws_s3_bucket" "chatting_alarm_s3_bucket" {
  bucket        = "chatting-alarm-bucket-${var.environment}-${var.bucket_suffix}"
  force_destroy = true

  tags = {
    Name        = "chatting-alarm_bucket"
    environment = var.environment
  }
}

resource "aws_s3_bucket_public_access_block" "chatting_alarm_s3_bucket_public_access_block" {
  bucket = aws_s3_bucket.chatting_alarm_s3_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "chatting_alarm_s3_bucket_encryption" {
  bucket = aws_s3_bucket.chatting_alarm_s3_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "chatting_alarm_s3_bucket_versioning" {
  bucket = aws_s3_bucket.chatting_alarm_s3_bucket.id

  versioning_configuration {
    status = "Enabled"
  }
}
