#!/bin/bash
set -e

TEMPLATE="/ldap-seed/bootstrap.ldif.template"
OUTPUT_DIR="/ldap-seed/rendered"
OUTPUT="$OUTPUT_DIR/bootstrap.ldif"

echo "[ldap-init] Rendering LDAP bootstrap template..."

# Sablondaki degiskenleri kontrol et
for var_name in LDAP_CUSTOMER_PASSWORD LDAP_AGENT_PASSWORD LDAP_MANAGER_PASSWORD; do
  if [ -z "${!var_name:-}" ]; then
    echo "[ldap-init] HATA: Zorunlu degisken eksik: $var_name"
    exit 1
  fi
done

mkdir -p "$OUTPUT_DIR"

# sed ile sablonu render et (envsubst yerine — bu imajda envsubst yok)
# Pipe delimiter kullanarak ozel karakterleri kacistiriyoruz
sed \
  -e "s|\${LDAP_CUSTOMER_PASSWORD}|${LDAP_CUSTOMER_PASSWORD}|g" \
  -e "s|\${LDAP_AGENT_PASSWORD}|${LDAP_AGENT_PASSWORD}|g" \
  -e "s|\${LDAP_MANAGER_PASSWORD}|${LDAP_MANAGER_PASSWORD}|g" \
  "$TEMPLATE" > "$OUTPUT"

echo "[ldap-init] Bootstrap LDIF basariyla olusturuldu: $OUTPUT"
echo "[ldap-init] Icerik kontrol:"
cat "$OUTPUT"

# Orijinal entrypoint'i calistir
exec /container/tool/run "$@"
