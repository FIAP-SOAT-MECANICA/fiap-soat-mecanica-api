terraform {
  required_version = ">= 1.14.5, < 1.16.0"

  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "0.11.0"
    }

    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "1.19.0"
    }
  }
}

provider "kind" {}

provider "kubectl" {
  host                   = kind_cluster.ci_cluster.endpoint
  client_certificate     = kind_cluster.ci_cluster.client_certificate
  client_key             = kind_cluster.ci_cluster.client_key
  cluster_ca_certificate = kind_cluster.ci_cluster.cluster_ca_certificate
  load_config_file       = false
}
