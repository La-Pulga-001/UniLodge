#!/usr/bin/env bash
# One-time setup for an Ubuntu 22.04/24.04 VM (Oracle Cloud Always Free).
#
#   sudo bash deploy/oracle-setup.sh
#
# Installs Docker Engine, the Compose plugin, adds the login user to the docker
# group, and adds 2 GB of swap when the VM has less than 2 GB of RAM (the
# Maven build inside Docker needs the headroom).
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
    echo "Run this script with sudo: sudo bash deploy/oracle-setup.sh" >&2
    exit 1
fi

TARGET_USER="${SUDO_USER:-ubuntu}"

echo "==> Installing Docker Engine and the Compose plugin"
apt-get update
apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

# shellcheck disable=SC1091
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

if id "${TARGET_USER}" >/dev/null 2>&1; then
    usermod -aG docker "${TARGET_USER}"
fi

MEM_KB="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
if [ "${MEM_KB}" -lt 2097152 ] && ! swapon --show | grep -q '/swapfile'; then
    echo "==> Adding 2 GB swap for the Maven build"
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
    ufw allow 22/tcp
    ufw allow 80/tcp
    ufw allow 443/tcp
fi

# Oracle's Ubuntu images ship an iptables ruleset that can reject HTTP/HTTPS
# even when the OCI security list allows them.
if command -v iptables >/dev/null 2>&1; then
    for port in 80 443; do
        if ! iptables -C INPUT -p tcp --dport "${port}" -j ACCEPT 2>/dev/null; then
            iptables -I INPUT -p tcp --dport "${port}" -j ACCEPT
        fi
    done
    if command -v netfilter-persistent >/dev/null 2>&1; then
        netfilter-persistent save
    fi
fi

cat <<EOF

Docker is ready.

Next:
  1. Log out and back in (or run: newgrp docker) so ${TARGET_USER} can use Docker.
  2. From the repository root:
       cp .env.example .env
       nano .env
       docker compose -f docker-compose.prod.yml up -d --build
  3. Check the logs:
       docker compose -f docker-compose.prod.yml logs -f app

Remember to allow inbound TCP 22, 80 and 443 in the OCI subnet's security list.
EOF
