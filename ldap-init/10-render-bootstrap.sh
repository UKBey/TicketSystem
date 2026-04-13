#!/usr/bin/env bash
set -euo pipefail

TEMPLATE="/container/service/slapd/assets/config/bootstrap/ldif/custom/bootstrap.ldif.template"
OUTPUT="/container/service/slapd/assets/config/bootstrap/ldif/custom/bootstrap.ldif"

if [ ! -f "$TEMPLATE" ]; then
  echo "[ldap-init] Template not found: $TEMPLATE"
  exit 1
fi

VARS='${LDAP_CUSTOMER_PASSWORD} ${LDAP_AGENT_PASSWORD} ${LDAP_MANAGER_PASSWORD}'
envsubst "$VARS" < "$TEMPLATE" > "$OUTPUT"

echo "[ldap-init] Rendered LDAP bootstrap file: $OUTPUT"
