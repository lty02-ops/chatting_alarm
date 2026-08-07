output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.chatting_alarm_eks.name
}

output "eks_cluster_endpoint" {
  description = "EKS API endpoint"
  value       = aws_eks_cluster.chatting_alarm_eks.endpoint
}

output "eks_cluster_certificate_authority" {
  description = "EKS cluster CA data"
  value       = aws_eks_cluster.chatting_alarm_eks.certificate_authority[0].data
  sensitive   = true
}

output "eks_oidc_provider_arn" {
  description = "EKS OIDC provider ARN for IRSA"
  value       = aws_iam_openid_connect_provider.eks_oidc_provider.arn
}

output "rds_endpoint" {
  description = "RDS MySQL endpoint"
  value       = aws_db_instance.chatting_alarm_db.address
}

output "rds_port" {
  description = "RDS MySQL port"
  value       = aws_db_instance.chatting_alarm_db.port
}

output "rds_database_name" {
  description = "Application database name"
  value       = aws_db_instance.chatting_alarm_db.db_name
}

output "redis_primary_endpoint" {
  description = "Redis primary endpoint"
  value       = aws_elasticache_replication_group.chatting_alarm.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "Redis reader endpoint"
  value       = aws_elasticache_replication_group.chatting_alarm.reader_endpoint_address
}

output "redis_port" {
  description = "Redis port"
  value       = aws_elasticache_replication_group.chatting_alarm.port
}

output "backend_ecr_repository_url" {
  description = "Backend ECR repository URL"
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_ecr_repository_url" {
  description = "Frontend ECR repository URL"
  value       = aws_ecr_repository.frontend.repository_url
}

output "s3_bucket_name" {
  description = "Application file storage bucket"
  value       = aws_s3_bucket.chatting_alarm_s3_bucket.bucket
}

output "s3_bucket_arn" {
  description = "Application file storage bucket ARN"
  value       = aws_s3_bucket.chatting_alarm_s3_bucket.arn
}

output "vpc_id" {
  description = "Application VPC ID"
  value       = aws_vpc.chatting_alarm_vpc.id
}

output "private_subnet_ids" {
  description = "Private application subnet IDs"
  value = [
    aws_subnet.private_subnet_1.id,
    aws_subnet.private_subnet_2.id
  ]
}

output "backend_irsa_role_arn" {
  description = "IAM role ARN for backend Kubernetes ServiceAccount"
  value       = aws_iam_role.backend_irsa_role.arn
}

output "backend_secret_arn" {
  description = "Secrets Manager ARN for backend runtime secrets"
  value       = aws_secretsmanager_secret.backend.arn
}

output "rds_master_secret_arn" {
  description = "AWS-managed RDS master credential secret ARN"
  value       = aws_db_instance.chatting_alarm_db.master_user_secret[0].secret_arn
}

output "alb_controller_role_arn" {
  description = "IRSA role ARN for AWS Load Balancer Controller"
  value       = aws_iam_role.alb_controller_role.arn
}

output "acm_certificate_arn" {
  description = "ACM certificate ARN for ALB HTTPS listener"
  value       = aws_acm_certificate.chatting_alarm_acm_cert.arn
}

output "aws_region" {
  description = "AWS region used by the infrastructure"
  value       = var.region
}

output "acm_dns_validation_records" {
  description = "CNAME records that must be added at the external DNS provider"
  value = {
    for option in aws_acm_certificate.chatting_alarm_acm_cert.domain_validation_options :
    option.domain_name => {
      name  = option.resource_record_name
      type  = option.resource_record_type
      value = option.resource_record_value
    }
  }
}

output "alb_security_group_id" {
  description = "Security group assigned to the internet-facing ALB"
  value       = aws_security_group.chatting_alarm_alb_sg.id
}

output "alert_topic_arn" {
  description = "SNS topic ARN for infrastructure alarms"
  value       = aws_sns_topic.infrastructure_alerts.arn
}

output "github_actions_role_arn" {
  description = "IAM role assumed by GitHub Actions"
  value       = var.github_repository == null ? null : aws_iam_role.github_actions[0].arn
}
