.PHONY: help \
        up rebuild build-only down logs ps restart \
        up-prod down-prod logs-prod ps-prod config-prod \
        infra dev dev-backend dev-frontend dev-mobile \
        build build-backend build-frontend \
        test test-backend test-frontend \
        verify ci set-version \
        javadoc javadoc-backend javadoc-llm \
        sonar sonar-up sonar-down \
        lint install clean \
        gen gen-host gen-k8s gen-build gen-run \
        k8s-up k8s-down k8s-stop k8s-start k8s-logs k8s-build k8s-load-images k8s-render k8s-rebuild \
        k8s-ensure k8s-apply k8s-restart-all k8s-redeploy-kjar k8s-seed-roles _k8s-create _k8s-start

BACKEND_DIR  := it-service-backend
FRONTEND_DIR := it-service-frontend
MOBILE_DIR   := it-service-mobile
GENERATOR_DIR := data-generator

# .env dosyasini oku (varsa)
-include .env
export

# --- Platforma gore arac / komut farklari (Windows cmd.exe <-> POSIX sh) ---
# OS=Windows_NT sadece Windows'ta tanimlidir; Linux/macOS bos birakir -> else dalina duser.
# Yeni bir platform-bagimli komut gerektiginde once buraya bir degisken ekle,
# sonra hedeflerde $(DEGISKEN) olarak kullan.
ifeq ($(OS),Windows_NT)
  MVNW       := mvnw.cmd
  MVNW_UP    := ..\$(BACKEND_DIR)\mvnw.cmd
  NULL       := NUL
  ECHO_BLANK := echo.
  RM_DIST    := if exist $(FRONTEND_DIR)\dist rmdir /s /q $(FRONTEND_DIR)\dist
else
  MVNW       := ./mvnw
  MVNW_UP    := ../$(BACKEND_DIR)/mvnw
  NULL       := /dev/null
  ECHO_BLANK := echo ""
  RM_DIST    := rm -rf $(FRONTEND_DIR)/dist
endif

# Sadece altyapi servisleri (backend/frontend haric) -- local dev icin
INFRA_SERVICES := it-service-db openldap-server phpldapadmin keycloak-iam \
                  mailpit opensearch opensearch-dashboards otel-collector \
                  data-prepper kafka logstash kie-server redis

