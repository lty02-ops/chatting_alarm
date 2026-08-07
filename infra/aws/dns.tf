resource "aws_acm_certificate" "chatting_alarm_acm_cert" {
  domain_name               = "*.chatting-alarm.p-e.kr"
  subject_alternative_names = ["chatting-alarm.p-e.kr"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name        = "chatting-alarm-certificate"
    Environment = var.environment
  }
}
