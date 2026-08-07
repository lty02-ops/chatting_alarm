data "aws_iam_policy_document" "alb_controller_assume_role_policy" {
  statement {
    effect = "Allow"

    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type = "Federated"

      identifiers = [aws_iam_openid_connect_provider.eks_oidc_provider.arn]
    }

    condition {
      test = "StringEquals"

      variable = "${replace(
      aws_eks_cluster.chatting_alarm_eks.identity[0].oidc[0].issuer, "https://", "")}:aud"

      values = ["sts.amazonaws.com"]
    }

    condition {
      test = "StringEquals"

      variable = "${replace(
      aws_eks_cluster.chatting_alarm_eks.identity[0].oidc[0].issuer, "https://", "")}:sub"

      values = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }
  }
}

resource "aws_iam_policy" "alb_controller_policy" {
  name = "chatting-alarm-alb-controller-policy"

  policy = file(
    "${path.module}/policies/aws-load-balancer-controller.json"
  )

  tags = {
    Name        = "chatting-alarm-alb-controller-policy"
    Environment = var.environment
  }
}

resource "aws_iam_role" "alb_controller_role" {
  name = "chatting-alarm-alb-controller-role"

  assume_role_policy = data.aws_iam_policy_document.alb_controller_assume_role_policy.json

  tags = {
    Name        = "chatting-alarm-alb-controller-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "alb_controller_policy_attachment" {
  role       = aws_iam_role.alb_controller_role.name
  policy_arn = aws_iam_policy.alb_controller_policy.arn
}