# NOT: Bu help metni hem cmd hem sh tarafindan echo edilir; o yuzden ( ) ' " ; & gibi
# kabukta ozel anlami olan karakterler kullanilmaz (sh bunlarda patlar). Windows'ta
# bosluklarla hizalama korunur; sh tek bosluga indirger ama icerik aynidir.
help:
	@echo Kullanim: make hedef
	@$(ECHO_BLANK)
	@echo  Docker - tam stack:
	@echo    up                    - Tum stacki Docker ile baslar
	@echo    rebuild               - Imajlari yeniden build edip baslar - kod degisince
	@echo    build-only s=servis   - Sadece belirtilen servisin imajini build eder
	@echo    down                  - Tum stacki durdurur
	@echo    logs                  - Tum servislerin loglarini izler
	@echo    logs s=servis         - Tek servisin loglarini izler - ornek: make logs s=keycloak-iam
	@echo    ps                    - Calisan containerlari listeler
	@echo    restart s=servis      - Tek servisi recreate eder - .env/compose degisikliklerini uygular - ornek: make restart s=it-service-backend
	@$(ECHO_BLANK)
	@echo  Docker - production overlay - base + docker-compose.prod.yaml:
	@echo    up-prod               - Prod ayarlariyla baslar - pinli imaj, sadece nginx 80/443, dev araclari yok
	@echo    down-prod             - Prod stacki durdurur
	@echo    logs-prod s=servis    - Prod servis loglarini izler
	@echo    ps-prod               - Prod containerlari listeler
	@echo    config-prod           - Prod merged compose ciktisini gosterir - dogrulama icin
	@$(ECHO_BLANK)
	@echo  Lokal Gelistirme - hot-reload:
	@echo    infra            - Sadece altyapi containerlarini baslar - DB, Keycloak, jBPM
	@echo    dev              - Backend ve Frontendi localde baslar - ayri pencereler
	@echo    dev-backend      - Sadece Backendi local baslar - Spring Boot :8081
	@echo    dev-frontend     - Sadece Frontendi local baslar - Vite :5173
	@echo    dev-mobile       - Mobil uygulamayi baslar - Expo dev server
	@$(ECHO_BLANK)
	@echo  Build:
	@echo    build            - Backend ve Frontendi derler
	@echo    build-backend    - Backendi Maven ile derler - jar
	@echo    build-frontend   - Frontendi Vite ile derler - dist
	@$(ECHO_BLANK)
	@echo  Test:
	@echo    test             - Tum testleri calistirir
	@echo    test-backend     - Backend testleri - Maven
	@echo    test-frontend    - Frontend testleri - Vitest
	@echo    ci               - Continuous Integration: verify + test-frontend + lint
	@echo    set-version V=x.y.z - Proje surumunu tum dosyalarda senkronlar - kjar haric - ornek: make set-version V=1.2.0
	@$(ECHO_BLANK)
	@echo  Kapsam - Coverage:
	@echo    verify           - Backend testlerini calistirir ve JaCoCo HTML raporu uretir
	@echo                       Rapor: it-service-backend/target/site/jacoco/index.html
	@$(ECHO_BLANK)
	@echo  Dokumantasyon - Javadoc:
	@echo    javadoc          - Tum servisler icin Javadoc HTML uretir - backend + llm-service
	@echo    javadoc-backend  - Sadece backend Javadocunu uretir
	@echo                       Rapor: it-service-backend/target/reports/apidocs/index.html
	@echo    javadoc-llm      - Sadece llm-service Javadocunu uretir
	@echo                       Rapor: llm-service/target/reports/apidocs/index.html
	@$(ECHO_BLANK)
	@echo  Kod Kalitesi - SonarQube:
	@echo    sonar-up         - SonarQube containerini baslatir - http://localhost:9000
	@echo    sonar-down       - SonarQube containerini durdurur
	@echo    sonar            - Kodu analiz edip SonarQubea gonderir
	@echo                       Token .env dosyasindaki SONAR_TOKEN degiskeninden okunur
	@$(ECHO_BLANK)
	@echo  Veri Uretici - Data Generator:
	@echo    gen              - Generatoru Docker container icinde calistirir - make up ile KALKMAZ
	@echo    gen-host         - Generatoru host JVM uzerinde calistirir - Dockersiz
	@echo    gen-k8s          - k8s ortaminda: port-forward + gen-host
	@echo    gen-build        - Generator JARini derler - host
	@echo    gen-run          - Onceden derlenmis JARi calistirir - host
	@echo    seed-roles       - Seed kullanicilara Keycloak rollerini atar - COMPOSE - make up sonrasi (k8s icin: k8s-seed-roles)
	@$(ECHO_BLANK)
	@echo  Kubernetes - kind + kustomize:
	@echo    k8s-rebuild      - TEK KOMUT: cluster yoksa olusturur, kapaliysa baslatir,
	@echo                       tum imajlari build edip kinde yukler, manifestleri
	@echo                       apply eder, podlari yenileyip kjari tekrar deploy eder.
	@echo                       PVC verisi korunur.
	@echo    k8s-build        - Tum k8s imajlarini build eder
	@echo    k8s-up           - Tum stacki kind clustera ilk kez deploy eder - overlay: local
	@echo    k8s-down         - kind clusteri SILER - PVCler dahil tum data gider
	@echo    k8s-stop         - Cluster'i SILMEDEN durdurur - veriler korunur - devam: k8s-start
	@echo    k8s-start        - Durdurulmus cluster'i yeniden baslatir - veriler korunur
	@echo    k8s-logs s=deploy - Tek deploymentin loglarini izler
	@echo    k8s-load-images  - Lokal Docker imajlarini kinde yukler
	@echo    k8s-render       - Manifestleri stdouta render eder - debug
	@echo    k8s-seed-roles   - Seed kullanicilara Keycloak rollerini atar - k8s (compose icin seed-roles)
	@$(ECHO_BLANK)
	@echo  Diger:
	@echo    lint             - Frontend ESLint kontrolu
	@echo    install          - Frontend bagimliliklarini yukler - npm install
	@echo    clean            - Derleme ciktilarini temizler

# --- Docker: Tam Stack ---
# --remove-orphans: dev<->prod gecisinde olusan artik (orphan) container'lari temizler.
# Prod kumesi dev-only servisleri (mailpit, phpldapadmin, dashboards, sonarqube) icermez;
# bayrak olmadan bunlar orphan kalip eski network referansiyla sonraki rebuild'i bozar
# ("network <hash> not found"). Bayrak her up/rebuild'de bu artiklari kaldirir.

