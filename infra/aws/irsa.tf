data "aws_iam_policy_document" "backend_irsa_assume_role_policy" {
  statement {
    effect = "Allow"

    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type = "Federated"

      identifiers = [aws_iam_openid_connect_provider.eks_oidc_provider.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_eks_cluster.chatting_alarm_eks.identity[0].oidc[0].issuer, "https://", "")}:aud"

      values = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_eks_cluster.chatting_alarm_eks.identity[0].oidc[0].issuer, "https://", "")}:sub"

      values = ["system:serviceaccount:chatting-alarm:chatting-alarm-backend"]
    }
  }
}

resource "aws_iam_role" "backend_irsa_role" {
  name = "chatting-alarm-backend-irsa-role"

  assume_role_policy = data.aws_iam_policy_document.backend_irsa_assume_role_policy.json
}

resource "aws_iam_role_policy_attachment" "backend_irsa_role_attachment" {
  role       = aws_iam_role.backend_irsa_role.name
  policy_arn = aws_iam_policy.backend_s3_policy.arn
}
