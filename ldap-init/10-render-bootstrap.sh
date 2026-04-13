#!/usr/bin/env bash
set -euo pipefail

TEMPLATE="/container/service/slapd/assets/config/bootstrap/ldif/custom/bootstrap.ldif.template"
OUTPUT="/container/service/slapd/assets/config/bootstrap/ldif/custom/bootstrap.ldif"

if [ ! -f "$TEMPLATE" ]; then
  echo "[ldap-init] Template not found: $TEMPLATE"
  exit 1
fi

required_vars=(LDAP_CUSTOMER_PASSWORD LDAP_AGENT_PASSWORD LDAP_MANAGER_PASSWORD)
for var_name in "${required_vars[@]}"; do
  if [ -z "${!var_name:-}" ]; then
    echo "[ldap-init] Missing required variable: $var_name"
    exit 1
  fi
done

# sed replacement icin olasi delimiter karakterlerini kacisla
cust_pw_escaped=$(printf '%s' "$LDAP_CUSTOMER_PASSWORD" | sed 's/[&|]/\\&/g')
agent_pw_escaped=$(printf '%s' "$LDAP_AGENT_PASSWORD" | sed 's/[&|]/\\&/g')
manager_pw_escaped=$(printf '%s' "$LDAP_MANAGER_PASSWORD" | sed 's/[&|]/\\&/g')

sed \
  -e "s|\${LDAP_CUSTOMER_PASSWORD}|$cust_pw_escaped|g" \
  -e "s|\${LDAP_AGENT_PASSWORD}|$agent_pw_escaped|g" \
  -e "s|\${LDAP_MANAGER_PASSWORD}|$manager_pw_escaped|g" \
  "$TEMPLATE" > "$OUTPUT"

echo "[ldap-init] Rendered LDAP bootstrap file: $OUTPUT"
