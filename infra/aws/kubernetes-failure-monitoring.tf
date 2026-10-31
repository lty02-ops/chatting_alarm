locals {
  container_insights_application_log_group = "/aws/containerinsights/${local.cluster_name}/application"
}

# Pod names change on every rollout, so Metrics Insights selects all backend
# pods instead of hard-coding an individual PodName dimension.
resource "aws_cloudwatch_metric_alarm" "backend_crash_loop_back_off" {
  alarm_name          = "chatting-alarm-${var.environment}-backend-crash-loop-back-off"
  alarm_description   = "A chatting-alarm backend container is in CrashLoopBackOff."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.infrastructure_alerts.arn]
  ok_actions          = [aws_sns_topic.infrastructure_alerts.arn]

  metric_query {
    id          = "crashloop"
    expression  = <<-EOT
      SELECT MAX(pod_container_status_waiting_reason_crash_loop_back_off)
      FROM SCHEMA("ContainerInsights", ClusterName, Namespace, Service)
      WHERE ClusterName = '${local.cluster_name}'
        AND Namespace = 'chatting-alarm'
        AND Service = 'chatting-alarm-backend'
    EOT
    label       = "Backend containers in CrashLoopBackOff"
    period      = 60
    return_data = true
  }

  tags = {
    Name        = "chatting-alarm-backend-crash-loop-back-off"
    Environment = var.environment
  }
}

resource "aws_cloudwatch_metric_alarm" "backend_oom_killed" {
  alarm_name          = "chatting-alarm-${var.environment}-backend-oom-killed"
  alarm_description   = "A chatting-alarm backend container exceeded its memory limit and was OOMKilled."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.infrastructure_alerts.arn]
  ok_actions          = [aws_sns_topic.infrastructure_alerts.arn]

  metric_query {
    id          = "oomkilled"
    expression  = <<-EOT
      SELECT MAX(pod_container_status_terminated_reason_oom_killed)
      FROM SCHEMA("ContainerInsights", ClusterName, Namespace, PodName)
      WHERE ClusterName = '${local.cluster_name}'
        AND Namespace = 'chatting-alarm'
    EOT
    label       = "Backend containers terminated by OOMKilled"
    period      = 60
    return_data = true
  }

  tags = {
    Name        = "chatting-alarm-backend-oom-killed"
    Environment = var.environment
  }
}

# The exporter emits a stable marker in every Warning event. This pattern works
# whether Fluent Bit merges the JSON or stores it inside its outer log field.
resource "aws_cloudwatch_log_metric_filter" "kubernetes_warning_events" {
  name           = "chatting-alarm-${var.environment}-kubernetes-warning-events"
  log_group_name = local.container_insights_application_log_group
  pattern        = "\"kubernetes-warning-event\""

  metric_transformation {
    name          = "KubernetesWarningEventCount"
    namespace     = "ChattingAlarm/Operations"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }

  depends_on = [aws_eks_addon.cloudwatch_observability]
}

resource "aws_cloudwatch_metric_alarm" "kubernetes_warning_events" {
  alarm_name          = "chatting-alarm-${var.environment}-kubernetes-warning-events"
  alarm_description   = "Kubernetes Warning events are repeatedly occurring in the chatting-alarm namespace."
  namespace           = "ChattingAlarm/Operations"
  metric_name         = "KubernetesWarningEventCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.infrastructure_alerts.arn]
  ok_actions          = [aws_sns_topic.infrastructure_alerts.arn]

  tags = {
    Name        = "chatting-alarm-kubernetes-warning-events"
    Environment = var.environment
  }
}
