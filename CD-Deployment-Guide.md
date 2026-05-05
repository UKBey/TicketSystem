# CD Deployment Guide

Bu rehber, `TicketSystemProject` icin VDS uzerine otomatik deployment (CD) kurulumunu anlatir.

## Hedef Bilgiler

- VDS IP: `138.197.182.117`
- SSH kullanici: `deployment`
- Local private key: `C:\Users\ukbet\.ssh\id_ed25519`
- Local public key: `C:\Users\ukbet\.ssh\id_ed25519.pub`
- Deploy klasoru: `/home/deployment/it-service`

## 1. VDS'e root ile baglan

Windows terminalde:

```powershell
ssh root@138.197.182.117
```

## 2. Sunucuyu guncelle ve temel paketleri kur

Sunucuda:

```bash
apt update
apt -y upgrade
apt -y install ca-certificates curl gnupg ufw fail2ban
```

## 3. Docker ve Docker Compose kur

Sunucuda:

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list
apt update
apt -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable docker
systemctl start docker
docker --version
docker compose version
```

## 4. Deployment kullanicisi olustur

Sunucuda:

```bash
adduser --disabled-password --gecos "" deployment
usermod -aG docker,sudo deployment
mkdir -p /home/deployment/.ssh
chmod 700 /home/deployment/.ssh
```

## 5. Public key'i sunucuya ekle

Not: `.pub` dosyasi sunucuya yazilir, SSH baglantisinda ise private key kullanilir.

Windows'ta public key'i gor:

```powershell
type C:\Users\ukbet\.ssh\id_ed25519.pub
```

Cikan tek satiri sunucuda su dosyaya ekle:

```bash
echo "BURAYA_PUBLIC_KEY_TEK_SATIR" >> /home/deployment/.ssh/authorized_keys
chmod 600 /home/deployment/.ssh/authorized_keys
chown -R deployment:deployment /home/deployment/.ssh
```

## 6. Firewall kurallari

Sunucuda:

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 3000/tcp
ufw allow 8080/tcp
ufw allow 8081/tcp
ufw --force enable
ufw status
```

## 7. SSH baglantisini test et

Windows terminalde:

```powershell
ssh -i C:\Users\ukbet\.ssh\id_ed25519 deployment@138.197.182.117 "whoami && docker --version && docker compose version"
```

Beklenen:

- `whoami` cikti olarak `deployment` donmeli
- Docker ve Compose versiyonlari gorunmeli

## 8. Deploy klasorunu hazirla

Windows terminalde:

```powershell
ssh -i C:\Users\ukbet\.ssh\id_ed25519 deployment@138.197.182.117 "mkdir -p /home/deployment/it-service"
```

## 9. GitHub Secrets ekle

GitHub reposunda `Settings > Secrets and variables > Actions` bolumune asagidaki secret'lari ekle:

- `VDS_HOST` = `138.197.182.117`
- `VDS_PORT` = `22`
- `VDS_USER` = `deployment`
- `VDS_SSH_PRIVATE_KEY` = `C:\Users\ukbet\.ssh\id_ed25519` iceriğinin tamamı
- `PROD_ENV_FILE` = prod `.env` dosyasinin tum icerigi

Notlar:

- `VDS_SSH_PRIVATE_KEY` icine `.pub` koyma.
- `VDS_SSH_PRIVATE_KEY` degerinin basinda `-----BEGIN OPENSSH PRIVATE KEY-----` benzeri bir satir olmalidir; sadece tek satirlik public key bu alana uygun degildir.
- `PROD_ENV_FILE` cok satirli olabilir.
- Secret icine fazladan bosluk ekleme.
- CD logunda `ssh.ParsePrivateKey: ssh: no key found` gorursen, problem genellikle bu secret'in bos olmasi ya da public key yapistirilmis olmasidir.

## 10. CD workflow'unu etkinlestir

Repo icinde workflow dosyasi su yolda olmalidir:

- `.github/workflows/cd.yml`

Bu workflow, `CI` basarili olduktan sonra `main` branch icin otomatik deploy calistirir.

## 11. Ilk deployment'i dogrula

GitHub Actions uzerinden CD basarili olduktan sonra sunucuda kontrol et:

```powershell
ssh -i C:\Users\ukbet\.ssh\id_ed25519 deployment@138.197.182.117 "cd /home/deployment/it-service && docker compose ps"
```

Log kontrolu:

```powershell
ssh -i C:\Users\ukbet\.ssh\id_ed25519 deployment@138.197.182.117 "cd /home/deployment/it-service && docker compose logs --tail=100 it-service-backend"
ssh -i C:\Users\ukbet\.ssh\id_ed25519 deployment@138.197.182.117 "cd /home/deployment/it-service && docker compose logs --tail=100 it-service-frontend"
```

## 12. Sorun giderme

- SSH baglantisi reddedilirse private key yerine public key kullanimini kontrol et.
- `docker compose` bulunamazsa Docker kurulumunu tekrar kontrol et.
- Actions secret'lari eksikse workflow deploy asamasinda durur.
- Uygulama portlari erisilemiyorsa `ufw` kurallarini kontrol et.

## Kisa akis

1. Sunucuyu hazirla.
2. `deployment` kullanicisini ve SSH key yetkisini ekle.
3. Docker kur.
4. GitHub Secrets'i tanimla.
5. `main`'e push et.
6. GitHub Actions ile CD'nin calistigini dogrula.
