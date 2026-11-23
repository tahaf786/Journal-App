# Deploying the Journal service to AWS (one EC2 instance)

This is the cheapest honest route to a public URL: one EC2 instance running the same Docker Compose stack used locally. It takes about 30 minutes. Nothing here has been run yet; the owner creates the AWS account and runs these steps.

## Before you start

- Create the AWS account yourself and turn on MFA for the root user. Do all work below as an IAM user or IAM Identity Center user, not root.
- **Set a budget first:** Billing → Budgets → Create budget → "Zero spend budget" or a monthly cost budget of USD 5 with an email alert. This is the guard against a forgotten instance.
- Region: `ap-south-1` (Mumbai) is closest to India. Any region works.

## Sizing (read this, the free tier is too small for the full stack)

The compose stack limits add up to about 2.5 GB (app 640 MB, MongoDB 512 MB, Kafka 1 GB, Redis 128 MB, Prometheus 256 MB). A 1 GB "micro" instance cannot hold it.

| Option | RAM | What to do |
|---|---|---|
| Recommended | 4 GB (`t3.medium`, or `m7i-flex.large` / `c7i-flex.large` if the console marks them "Free tier eligible" for your account) | Run the full stack. Check the console's free-tier label and the EC2 pricing page for the current hourly price before launching. |
| Cheapest | 2 GB (`t3.small` / `t4g.small`) | Add a 2 GB swap file (below) and start without Prometheus: `docker compose up -d app mongo redis kafka`. Expect slower start-up. |

New AWS accounts get free-tier credits; check Billing → Free Tier for what yours includes. Stop the instance when you are not demoing it; a stopped instance costs only its disk.

## Steps

1. **Launch the instance:** EC2 → Launch instance.
   - AMI: Amazon Linux 2023 (x86_64 for `t3`, arm64 for `t4g`).
   - Key pair: create one, download the `.pem`, keep it outside every repository.
   - Storage: 20 GB gp3.
   - Security group: allow SSH (22) from **My IP** only, and TCP 8080 from anywhere (0.0.0.0/0). Do **not** open 27017, 6379, 9092 or 9090.
2. **Connect:** EC2 → the instance → Connect → EC2 Instance Connect (browser), or `ssh -i key.pem ec2-user@<public-ip>`.
3. **Install Docker and Compose:**
   ```bash
   sudo dnf install -y docker git
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user && newgrp docker
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
     -o /usr/local/lib/docker/cli-plugins/docker-compose
   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   docker compose version
   ```
4. **(2 GB instances only) add swap:**
   ```bash
   sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
5. **Get the code:** `git clone https://github.com/<your-account>/Journal-App.git && cd Journal-App` (after you push it).
6. **Set a real signing secret** (never commit it):
   ```bash
   echo "JWT_SECRET=$(openssl rand -base64 48)" > .env
   chmod 600 .env
   ```
7. **Start:** `docker compose up -d --build`, then `docker compose ps` until every service shows `healthy`.
8. **Check from your laptop:**
   ```bash
   curl http://<public-ip>:8080/actuator/health
   BASE_URL=http://<public-ip>:8080 ./scripts/smoke.sh
   ```
   Swagger UI: `http://<public-ip>:8080/swagger-ui.html`.
9. **Optional, stable address:** EC2 → Elastic IPs → Allocate → Associate with the instance. An Elastic IP attached to a running instance is cheap; one left unattached is billed, so release it at teardown.

## Record these two facts after deploying (they can go on the resume)

1. The public URL (Elastic IP or DNS name) and the date it went live.
2. Uptime: e.g. a free external uptime monitor on `/actuator/health`, and the percentage after a few weeks.

Resume wording once deployed: "Deployed to AWS EC2 with Docker Compose" — only after step 8 passes.

## Teardown (so nothing keeps billing)

1. EC2 → Instances → select → Instance state → **Terminate**.
2. EC2 → Volumes: delete any volume left "available".
3. EC2 → Elastic IPs: **Release** any address.
4. EC2 → Security Groups: delete the group you created.
5. Billing → Bills: confirm the next day that charges have stopped.

## Later upgrades (each is a real resume line only once done)

- Put the app behind HTTPS with a domain (Caddy or nginx container with Let's Encrypt).
- Move MongoDB to MongoDB Atlas free tier and Redis to a managed cache, leaving EC2 for the app and Kafka.
- Push the image to Amazon ECR and run it on ECS Fargate instead of a hand-managed instance.
- Send container logs to CloudWatch Logs with the `awslogs` driver.
