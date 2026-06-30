terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0.1"
    }
  }
}

provider "docker" {}

resource "docker_network" "private_network" {
  name = "eneik_network"
}

resource "docker_image" "ai_prediction" {
  name = "eneik-ai:latest"
  build {
    context    = "../.."
    dockerfile = "Dockerfile.ai"
  }
}

resource "docker_container" "ai_prediction" {
  image = docker_image.ai_prediction.image_id
  name  = "ai-prediction"
  networks_simple = [docker_network.private_network.name]
  ports {
    internal = 8000
    external = 8000
  }
}

resource "docker_image" "backend" {
  name = "eneik-backend:latest"
  build {
    context    = "../.."
    dockerfile = "Dockerfile"
  }
}

resource "docker_container" "backend" {
  image = docker_image.backend.image_id
  name  = "backend"
  networks_simple = [docker_network.private_network.name]
  ports {
    internal = 8080
    external = 8080
  }
  env = [
    "ML_SERVICE_URL=http://ai-prediction:8000"
  ]
  must_run = true
}

resource "docker_image" "frontend" {
  name = "eneik-frontend:latest"
  build {
    context    = "../.."
    dockerfile = "Dockerfile.frontend"
  }
}

resource "docker_container" "frontend" {
  image = docker_image.frontend.image_id
  name  = "frontend"
  networks_simple = [docker_network.private_network.name]
  ports {
    internal = 80
    external = 3000
  }
}
