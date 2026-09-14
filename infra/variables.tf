variable "cluster_name" {
  type        = string
  description = "Nome do cluster Kind efemero."
  default     = "oficina-cluster"

  validation {
    condition     = can(regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", var.cluster_name))
    error_message = "cluster_name deve ser um nome DNS valido em letras minusculas."
  }
}

variable "namespace" {
  type        = string
  description = "Namespace Kubernetes da aplicacao."
  default     = "mecanica"

  validation {
    condition     = can(regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", var.namespace))
    error_message = "namespace deve ser um nome DNS valido em letras minusculas."
  }
}

variable "kind_node_image" {
  type        = string
  description = "Imagem imutavel do node Kind."
  default     = "kindest/node:v1.34.3@sha256:08497ee19eace7b4b5348db5c6a1591d7752b164530a36f855cb0f2bdcbadd48"

  validation {
    condition     = can(regex("^kindest/node:v[0-9]+\\.[0-9]+\\.[0-9]+@sha256:[0-9a-f]{64}$", var.kind_node_image))
    error_message = "kind_node_image deve conter tag e digest sha256 oficiais."
  }
}

variable "api_image" {
  type        = string
  description = "Imagem imutavel da API publicada no GHCR."

  validation {
    condition = (
      can(regex("^ghcr\\.io/[a-z0-9._/-]+:[a-zA-Z0-9._-]+$", var.api_image)) &&
      !endswith(var.api_image, ":latest")
    )
    error_message = "api_image deve usar ghcr.io/owner/repository:tag e nao pode usar latest."
  }
}

variable "api_host_port" {
  type        = number
  description = "Porta do host encaminhada ao NodePort da API."
  default     = 8080

  validation {
    condition     = var.api_host_port >= 1 && var.api_host_port <= 65535
    error_message = "api_host_port deve estar entre 1 e 65535."
  }
}

variable "api_node_port" {
  type        = number
  description = "NodePort fixo da API dentro do cluster Kind."
  default     = 30080

  validation {
    condition     = var.api_node_port >= 30000 && var.api_node_port <= 32767
    error_message = "api_node_port deve estar entre 30000 e 32767."
  }
}

variable "db_name" {
  type        = string
  description = "Nome do banco PostgreSQL."
  default     = "mecanica"
}

variable "db_username" {
  type        = string
  description = "Usuario do banco PostgreSQL."
  default     = "postgres"
}

variable "db_password" {
  type        = string
  description = "Senha do banco PostgreSQL, fornecida fora do codigo versionado."
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 8
    error_message = "db_password deve possuir ao menos 8 caracteres."
  }
}

variable "jwt_secret" {
  type        = string
  description = "Segredo JWT em Base64, fornecido fora do codigo versionado."
  sensitive   = true

  validation {
    condition     = length(var.jwt_secret) >= 32
    error_message = "jwt_secret deve possuir ao menos 32 caracteres."
  }
}