up:
	docker compose up -d --remove-orphans

rebuild:
	docker compose up -d --build --remove-orphans

build-only:
	docker compose up --build -d --no-deps $(s)

down:
	docker compose down

logs:
	docker compose logs -f $(s)

ps:
	docker compose ps

# `docker compose restart` .env'i YENIDEN OKUMAZ — container'i ayni config ile bounce eder,
# bu yuzden secret/env degisiklikleri uygulanmaz. `up -d --force-recreate` container'i guncel
# .env/compose ile yeniden olusturur (env degisikligi olmasa bile bounce eder). --no-deps:
# bagimliliklari (db/keycloak/kjar-deploy) yeniden degerlendirmez. Imaj DERLEMEZ (rebuild'den hizli).
restart:
	docker compose up -d --force-recreate --no-deps $(s)

# --- Docker: Production overlay ---
# Prod = base (docker-compose.yaml) + docker-compose.prod.yaml. -f verildiginde Compose
# dev override'i (docker-compose.override.yaml) OTOMATIK YUKLEMEZ, boylece prod run yalnizca
# base + prod ayarlarini alir: pinli imaj (pull), sadece nginx 80/443, dev araclari yok,
# Keycloak production modu, restart politikalari. Imajlar registry'de hazir olmali (CD
# pipeline main'de Docker Hub'a push eder; DOCKERHUB_USERNAME/IMAGE_TAG ile secilir) ve prod
# .env'i hazirlanmali (KC_HOSTNAME_URL, gercek SMTP, guclu secret'lar).
PROD_COMPOSE := docker compose -f docker-compose.yaml -f docker-compose.prod.yaml

up-prod:
	$(PROD_COMPOSE) up -d --remove-orphans

down-prod:
	$(PROD_COMPOSE) down --remove-orphans

logs-prod:
	$(PROD_COMPOSE) logs -f $(s)

ps-prod:
	$(PROD_COMPOSE) ps

config-prod:
	$(PROD_COMPOSE) config

# --- Lokal Gelistirme ---

infra:
	docker compose up -d $(INFRA_SERVICES)

# Backend + Frontend'i ayri pencerelerde baslatir.
# Windows: cmd /k ile iki yeni konsol. Linux/macOS: bulunan terminal emulatorunu
# (gnome-terminal/konsole/xterm) dener; hicbiri yoksa elle calistirma talimati verir.
ifeq ($(OS),Windows_NT)
dev:
	start "Backend"  cmd /k "cd $(BACKEND_DIR) && $(MVNW) spring-boot:run"
	start "Frontend" cmd /k "cd $(FRONTEND_DIR) && npm run dev"
else
dev:
	@if command -v gnome-terminal >$(NULL) 2>&1; then \
		gnome-terminal --title=Backend  -- bash -c "cd $(BACKEND_DIR) && ./mvnw spring-boot:run; exec bash"; \
		gnome-terminal --title=Frontend -- bash -c "cd $(FRONTEND_DIR) && npm run dev; exec bash"; \
	elif command -v konsole >$(NULL) 2>&1; then \
		konsole -e bash -c "cd $(BACKEND_DIR) && ./mvnw spring-boot:run; exec bash" & \
		konsole -e bash -c "cd $(FRONTEND_DIR) && npm run dev; exec bash" & \
	elif command -v xterm >$(NULL) 2>&1; then \
		xterm -T Backend  -e "cd $(BACKEND_DIR) && ./mvnw spring-boot:run" & \
		xterm -T Frontend -e "cd $(FRONTEND_DIR) && npm run dev" & \
	else \
		echo "Terminal emulatoru bulunamadi. Iki ayri terminalde su komutlari calistir:"; \
		echo "  make dev-backend"; \
		echo "  make dev-frontend"; \
	fi
endif

dev-backend:
	cd $(BACKEND_DIR) && $(MVNW) spring-boot:run

dev-frontend:
	cd $(FRONTEND_DIR) && npm run dev

dev-mobile:
	cd $(MOBILE_DIR) && npx expo start

# --- Build ---

build: build-backend build-frontend

build-backend:
	cd $(BACKEND_DIR) && $(MVNW) clean package -DskipTests

build-frontend:
	cd $(FRONTEND_DIR) && npm run build

# --- Test ---

test: test-backend test-frontend

