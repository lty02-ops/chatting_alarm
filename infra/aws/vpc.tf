resource "aws_vpc" "chatting_alarm_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
}

resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.chatting_alarm_vpc.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "ap-northeast-2a"
  map_public_ip_on_launch = true
  tags = {
    Name                     = "public-subnet_1"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.chatting_alarm_vpc.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "ap-northeast-2b"
  map_public_ip_on_launch = true
  tags = {
    Name                     = "public-subnet_2"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "private_subnet_1" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = "ap-northeast-2a"
  tags = {
    Name                              = "private-subnet_1"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_subnet" "private_subnet_2" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.12.0/24"
  availability_zone = "ap-northeast-2b"
  tags = {
    Name                              = "private-subnet_2"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_subnet" "private_cache_subnet_1" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.21.0/24"
  availability_zone = "ap-northeast-2a"
}

resource "aws_subnet" "private_cache_subnet_2" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.22.0/24"
  availability_zone = "ap-northeast-2b"
}

resource "aws_subnet" "db_subnet_1" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.31.0/24"
  availability_zone = "ap-northeast-2a"
}

resource "aws_subnet" "db_subnet_2" {
  vpc_id            = aws_vpc.chatting_alarm_vpc.id
  cidr_block        = "10.0.32.0/24"
  availability_zone = "ap-northeast-2b"
}

resource "aws_internet_gateway" "chatting_alarm_igw" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id
}

resource "aws_eip" "chatting_alarm_eip_1" {
  domain = "vpc"
}

resource "aws_eip" "chatting_alarm_eip_2" {
  domain = "vpc"
}

resource "aws_nat_gateway" "chatting_alarm_nat_gw_1" {
  allocation_id = aws_eip.chatting_alarm_eip_1.id
  subnet_id     = aws_subnet.public_subnet_1.id

  depends_on = [aws_internet_gateway.chatting_alarm_igw]
}

resource "aws_nat_gateway" "chatting_alarm_nat_gw_2" {
  allocation_id = aws_eip.chatting_alarm_eip_2.id
  subnet_id     = aws_subnet.public_subnet_2.id

  depends_on = [aws_internet_gateway.chatting_alarm_igw]
}

resource "aws_route_table" "public_route_table" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.chatting_alarm_igw.id
  }
}

resource "aws_route_table" "private_route_table_1" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.chatting_alarm_nat_gw_1.id
  }
}

resource "aws_route_table" "private_route_table_2" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.chatting_alarm_nat_gw_2.id
  }
}

resource "aws_route_table" "cache_route_table" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id
}


resource "aws_route_table" "db_route_table" {
  vpc_id = aws_vpc.chatting_alarm_vpc.id
}

resource "aws_route_table_association" "public_subnet_1_association" {
  subnet_id      = aws_subnet.public_subnet_1.id
  route_table_id = aws_route_table.public_route_table.id
}

resource "aws_route_table_association" "public_subnet_2_association" {
  subnet_id      = aws_subnet.public_subnet_2.id
  route_table_id = aws_route_table.public_route_table.id
}

resource "aws_route_table_association" "private_subnet_1_association" {
  subnet_id      = aws_subnet.private_subnet_1.id
  route_table_id = aws_route_table.private_route_table_1.id
}

resource "aws_route_table_association" "private_subnet_2_association" {
  subnet_id      = aws_subnet.private_subnet_2.id
  route_table_id = aws_route_table.private_route_table_2.id
}

resource "aws_route_table_association" "cache_subnet_1_association" {
  subnet_id      = aws_subnet.private_cache_subnet_1.id
  route_table_id = aws_route_table.cache_route_table.id
}

resource "aws_route_table_association" "cache_subnet_2_association" {
  subnet_id      = aws_subnet.private_cache_subnet_2.id
  route_table_id = aws_route_table.cache_route_table.id
}

resource "aws_route_table_association" "db_subnet_1_association" {
  subnet_id      = aws_subnet.db_subnet_1.id
  route_table_id = aws_route_table.db_route_table.id
}

resource "aws_route_table_association" "db_subnet_2_association" {
  subnet_id      = aws_subnet.db_subnet_2.id
  route_table_id = aws_route_table.db_route_table.id
}

resource "aws_db_subnet_group" "chatting_alarm" {
  name = "chatting-alarm-db-subnet-group"

  subnet_ids = [
    aws_subnet.db_subnet_1.id,
    aws_subnet.db_subnet_2.id
  ]

  tags = {
    Name = "chatting-alarm-db-subnet-group"
  }
}

resource "aws_elasticache_subnet_group" "chatting_alarm" {
  name = "chatting-alarm-cache-subnet-group"

  subnet_ids = [
    aws_subnet.private_cache_subnet_1.id,
    aws_subnet.private_cache_subnet_2.id
  ]

  tags = {
    Name = "chatting-alarm-cache-subnet-group"
  }
}