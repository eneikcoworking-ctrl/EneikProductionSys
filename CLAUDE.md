# 🏭 Eneik Production System — Claude Guidelines & Server Access

## 🌐 Remote Production Server (Hetzner Cloud)
The entire Eneik Production System factory stack is deployed and running on a dedicated Hetzner Cloud server:
- **Host IP:** `2.28.123.162`
- **SSH Alias:** `hetzner` (configured in `~/.ssh/config` via key `~/.ssh/eneik_server`). Passwordless root access is active.
- **Project Directory on Server:** `/opt/EneikProductionSys`

## 📡 Live Factory Endpoints
- **Backend Actuator (Spring Boot):** `http://2.28.123.162:8080/actuator/health`
- **ML Service (FastAPI / Embeddings):** `http://2.28.123.162:8000`
- **Runtime Launcher:** `http://2.28.123.162:8091`
- **Judgment Proxy:** `http://2.28.123.162:8093`

## 🛠️ Remote Operations & Commands
Always run heavy operations on the Hetzner server to prevent local host RAM exhaustion:

### 1. Checking Container Status & Logs
```bash
ssh hetzner 'docker ps'
ssh hetzner 'docker logs --tail 50 eneikproductionsys-backend-1'
ssh hetzner 'docker stats --no-stream'
```

### 2. Syncing Local Code Changes to Hetzner
After editing code locally, sync changes to the server:
```bash
rsync -avz --exclude 'target/' --exclude '*.log' --exclude '.*.log' --exclude '.m2-cache/' --exclude 'chk2.jar' /mnt/c/Projects/Eneik/docker-build/EneikProductionSys/ hetzner:/opt/EneikProductionSys/
```

### 3. Rebuilding & Restarting Services on Hetzner
```bash
ssh hetzner 'cd /opt/EneikProductionSys && docker compose build backend && docker compose up -d backend'
```

### 4. Direct Shell on Server
```bash
ssh hetzner
```

## ⚖️ Epistemic Rules (Zero Local Muda)
- Do NOT run heavy multi-GB Docker stacks or full containerized builds locally on this laptop host.
- Observe telemetry directly from `http://2.28.123.162:8080/actuator/health` and Hetzner Docker logs.
