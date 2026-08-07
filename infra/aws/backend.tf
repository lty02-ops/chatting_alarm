terraform {
  backend "s3" {
    bucket       = "chatting-alarm-terraform-state-lty02"
    key          = "dev/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}