test-backend:
	cd $(BACKEND_DIR) && $(MVNW) test

test-frontend:
	cd $(FRONTEND_DIR) && npm test

# --- Kalite ---
# Continuous Integration: backend (verify = unit + integration + jacoco) + frontend testleri + lint
ci:
	$(MAKE) verify
	$(MAKE) test-frontend
	$(MAKE) lint
	@$(ECHO_BLANK)
	@echo ================================================================
	@echo   CI PASSED -- backend verify + frontend tests + lint all green.
	@echo   Safe to push.
	@echo ================================================================

lint:
	cd $(FRONTEND_DIR) && npm run lint

verify:
	cd $(BACKEND_DIR) && $(MVNW) verify

# --- Surum ---
# Proje surumunu 7 yerde senkronlar: 3 pom <version>, 2 package.json, Expo app.json,
# OpenAPI info.version. kjar (ayri deployment koordinati) ve npm lockfile'lar (kozmetik;
# npm ci zorlamaz, npm install zaten senkronlar) HARIC. Asil surum kaynagi git tag'idir.
# Cross-platform kalmasi icin Node ile (frontend/mobile zaten Node gerektirir).
# Bir desen bulunamazsa hicbir sey yazmaz ve FAIL ile cikar. Cikti yalnizca OK / FAIL.
set-version:
	@node -e "const v=process.argv[1],fs=require('fs');if(!/^[0-9]+\.[0-9]+\.[0-9]/.test(v||'')){console.error('FAIL: gecersiz surum (V=x.y.z bekleniyor)');process.exit(1)}const J=[['it-service-backend/pom.xml',/(<artifactId>it-service-backend<\/artifactId>\s*<version>)[^<]+(<\/version>)/],['llm-service/pom.xml',/(<artifactId>llm-service<\/artifactId>\s*<version>)[^<]+(<\/version>)/],['data-generator/pom.xml',/(<artifactId>data-generator<\/artifactId>\s*<version>)[^<]+(<\/version>)/],['it-service-frontend/package.json',/(\x22version\x22\s*:\s*\x22)[^\x22]+(\x22)/],['it-service-mobile/package.json',/(\x22version\x22\s*:\s*\x22)[^\x22]+(\x22)/],['it-service-mobile/app.json',/(\x22version\x22\s*:\s*\x22)[^\x22]+(\x22)/],['it-service-backend/src/main/java/com/ticketsystem/it_service_backend/config/OpenApiConfig.java',/(\.version\(\x22)[^\x22]+(\x22\))/]];let ok=true;for(const[f,re]of J){let s;try{s=fs.readFileSync(f,'utf8')}catch(e){console.error('FAIL: okunamadi '+f);ok=false;continue}if(!re.test(s)){console.error('FAIL: desen yok '+f);ok=false;continue}fs.writeFileSync(f,s.replace(re,(m,a,b)=>a+v+b))}if(!ok)process.exit(1);console.log('OK: surum '+v)" $(V)

# --- Javadoc ---
# Plugin yapilandirmasi (doclint=none) pom.xml'de tanimli; ekstra flag gerekmez.

javadoc: javadoc-backend javadoc-llm
	@$(ECHO_BLANK)
	@echo ================================================================
	@echo  Javadoc uretildi:
	@echo    Backend     : $(BACKEND_DIR)/target/reports/apidocs/index.html
	@echo    LLM service : llm-service/target/reports/apidocs/index.html
	@echo ================================================================

javadoc-backend:
	cd $(BACKEND_DIR) && $(MVNW) javadoc:javadoc

javadoc-llm:
	cd llm-service && $(MVNW_UP) javadoc:javadoc

# --- SonarQube ---

sonar-up:
	docker compose up -d sonarqube-db sonarqube
	@echo SonarQube baslatiliyor... Hazir olunca http://localhost:9000 adresini ac.
	@echo Ilk giris: admin / admin

sonar-down:
	docker compose stop sonarqube sonarqube-db

sonar:
	cd $(BACKEND_DIR) && $(MVNW) verify sonar:sonar -Dsonar.token=$(SONAR_TOKEN)

# --- Veri Uretici ---

