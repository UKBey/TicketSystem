.PHONY: help \
        up rebuild down logs ps restart \
        infra dev dev-backend dev-frontend \
        build build-backend build-frontend \
        test test-backend test-frontend \
        verify \
        sonar sonar-up sonar-down \
        lint install clean \
        gen gen-k8s gen-build gen-run \
        k8s-up k8s-down k8s-logs k8s-build k8s-load-images k8s-render k8s-rebuild \
        k8s-ensure k8s-apply k8s-restart-all k8s-redeploy-kjar _k8s-create _k8s-start

BACKEND_DIR  := it-service-backend
FRONTEND_DIR := it-service-frontend
GENERATOR_DIR := data-generator

# .env dosyasini oku (varsa)
-include .env
export

# Sadece altyapı servisleri (backend/frontend hariç) — local dev için
INFRA_SERVICES := it-service-db openldap-server phpldapadmin keycloak-iam \
                  mailpit opensearch opensearch-dashboards otel-collector \
                  kafka logstash jbpm-db kie-server redis

help:
	@echo Kullanim: make [komut]
	@echo.
	@echo  Docker (tam stack):
	@echo    up               		- Tum stack'i Docker ile baslar  (eskiden: docker compose up)
	@echo    rebuild          		- Image'lari yeniden build edip baslar (kod degisince)
	@echo    build-only s=servis  	- Sadece belirtilen servisin image'ini build eder
	@echo    down             		- Tum stack'i durdurur
	@echo    logs             		- Tum servislerin loglarini izler
	@echo    logs s=servis    		- Tek servisin loglarini izler    (ornek: make logs s=keycloak-iam)
	@echo    ps               		- Calisan container'lari listeler
	@echo    restart s=servis 		- Tek servisi yeniden baslatir    (ornek: make restart s=it-service-db)
	@echo.
	@echo  Lokal Gelistirme (hot-reload):
	@echo    infra            - Sadece altyapi container'larini baslar (DB, Keycloak, jBPM...)
	@echo    dev              - Backend ve Frontend'i local'de baslar (ayri pencereler)
	@echo    dev-backend      - Sadece Backend'i local baslar (Spring Boot :8081)
	@echo    dev-frontend     - Sadece Frontend'i local baslar (Vite :5173)
	@echo.
	@echo  Build:
	@echo    build            - Backend ve Frontend'i derler
	@echo    build-backend    - Backend'i Maven ile derler (jar)
	@echo    build-frontend   - Frontend'i Vite ile derler (dist/)
	@echo.
	@echo  Test:
	@echo    test             - Tum testleri calistirir
	@echo    test-backend     - Backend testleri (Maven)
	@echo    test-frontend    - Frontend testleri (Vitest)
	@echo    ci			   	  - Continuous Integration: verify + test-frontend + lint
	@echo.
	@echo  Kapsam (Coverage):
	@echo    verify           - Backend testlerini calistirir ve JaCoCo HTML raporu uretir
	@echo                       Rapor: it-service-backend/target/site/jacoco/index.html
	@echo.
	@echo  Kod Kalitesi (SonarQube):
	@echo    sonar-up         - SonarQube container'ini baslatir (http://localhost:9000)
	@echo    sonar-down       - SonarQube container'ini durdurur
	@echo    sonar            - Kodu analiz edip SonarQube'a gonderir
	@echo                       Token .env dosyasindaki SONAR_TOKEN degiskeninden okunur
	@echo.
	@echo  Veri Uretici (Data Generator):
	@echo    gen              - Generator'u derler ve calistirir (build + run)
	@echo    gen-k8s          - k8s ortaminda: port-forward + gen-build + gen-run
	@echo    gen-build        - Generator JAR'ini derler
	@echo    gen-run          - Onceden derlenmiş JAR'i calistirir
	@echo.
	@echo  Kubernetes (kind + kustomize):
	@echo    k8s-rebuild      - TEK KOMUT: cluster yoksa olusturur, kapaliysa baslatir,
	@echo                       tum image'lari build edip kind'a yukler, manifest'leri
	@echo                       apply eder, pod'lari yenileyip kjar'i tekrar deploy eder.
	@echo                       PVC verisi korunur (compose'daki 'make rebuild' karsiligi).
	@echo    k8s-build        - Tum k8s image'larini build eder (4 compose servisi + keycloak + kie)
	@echo    k8s-up           - Tum stack'i kind cluster'a ilk kez deploy eder (overlay: local)
	@echo    k8s-down         - kind cluster'i SILER (PVC'ler dahil tum data gider)
	@echo    k8s-logs s=deploy - Tek deployment'in loglarini izler
	@echo    k8s-load-images  - Lokal Docker image'larini kind'a yukler
	@echo    k8s-render       - Manifest'leri stdout'a render eder (debug)
	@echo.
	@echo  Diger:
	@echo    lint             - Frontend ESLint kontrolu
	@echo    install          - Frontend bagimliliklerini yukler (npm install)
	@echo    clean            - Derleme ciktilarini temizler

