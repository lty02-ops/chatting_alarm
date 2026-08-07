resource "aws_security_group" "chatting_alarm_alb_sg" {
  name   = "chatting_alarm-alb-sg"
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "chatting_alarm-alb-sg"
  }
}

resource "aws_security_group" "chatting_alarm_app_sg" {
  name   = "chatting_alarm-private-sg"
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  ingress {
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.chatting_alarm_alb_sg.id]
  }

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.chatting_alarm_alb_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "chatting_alarm-app-sg"
  }
}

resource "aws_security_group" "chatting_alarm_db_sg" {
  name   = "chatting-alarm-db-sg"
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.chatting_alarm_eks.vpc_config[0].cluster_security_group_id]
  }
}

resource "aws_security_group" "chatting_alarm_redis_sg" {
  name   = "chatting_alarm-redis-sg"
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.chatting_alarm_eks.vpc_config[0].cluster_security_group_id]
  }

  tags = {
    Name = "chatting-alarm-redis-sg"
  }
}
