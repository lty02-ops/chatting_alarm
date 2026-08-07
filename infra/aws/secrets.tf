resource "aws_secretsmanager_secret" "backend" {
  name                    = "chatting-alarm/${var.environment}/backend"
  description             = "Runtime secrets for the chatting-alarm backend"
  recovery_window_in_days = 7

  tags = {
    Name        = "chatting-alarm-backend-secrets"
    Environment = var.environment
  }
}

resource "aws_iam_policy" "backend_secrets_policy" {
  name        = "chatting-alarm-backend-secrets-policy"
  description = "Allow the backend workload to read its application and RDS secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadBackendSecrets"
        Effect = "Allow"
        Action = [
          "secretsmanager:DescribeSecret",
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.backend.arn,
          aws_db_instance.chatting_alarm_db.master_user_secret[0].secret_arn
        ]
      }
    ]
  })

  tags = {
    Name        = "chatting-alarm-backend-secrets-policy"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "backend_secrets_policy_attachment" {
  role       = aws_iam_role.backend_irsa_role.name
  policy_arn = aws_iam_policy.backend_secrets_policy.arn
}
