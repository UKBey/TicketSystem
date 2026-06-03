#!/usr/bin/env bash
# =============================================================================
# seed-roles.sh — LDAP'tan federe edilen seed kullanıcılara Keycloak realm
# rollerini idempotent şekilde atar. `keycloak-seeder` one-shot container'ı
# (quay.io/keycloak/keycloak imajı) tarafından çalıştırılır; `make seed-roles`
# ile elle de tetiklenebilir.
#
# - Roller LDAP girdisinden gelmez; gerçek/tek doğruluk kaynağı budur.
# - add-roles tekrar çalıştırmaya güvenlidir (mevcut atama no-op).
# - `get users -q username=...` LDAP kullanıcısını Keycloak'a import eder.
# =============================================================================
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
CONFIG=/tmp/kcadm.config
SERVER="${KC_URL:-http://keycloak-iam:8080/auth}"
REALM="${KC_REALM:-TicketSystemRealm}"
ADMIN_USER="${KC_ADMIN:-admin}"
ADMIN_PASS="${KC_ADMIN_PASSWORD:-admin}"

# uid → atanacak realm rolleri (boşlukla ayrılır)
SEED_USERS=(
  "customer:CUSTOMER"
  "agent:AGENT"
  "lead:LEAD_AGENT"
  "manager:MANAGER"
  "admin:ADMIN"
  "adminmanager:ADMIN MANAGER"
  "leadmanager:LEAD_AGENT MANAGER"
  "superadmin:ADMIN LEAD_AGENT MANAGER"
)

log() { echo "[seed-roles] $*"; }

# 1) Keycloak master realm'e giriş yapana kadar bekle (port açık ama realm
#    henüz hazır olmayabilir).
log "Keycloak bekleniyor: $SERVER"
for i in $(seq 1 60); do
  if "$KCADM" config credentials --config "$CONFIG" \
        --server "$SERVER" --realm master \
        --user "$ADMIN_USER" --password "$ADMIN_PASS" >/dev/null 2>&1; then
    log "Admin girişi başarılı."
    break
  fi
  [ "$i" -eq 60 ] && { log "HATA: Keycloak'a ulaşılamadı."; exit 1; }
  sleep 3
done

# 2) Hedef realm'in rolleri import edilene kadar bekle (realm import devam
#    ediyor olabilir).
log "Realm rolleri bekleniyor: $REALM"
for i in $(seq 1 40); do
  if "$KCADM" get "roles/ADMIN" -r "$REALM" --config "$CONFIG" >/dev/null 2>&1; then
    break
  fi
  [ "$i" -eq 40 ] && { log "HATA: '$REALM' realm rolleri görünmüyor."; exit 1; }
  sleep 3
done

# 3) Her seed kullanıcı için: federasyon import + rol atama.
rc=0
for entry in "${SEED_USERS[@]}"; do
  user="${entry%%:*}"
  roles="${entry#*:}"
  # LDAP kullanıcısını Keycloak'a import et (federasyon araması).
  if ! "$KCADM" get users -r "$REALM" --config "$CONFIG" \
        -q "username=$user" -q exact=true 2>/dev/null | grep -q "\"username\""; then
    log "UYARI: '$user' Keycloak'ta bulunamadı (LDAP'ta var mı? bootstrap?)."
    rc=1
    continue
  fi
  for role in $roles; do
    if "$KCADM" add-roles -r "$REALM" --config "$CONFIG" \
          --uusername "$user" --rolename "$role" >/dev/null 2>&1; then
      log "  $user <- $role"
    else
      log "  ! '$user' kullanıcısına '$role' atanamadı."
      rc=1
    fi
  done
done

[ "$rc" -eq 0 ] && log "Tüm seed rolleri atandı." || log "Bazı atamalar başarısız (yukarı bak)."
exit "$rc"