# --- Docker: Tam Stack ---

up:
	docker compose up -d

rebuild:
	docker compose up -d --build

build-only:
	docker compose up --build -d --no-deps $(s)

down:
	docker compose down

logs:
	docker compose logs -f $(s)

ps:
	docker compose ps

restart:
	docker compose restart $(s)

# --- Lokal Geliştirme ---

infra:
	docker compose up -d $(INFRA_SERVICES)

dev:
	start "Backend"  cmd /k "cd $(BACKEND_DIR) && mvnw.cmd spring-boot:run"
	start "Frontend" cmd /k "cd $(FRONTEND_DIR) && npm run dev"

dev-backend:
	cd $(BACKEND_DIR) && mvnw.cmd spring-boot:run

dev-frontend:
	cd $(FRONTEND_DIR) && npm run dev

# --- Build ---

build: build-backend build-frontend

build-backend:
	cd $(BACKEND_DIR) && mvnw.cmd clean package -DskipTests

build-frontend:
	cd $(FRONTEND_DIR) && npm run build

# --- Test ---

test: test-backend test-frontend

test-backend:
	cd $(BACKEND_DIR) && mvnw.cmd test

test-frontend:
	cd $(FRONTEND_DIR) && npm test

# --- Kalite ---
ci: # Continuous Integration: backend (verify = unit + integration + jacoco) + frontend tests + lint
	make verify
	make test-frontend
	make lint
	@echo.
	@echo ================================================================
	@echo   CI PASSED -- backend verify + frontend tests + lint all green.
	@echo   Safe to push.
	@echo ================================================================

lint:
	cd $(FRONTEND_DIR) && npm run lint

verify:
	cd $(BACKEND_DIR) && mvnw.cmd verify

# --- SonarQube ---


sonar-up:
	docker compose up -d sonarqube-db sonarqube
	@echo SonarQube baslatiliyor... Hazir olunca http://localhost:9000 adresini ac.
	@echo Ilk giris: admin / admin

sonar-down:
	docker compose stop sonarqube sonarqube-db

sonar:
	cd $(BACKEND_DIR) && mvnw.cmd verify sonar:sonar -Dsonar.token=$(SONAR_TOKEN)

# --- Veri Üretici ---

gen: gen-build gen-run

# Generator host'tan calisir ve API icin http://localhost, DateBackfiller icin
# jdbc:postgresql://localhost:5432/ticketdb adreslerini kullanir. K8s ortaminda
# Postgres host'a kapali oldugu icin port-forward gerekiyor. Bu target ayri bir
# pencerede port-forward baslatir, sonra gen-build + gen-run akisini calistirir.
# Pencereyi kullanici manuel kapatir (Ctrl+C).
gen-k8s:
	start "PortForward postgres" cmd /k "kubectl -n $(K8S_NAMESPACE) port-forward svc/it-service-db 5432:5432"
	@echo Port-forward baslatildi (ayri pencerede). 3 sn bekliyor...
	@ping -n 4 127.0.0.1 >NUL
	$(MAKE) gen

gen-build:
	cd $(GENERATOR_DIR) && ..\$(BACKEND_DIR)\mvnw.cmd package -q -DskipTests

gen-run:
	java -jar $(GENERATOR_DIR)\target\data-generator-1.0.0.jar

# --- Kurulum ---

install:
	cd $(FRONTEND_DIR) && npm install

