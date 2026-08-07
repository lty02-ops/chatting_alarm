resource "aws_cloudwatch_log_group" "eks_cluster" {
  name              = "/aws/eks/${local.cluster_name}/cluster"
  retention_in_days = 30

  tags = {
    Name        = "chatting-alarm-eks-logs"
    Environment = var.environment
  }
}

resource "aws_sns_topic" "infrastructure_alerts" {
  name = "chatting-alarm-${var.environment}-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alert_email == null ? 0 : 1

  topic_arn = aws_sns_topic.infrastructure_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "chatting-alarm-${var.environment}-rds-high-cpu"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.infrastructure_alerts.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.chatting_alarm_db.identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "redis_cpu" {
  alarm_name          = "chatting-alarm-${var.environment}-redis-high-cpu"
  namespace           = "AWS/ElastiCache"
  metric_name         = "EngineCPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.infrastructure_alerts.arn]

  dimensions = {
    ReplicationGroupId = aws_elasticache_replication_group.chatting_alarm.replication_group_id
  }
}
