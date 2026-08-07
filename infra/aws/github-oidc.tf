data "tls_certificate" "github_actions" {
  count = var.github_repository == null ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  count = var.github_repository == null ? 0 : 1

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions[0].certificates[0].sha1_fingerprint]
}

data "aws_iam_policy_document" "github_actions_assume_role" {
  count = var.github_repository == null ? 0 : 1

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions[0].arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  count = var.github_repository == null ? 0 : 1

  name               = "chatting-alarm-github-actions-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role[0].json
}

resource "aws_iam_role_policy" "github_actions" {
  count = var.github_repository == null ? 0 : 1

  name = "chatting-alarm-github-actions-policy"
  role = aws_iam_role.github_actions[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart"
        ]
        Resource = [
          aws_ecr_repository.frontend.arn,
          aws_ecr_repository.backend.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["eks:DescribeCluster"]
        Resource = aws_eks_cluster.chatting_alarm_eks.arn
      }
    ]
  })
}

resource "aws_eks_access_entry" "github_actions" {
  count = var.github_repository == null ? 0 : 1

  cluster_name  = aws_eks_cluster.chatting_alarm_eks.name
  principal_arn = aws_iam_role.github_actions[0].arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "github_actions" {
  count = var.github_repository == null ? 0 : 1

  cluster_name  = aws_eks_cluster.chatting_alarm_eks.name
  principal_arn = aws_iam_role.github_actions[0].arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.github_actions]
}