# --- Kubernetes (kind + kustomize) ---

# kind cluster adi ve overlay yolu — overlay disardan ezilebilir: make k8s-up OVERLAY=prod
KIND_CLUSTER  ?= ticketsystem
K8S_OVERLAY   ?= k8s/overlays/local
K8S_NAMESPACE ?= ticketsystem
# Local kullanildiginda kind'a yuklenecek image listesi (CD'deki ile aynı isimler).
K8S_LOCAL_IMAGES := local/it-service-backend:latest \
                    local/llm-service:latest \
                    local/it-service-frontend:latest \
                    local/openldap-server:latest \
                    local/keycloak-iam:latest \
                    local/kie-server:latest

k8s-up:
	@kind get clusters | findstr /B /L /C:"$(KIND_CLUSTER)" >NUL 2>&1 || kind create cluster --name $(KIND_CLUSTER) --config k8s/kind-config.yaml
	kubectl --context kind-$(KIND_CLUSTER) apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	kubectl --context kind-$(KIND_CLUSTER) -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -
	@echo.
	@echo Cluster hazirlaniyor. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w
	@echo Site: http://localhost  (hosts dosyasina giris GEREKMIYOR; kind-config.yaml 80'i host'a aciyor)

k8s-down:
	kind delete cluster --name $(KIND_CLUSTER)

k8s-logs:
	kubectl -n $(K8S_NAMESPACE) logs -f deploy/$(s)

# DOCKERHUB_USERNAME=local override: docker-compose.yaml image tag'lerini
# `${DOCKERHUB_USERNAME:-local}/...` ile uretiyor. Kullanicinin .env'inde gercek
# kullanici adi olsa bile (ornegin push icin), k8s deployment'lari `local/...`
# image arar. Bu target'a ozel override ile compose `local/...` tag'liyor.
k8s-build: DOCKERHUB_USERNAME := local
k8s-build:
	docker compose build openldap-server it-service-backend llm-service it-service-frontend
	docker build -t local/keycloak-iam:latest -f Dockerfile-keycloak .
	docker build -t local/kie-server:latest  -f Dockerfile-kie  .

k8s-load-images:
	kind load docker-image $(K8S_LOCAL_IMAGES) --name $(KIND_CLUSTER)

k8s-render:
	kubectl kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone

# Compose'daki `make rebuild` ile ayni semantik: data silmeden tum guncellemeleri
# uygular. Bilgisayar yeni acilmissa kind container'ini baslatir; cluster hic
# yoksa olusturur. PVC'ler (postgres, ldap, redis, kafka, opensearch) korunur.
k8s-rebuild: k8s-ensure k8s-build k8s-load-images k8s-apply k8s-restart-all k8s-redeploy-kjar
	@echo.
	@echo ================================================================
	@echo  k8s-rebuild tamam. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w
	@echo  Site: http://localhost
	@echo ================================================================

# Cluster yoksa olustur, durmussa baslat. Var ve calisiyorsa hicbir sey yapma.
k8s-ensure:
	@kind get clusters 2>NUL | findstr /B /L /C:"$(KIND_CLUSTER)" >NUL 2>&1 || $(MAKE) _k8s-create
	@docker inspect -f "{{.State.Running}}" $(KIND_CLUSTER)-control-plane 2>NUL | findstr /B /L /C:"true" >NUL 2>&1 || $(MAKE) _k8s-start

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

# Tum deployment ve statefulset'leri rolling restart eder — yeni image SHA'lar
# kind'a yuklendi, restart pod'lari yeni image ile yeniden baslatir.
k8s-restart-all:
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) rollout restart deployment
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) rollout restart statefulset

# KIE Server H2 (in-memory) kullaniyor; restart sonrasi container registration
# kayboluyor. Job'u silip yeniden olusturmak kjar'i tekrar kayit eder.
k8s-redeploy-kjar:
	-kubectl --context kind-$(KIND_CLUSTER) -n $(K8S_NAMESPACE) delete job kjar-deploy --ignore-not-found
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -

# --- Temizlik ---

clean:
	cd $(BACKEND_DIR) && mvnw.cmd clean
	if exist $(FRONTEND_DIR)\dist rmdir /s /q $(FRONTEND_DIR)\dist
