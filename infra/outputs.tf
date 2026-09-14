output "cluster_name" {
  value       = kind_cluster.ci_cluster.name
  description = "Nome do cluster Kind criado."
}

output "namespace" {
  value       = var.namespace
  description = "Namespace da aplicacao."
}

output "api_url" {
  value       = "http://127.0.0.1:${var.api_host_port}"
  description = "URL local da API exposta pelo Kind."
}

output "kubeconfig_path" {
  value       = abspath(kind_cluster.ci_cluster.kubeconfig_path)
  description = "Caminho local do kubeconfig gerado."
}
