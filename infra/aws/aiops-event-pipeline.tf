data "aws_caller_identity" "current" {}

resource "aws_sqs_queue" "aiops_events_dlq" {
  name                      = "chatting-alarm-${var.environment}-aiops-events-dlq"
  message_retention_seconds = 604800
  sqs_managed_sse_enabled   = true

  tags = {
    Name        = "chatting-alarm-aiops-events-dlq"
    Environment = var.environment
  }
}

resource "aws_sqs_queue" "aiops_events" {
  name                       = "chatting-alarm-${var.environment}-aiops-events"
  message_retention_seconds  = 86400
  visibility_timeout_seconds = 180
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.aiops_events_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name        = "chatting-alarm-aiops-events"
    Environment = var.environment
  }
}

resource "aws_sqs_queue_redrive_allow_policy" "aiops_events_dlq" {
  queue_url = aws_sqs_queue.aiops_events_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.aiops_events.arn]
  })
}

resource "aws_cloudwatch_event_rule" "aiops_alarm_state_change" {
  name        = "chatting-alarm-${var.environment}-aiops-alarm-state-change"
  description = "Route selected CloudWatch ALARM transitions to the AIOps incident queue."

  event_pattern = jsonencode({
    source      = ["aws.cloudwatch"]
    detail-type = ["CloudWatch Alarm State Change"]
    resources = [
      aws_cloudwatch_metric_alarm.backend_crash_loop_back_off.arn,
      aws_cloudwatch_metric_alarm.backend_oom_killed.arn,
      aws_cloudwatch_metric_alarm.kubernetes_warning_events.arn
    ]
    detail = {
      state = {
        value = ["ALARM"]
      }
    }
  })

  tags = {
    Name        = "chatting-alarm-aiops-alarm-state-change"
    Environment = var.environment
  }
}

resource "aws_cloudwatch_event_target" "aiops_events_queue" {
  rule      = aws_cloudwatch_event_rule.aiops_alarm_state_change.name
  target_id = "aiops-events-queue"
  arn       = aws_sqs_queue.aiops_events.arn
}

resource "aws_sqs_queue_policy" "aiops_events" {
  queue_url = aws_sqs_queue.aiops_events.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowEventBridgeAlarmEvents"
        Effect    = "Allow"
        Principal = { Service = "events.amazonaws.com" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.aiops_events.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_cloudwatch_event_rule.aiops_alarm_state_change.arn
          }
          StringEquals = {
            "aws:SourceAccount" = data.aws_caller_identity.current.account_id
          }
        }
      }
    ]
  })
}
