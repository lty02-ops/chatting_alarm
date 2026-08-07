resource "aws_db_instance" "chatting_alarm_db" {
  identifier = "chatting-alarm-db"

  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp2"
  storage_encrypted     = true


  db_name                     = "chatting_alarm"
  username                    = var.db_username
  manage_master_user_password = true
  port                        = 3306

  vpc_security_group_ids = [aws_security_group.chatting_alarm_db_sg.id]
  db_subnet_group_name   = aws_db_subnet_group.chatting_alarm.name

  multi_az            = true
  publicly_accessible = false

  backup_retention_period = 1
  skip_final_snapshot     = true

  tags = {
    Name = "chatting_alarm Database"
  }
}

