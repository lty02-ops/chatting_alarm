variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "db_username" {
  description = "RDS Mysql username"
  type        = string
  default     = "chat_user"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "dev"
}

variable "bucket_suffix" {
  description = "Unique suffix for globally unique S3 bucket name"
  type        = string
  default     = "lty02"
}

variable "alert_email" {
  description = "Optional email address for infrastructure alarms"
  type        = string
  default     = null
}

variable "aiops_bedrock_model_id" {
  description = "Amazon Bedrock model used for event-driven incident analysis"
  type        = string
  default     = "anthropic.claude-3-haiku-20240307-v1:0"
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy, in owner/repository format; null disables the deployment role"
  type        = string
  default     = null
}


