resource "aws_iam_role" "incident_collector" {
  name = "chatting-alarm-${var.environment}-incident-collector-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = "chatting-alarm-incident-collector-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "incident_collector" {
  name = "chatting-alarm-${var.environment}-incident-collector-policy"
  role = aws_iam_role.incident_collector.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ConsumeIncidentQueue"
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:ChangeMessageVisibility",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.aiops_events.arn
      },
      {
        Sid    = "ReadAlarmContext"
        Effect = "Allow"
        Action = [
          "cloudwatch:DescribeAlarms",
          "cloudwatch:DescribeAlarmHistory"
        ]
        Resource = "*"
      },
      {
        Sid      = "ReadContainerApplicationLogs"
        Effect   = "Allow"
        Action   = ["logs:FilterLogEvents"]
        Resource = "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:${local.container_insights_application_log_group}:*"
      },
      {
        Sid      = "AnalyzeIncidentWithBedrock"
        Effect   = "Allow"
        Action   = ["bedrock:InvokeModel"]
        Resource = "arn:aws:bedrock:${var.region}::foundation-model/${var.aiops_bedrock_model_id}"
      },
      {
        Sid      = "PublishAIOpsAlert"
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.infrastructure_alerts.arn
      },
      {
        Sid    = "WriteFunctionLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.incident_collector.arn}:*"
      }
    ]
  })
}
