resource "kind_cluster" "ci_cluster" {
  name            = var.cluster_name
  node_image      = var.kind_node_image
  wait_for_ready  = true
  kubeconfig_path = "${path.module}/kubeconfig"

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      extra_port_mappings {
        container_port = var.api_node_port
        host_port      = var.api_host_port
        listen_address = "127.0.0.1"
        protocol       = "TCP"
      }
    }
  }
}

data "kubectl_path_documents" "manifests" {
  pattern = "${path.module}/../k8s/*.yaml"

  vars = {
    api_image     = var.api_image
    api_node_port = tostring(var.api_node_port)
    db_name       = var.db_name
    db_username   = var.db_username
    namespace     = var.namespace
  }
}

locals {
  manifest_objects = {
    for key, body in data.kubectl_path_documents.manifests.manifests :
    key => yamldecode(body)
  }

  namespace_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.kind, "") == "Namespace"
  }

  metrics_foundation_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.labels["k8s-app"], "") == "metrics-server" &&
    !contains(["Deployment", "APIService"], try(manifest.kind, ""))
  }

  metrics_workload_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.labels["k8s-app"], "") == "metrics-server" &&
    try(manifest.kind, "") == "Deployment"
  }

  metrics_api_service_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.labels["k8s-app"], "") == "metrics-server" &&
    try(manifest.kind, "") == "APIService"
  }

  postgres_foundation_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.namespace, "") == var.namespace &&
    contains(["mecanica-db", "mecanica-db-pvc"], try(manifest.metadata.name, "")) &&
    try(manifest.kind, "") != "Deployment"
  }

  postgres_workload_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.namespace, "") == var.namespace &&
    try(manifest.metadata.name, "") == "mecanica-db" &&
    try(manifest.kind, "") == "Deployment"
  }

  api_foundation_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.namespace, "") == var.namespace &&
    contains(["mecanica-api", "mecanica-api-config"], try(manifest.metadata.name, "")) &&
    contains(["ConfigMap", "Service"], try(manifest.kind, ""))
  }

  api_workload_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.namespace, "") == var.namespace &&
    try(manifest.metadata.name, "") == "mecanica-api" &&
    try(manifest.kind, "") == "Deployment"
  }

  api_hpa_manifests = {
    for key, manifest in local.manifest_objects :
    key => data.kubectl_path_documents.manifests.manifests[key]
    if try(manifest.metadata.namespace, "") == var.namespace &&
    try(manifest.metadata.name, "") == "mecanica-api-hpa" &&
    try(manifest.kind, "") == "HorizontalPodAutoscaler"
  }

  classified_manifest_keys = concat(
    keys(local.namespace_manifests),
    keys(local.metrics_foundation_manifests),
    keys(local.metrics_workload_manifests),
    keys(local.metrics_api_service_manifests),
    keys(local.postgres_foundation_manifests),
    keys(local.postgres_workload_manifests),
    keys(local.api_foundation_manifests),
    keys(local.api_workload_manifests),
    keys(local.api_hpa_manifests),
  )
}

check "all_yaml_manifests_are_managed" {
  assert {
    condition = (
      length(local.classified_manifest_keys) == length(local.manifest_objects) &&
      length(setsubtract(toset(local.classified_manifest_keys), toset(keys(local.manifest_objects)))) == 0 &&
      length(setsubtract(toset(keys(local.manifest_objects)), toset(local.classified_manifest_keys))) == 0
    )
    error_message = "Todo documento YAML em k8s deve pertencer exatamente a uma etapa gerenciada pelo Terraform."
  }
}

resource "kubectl_manifest" "namespace" {
  for_each  = local.namespace_manifests
  yaml_body = each.value

  depends_on = [kind_cluster.ci_cluster]
}

resource "kubectl_manifest" "metrics_foundation" {
  for_each  = local.metrics_foundation_manifests
  yaml_body = each.value

  depends_on = [kind_cluster.ci_cluster]
}

resource "kubectl_manifest" "metrics_workload" {
  for_each         = local.metrics_workload_manifests
  yaml_body        = each.value
  wait_for_rollout = true

  depends_on = [kubectl_manifest.metrics_foundation]
}

resource "kubectl_manifest" "metrics_api_service" {
  for_each  = local.metrics_api_service_manifests
  yaml_body = each.value

  depends_on = [kubectl_manifest.metrics_workload]
}

resource "kubectl_manifest" "postgres_secret" {
  yaml_body = yamlencode({
    apiVersion = "v1"
    kind       = "Secret"
    metadata = {
      name      = "mecanica-db-secret"
      namespace = var.namespace
    }
    type = "Opaque"
    data = {
      POSTGRES_DB       = base64encode(var.db_name)
      POSTGRES_USER     = base64encode(var.db_username)
      POSTGRES_PASSWORD = base64encode(var.db_password)
    }
  })

  depends_on = [kubectl_manifest.namespace]
}

resource "kubectl_manifest" "api_secret" {
  yaml_body = yamlencode({
    apiVersion = "v1"
    kind       = "Secret"
    metadata = {
      name      = "mecanica-api-secret"
      namespace = var.namespace
    }
    type = "Opaque"
    data = {
      SPRING_DATASOURCE_USERNAME = base64encode(var.db_username)
      SPRING_DATASOURCE_PASSWORD = base64encode(var.db_password)
      JWT_SECRET                 = base64encode(var.jwt_secret)
    }
  })

  depends_on = [kubectl_manifest.namespace]
}

resource "kubectl_manifest" "postgres_foundation" {
  for_each  = local.postgres_foundation_manifests
  yaml_body = each.value

  depends_on = [
    kubectl_manifest.namespace,
    kubectl_manifest.postgres_secret,
  ]
}

resource "kubectl_manifest" "postgres_workload" {
  for_each         = local.postgres_workload_manifests
  yaml_body        = each.value
  wait_for_rollout = true

  depends_on = [kubectl_manifest.postgres_foundation]
}

resource "kubectl_manifest" "api_foundation" {
  for_each  = local.api_foundation_manifests
  yaml_body = each.value

  depends_on = [
    kubectl_manifest.api_secret,
    kubectl_manifest.postgres_workload,
  ]
}

resource "kubectl_manifest" "api_workload" {
  for_each         = local.api_workload_manifests
  yaml_body        = each.value
  wait_for_rollout = true

  depends_on = [kubectl_manifest.api_foundation]
}

resource "kubectl_manifest" "api_hpa" {
  for_each  = local.api_hpa_manifests
  yaml_body = each.value

  depends_on = [
    kubectl_manifest.api_workload,
    kubectl_manifest.metrics_api_service,
  ]
}