# Generator'u Docker container icinde calistirir -- eski host `make gen` ile ayni is,
# ama host'ta Java/Maven GEREKTIRMEZ. compose profili: tools.
# `make up` / `make rebuild` / `docker compose up` ile AYAGA KALKMAZ.
# --no-deps: ZATEN AYAKTA olan stack'e baglanir; stack'i yonetmez/yeniden baslatmaz
# (kjar-deploy gibi tek-seferlik job'lar tekrar tetiklenmez). Once `make up` gerekir.
gen: $(GENERATOR_DIR)/users.json
	docker compose build data-generator
	docker compose run --rm --no-deps data-generator

# Shell-bagimsiz on-kosul: users.json (gizli, zorunlu) yoksa make asagidaki kurali
# calistirip durur -- cmd, sh ve Linux'ta ayni davranir (cmd/sh ayrimi gerekmez).
# Dosya varsa (regular file) bu kural hic tetiklenmez.
$(GENERATOR_DIR)/users.json:
	@echo data-generator/users.json bulunamadi -- once data-generator/users.example.json dosyasini users.json olarak kopyalayin
	@exit 1

# Generator'u host JVM uzerinde calistirir (Dockersiz) -- gen-k8s ve dogrudan JVM icin.
gen-host: gen-build gen-run

# k8s: DB ve Keycloak admin portu cluster ici oldugu icin once port-forward,
# sonra host JVM ile (gen-host). Keycloak admin ingress'ten SUNULMAZ (yalniz
# /auth/realms + /auth/resources) — generator'in Admin REST cagrilari dogrudan
# 8080 port-forward'una gider. Bu target sadece talimat verir.
gen-k8s:
	@echo ============================================================
	@echo  K8s ortaminda Generator Calistirma -- 3 ADIM
	@echo ============================================================
	@echo  1. AYRI BIR terminal penceresinde sunu calistir:
	@echo        kubectl -n $(K8S_NAMESPACE) port-forward svc/it-service-db 5432:5432
	@echo  2. IKINCI bir ayri pencerede (Keycloak Admin REST icin):
	@echo        kubectl -n $(K8S_NAMESPACE) port-forward svc/keycloak-iam 8080:8080
	@echo  3. Sonra bu pencerede:
	@echo        make gen-host
	@echo  Pencereler acik kalmali -- bittikten sonra Ctrl+C ile kapat.
	@echo ============================================================

gen-build:
	cd $(GENERATOR_DIR) && $(MVNW_UP) package -q -DskipTests

# Jar adi pom.xml <finalName> ile surumsuz sabitlendi — set-version bunu etkilemez.
gen-run:
	java -jar $(GENERATOR_DIR)/target/data-generator.jar

# --- Seed kullanici rolleri ---

# LDAP'tan federe edilen seed kullanicilara (customer/agent/lead/manager/admin/
# adminmanager/leadmanager/superadmin) Keycloak realm rollerini atar. Docker
# container icinde kcadm ile calisir; idempotenttir. `make up` ile AYAGA KALKMAZ.
# --no-deps: ZATEN AYAKTA olan stack'e baglanir. Once `make up` gerekir.
seed-roles:
	docker compose run --rm --no-deps keycloak-seeder

# --- Kurulum ---

install:
	cd $(FRONTEND_DIR) && npm install

# --- Kubernetes (kind + kustomize) ---

# kind cluster adi ve overlay yolu -- overlay disardan ezilebilir: make k8s-up K8S_OVERLAY=k8s/overlays/prod
KIND_CLUSTER  ?= ticketsystem
K8S_OVERLAY   ?= k8s/overlays/local
K8S_NAMESPACE ?= ticketsystem
# Local kullanildiginda kind'a yuklenecek image listesi (CD'deki ile ayni isimler).
K8S_LOCAL_IMAGES := local/it-service-backend:latest \
                    local/llm-service:latest \
                    local/it-service-frontend:latest \
                    local/openldap-server:latest \
                    local/keycloak-iam:latest \
                    local/kie-server:latest

# Cluster / container "var mi" kontrolleri -- cikti yutulur, sadece exit code onemli.
# Windows: findstr + NUL. Linux/macOS: grep -qx + /dev/null.
ifeq ($(OS),Windows_NT)
  HAS_CLUSTER     := kind get clusters 2>$(NULL) | findstr /B /L /C:"$(KIND_CLUSTER)" >$(NULL) 2>&1
  CLUSTER_RUNNING := docker inspect -f "{{.State.Running}}" $(KIND_CLUSTER)-control-plane 2>$(NULL) | findstr /B /L /C:"true" >$(NULL) 2>&1
