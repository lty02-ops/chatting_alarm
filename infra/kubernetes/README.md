# Kubernetes deployment

The manifests implement the two-AZ EKS workload shown in the architecture diagram.
Terraform must be applied first. `apply.sh` reads Terraform outputs and injects them into the manifests automatically.

Deploy all resources from WSL:

```bash
cd /mnt/d/Chatting_Alarm/infra/kubernetes
IMAGE_TAG=latest bash apply.sh
```

The Secrets Store CSI Driver and AWS provider are installed by the EKS add-on in `infra/aws/eks-addons.tf`.
