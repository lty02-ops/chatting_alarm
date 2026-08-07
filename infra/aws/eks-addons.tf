resource "aws_eks_addon" "secrets_store_csi_driver_provider" {
  cluster_name = aws_eks_cluster.chatting_alarm_eks.name
  addon_name   = "aws-secrets-store-csi-driver-provider"
  configuration_values = jsonencode({
    secrets-store-csi-driver = {
      syncSecret = {
        enabled = true
      }
      enableSecretRotation = true
      rotationPollInterval = "2m"
    }
  })

  depends_on = [aws_eks_node_group.chatting_alarm_eks_node_group]

  tags = {
    Name        = "chatting-alarm-secrets-store-csi"
    Environment = var.environment
  }
}

resource "aws_eks_addon" "cloudwatch_observability" {
  cluster_name = aws_eks_cluster.chatting_alarm_eks.name
  addon_name   = "amazon-cloudwatch-observability"

  depends_on = [aws_eks_node_group.chatting_alarm_eks_node_group]

  tags = {
    Name        = "chatting-alarm-cloudwatch-observability"
    Environment = var.environment
  }
}