else
  HAS_CLUSTER     := kind get clusters 2>$(NULL) | grep -qx "$(KIND_CLUSTER)"
  CLUSTER_RUNNING := docker inspect -f "{{.State.Running}}" $(KIND_CLUSTER)-control-plane 2>$(NULL) | grep -qx "true"
endif

k8s-up:
	@$(HAS_CLUSTER) || kind create cluster --name $(KIND_CLUSTER) --config k8s/kind-config.yaml
	kubectl --context kind-$(KIND_CLUSTER) apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	kubectl --context kind-$(KIND_CLUSTER) -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -
	@$(ECHO_BLANK)
	@echo Cluster hazirlaniyor. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w
	@echo Site: http://localhost  -- hosts dosyasina giris GEREKMIYOR, kind-config.yaml 80 portunu hosta aciyor

k8s-down:
	kind delete cluster --name $(KIND_CLUSTER)

# Cluster'i SILMEDEN durdurur: node container'i durur ama PVC + etcd state korunur
# (veri GITMEZ). Devam: make k8s-start. (make k8s-down ise cluster'i + TUM veriyi siler.)
k8s-stop:
	docker stop $(KIND_CLUSTER)-control-plane

# Durdurulmus cluster'i yeniden baslatir (veriler korunur; pod'lar otomatik toparlanir).
k8s-start:
	docker start $(KIND_CLUSTER)-control-plane
	@echo Cluster baslatildi. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w

k8s-logs:
	kubectl -n $(K8S_NAMESPACE) logs -f deploy/$(s)

# DOCKERHUB_USERNAME=local override: docker-compose.yaml image tag'lerini
# local/... ile uretsin diye. K8s deployment'lari local/... image arar.
k8s-build: DOCKERHUB_USERNAME := local
k8s-build:
	docker compose build openldap-server it-service-backend llm-service it-service-frontend
	docker build -t local/keycloak-iam:latest -f Dockerfile-keycloak .
	docker build -t local/kie-server:latest  -f Dockerfile-kie  .

k8s-load-images:
	kind load docker-image $(K8S_LOCAL_IMAGES) --name $(KIND_CLUSTER)

k8s-render:
	kubectl kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone

# Compose'daki 'make rebuild' ile ayni semantik: data silmeden tum guncellemeleri uygular.
k8s-rebuild: k8s-ensure k8s-build k8s-load-images k8s-apply k8s-restart-all k8s-redeploy-kjar
	@$(ECHO_BLANK)
	@echo ================================================================
	@echo  k8s-rebuild tamam. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w
	@echo  Site: http://localhost
	@echo ================================================================

# Cluster yoksa olustur, durmussa baslat. Var ve calisiyorsa hicbir sey yapma.
k8s-ensure:
	@$(HAS_CLUSTER) || $(MAKE) _k8s-create
	@$(CLUSTER_RUNNING) || $(MAKE) _k8s-start

_k8s-create:
	@echo Cluster yok, olusturuluyor...
	kind create cluster --name $(KIND_CLUSTER) --config k8s/kind-config.yaml
	kubectl --context kind-$(KIND_CLUSTER) apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	kubectl --context kind-$(KIND_CLUSTER) -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

_k8s-start:
	@echo Cluster container kapali, baslatiliyor...
	docker start $(KIND_CLUSTER)-control-plane
	kubectl --context kind-$(KIND_CLUSTER) wait --for=condition=Ready node --all --timeout=180s

k8s-apply:
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -

# Tum deployment ve statefulset'leri rolling restart eder (yeni image SHA'lari icin).
k8s-restart-all:
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) rollout restart deployment
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) rollout restart statefulset

# KIE Server H2 (in-memory) kullaniyor; restart sonrasi container registration kayboluyor.
k8s-redeploy-kjar:
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) delete job kjar-deploy --ignore-not-found
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -

# Seed kullanicilara Keycloak rollerini atar (compose `make seed-roles` k8s karsiligi).
# DIKKAT: `make seed-roles` compose icindir; k8s'te BUNU kullan. Job idempotent; eskiyi
# silip yeniden olusturarak tekrar calistirir.
k8s-seed-roles:
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) delete job seed-roles --ignore-not-found
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -
	kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) wait --for=condition=complete job/seed-roles --timeout=180s

# --- Temizlik ---

clean:
	cd $(BACKEND_DIR) && $(MVNW) clean
	$(RM_DIST)
