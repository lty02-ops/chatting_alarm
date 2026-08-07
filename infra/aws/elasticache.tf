resource "aws_elasticache_replication_group" "chatting_alarm" {
  replication_group_id = "chatting-alarm-redis"
  description          = "Redis for chatting-alarm"

  engine         = "redis"
  engine_version = "7.1"
  node_type      = "cache.t3.micro"
  port           = 6379

  # Primary 1개 + Replica 1개
  num_cache_clusters = 2

  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name = aws_elasticache_subnet_group.chatting_alarm.name

  security_group_ids = [
    aws_security_group.chatting_alarm_redis_sg.id
  ]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true

  snapshot_retention_limit = 1
  apply_immediately        = true

  tags = {
    Name = "chatting-alarm-redis"
  }
}