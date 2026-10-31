data "archive_file" "incident_collector" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/incident_collector"
  output_path = "${path.module}/.terraform/incident-collector.zip"
}

resource "aws_cloudwatch_log_group" "incident_collector" {
  name              = "/aws/lambda/chatting-alarm-${var.environment}-incident-collector"
  retention_in_days = 7

  tags = {
    Name        = "chatting-alarm-incident-collector-logs"
    Environment = var.environment
  }
}

resource "aws_lambda_function" "incident_collector" {
  function_name = "chatting-alarm-${var.environment}-incident-collector"
  description   = "Collect bounded EKS incident context, analyze it with Bedrock, and notify operators."
  role          = aws_iam_role.incident_collector.arn
  runtime       = "python3.12"
  architectures = ["arm64"]
  handler       = "handler.lambda_handler"
  memory_size   = 256
  timeout       = 60

  filename         = data.archive_file.incident_collector.output_path
  source_code_hash = data.archive_file.incident_collector.output_base64sha256

  environment {
    variables = {
      ALLOWED_ALARM_NAMES = join(",", [
        aws_cloudwatch_metric_alarm.backend_crash_loop_back_off.alarm_name,
        aws_cloudwatch_metric_alarm.backend_oom_killed.alarm_name,
        aws_cloudwatch_metric_alarm.kubernetes_warning_events.alarm_name
      ])
      APPLICATION_LOG_GROUP = local.container_insights_application_log_group
      CLUSTER_NAME          = local.cluster_name
      KUBERNETES_NAMESPACE  = "chatting-alarm"
      LOG_LOOKBACK_SECONDS  = "300"
      MAX_LOG_CHARACTERS    = "20000"
      MAX_LOG_EVENTS        = "100"
      BEDROCK_MODEL_ID      = var.aiops_bedrock_model_id
      BEDROCK_MAX_TOKENS    = "1200"
      ALERT_TOPIC_ARN       = aws_sns_topic.infrastructure_alerts.arn
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.incident_collector,
    aws_iam_role_policy.incident_collector
  ]

  tags = {
    Name        = "chatting-alarm-incident-collector"
    Environment = var.environment
  }
}

resource "aws_lambda_event_source_mapping" "incident_collector" {
  event_source_arn                   = aws_sqs_queue.aiops_events.arn
  function_name                      = aws_lambda_function.incident_collector.arn
  batch_size                         = 1
  maximum_batching_window_in_seconds = 0
  function_response_types            = ["ReportBatchItemFailures"]
  enabled                            = true
}
