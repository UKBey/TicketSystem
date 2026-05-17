.PHONY: help \
        up rebuild down logs ps restart \
        infra dev dev-backend dev-frontend \
        build build-backend build-frontend \
        test test-backend test-frontend \
        verify \
        sonar sonar-up sonar-down \
        lint install clean \
        gen gen-build gen-run \
        k8s-up k8s-down k8s-logs k8s-load-images k8s-render

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
	@echo    gen-build        - Generator JAR'ini derler
	@echo    gen-run          - Onceden derlenmiş JAR'i calistirir
	@echo.
	@echo  Kubernetes (kind + kustomize):
	@echo    k8s-up           - Tum stack'i kind cluster'a deploy eder (overlay: local)
	@echo    k8s-down         - kind cluster'i siler
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

gen-build:
	cd $(GENERATOR_DIR) && ..\$(BACKEND_DIR)\mvnw.cmd package -q -DskipTests

gen-run:
	java -jar $(GENERATOR_DIR)\target\data-generator-1.0.0.jar

# --- Kurulum ---

install:
	cd $(FRONTEND_DIR) && npm install

# --- Kubernetes (kind + kustomize) ---

# kind cluster adi ve overlay yolu — overlay disardan ezilebilir: make k8s-up OVERLAY=prod
KIND_CLUSTER ?= ticketsystem
K8S_OVERLAY  ?= k8s/overlays/local
K8S_NAMESPACE := ticketsystem
# Local kullanildiginda kind'a yuklenecek image listesi (CD'deki ile aynı isimler).
K8S_LOCAL_IMAGES := local/it-service-backend:latest \
                    local/llm-service:latest \
                    local/it-service-frontend:latest \
                    local/openldap-server:latest \
                    local/data-generator:latest

k8s-up:
	@kind get clusters | findstr /B /L /C:"$(KIND_CLUSTER)" >NUL 2>&1 || kind create cluster --name $(KIND_CLUSTER)
	kubectl --context kind-$(KIND_CLUSTER) apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	kubectl --context kind-$(KIND_CLUSTER) -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s
	kubectl --context kind-$(KIND_CLUSTER) kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone | kubectl --context kind-$(KIND_CLUSTER) apply -f -
	@echo.
	@echo Cluster hazirlaniyor. Pod durumu: kubectl -n $(K8S_NAMESPACE) get pods -w
	@echo ticketsystem.local hosts'da 127.0.0.1'e yonlendirilmis olmali.

k8s-down:
	kind delete cluster --name $(KIND_CLUSTER)

k8s-logs:
	kubectl -n $(K8S_NAMESPACE) logs -f deploy/$(s)

k8s-load-images:
	@for %%i in ($(K8S_LOCAL_IMAGES)) do kind load docker-image %%i --name $(KIND_CLUSTER)

k8s-render:
	kubectl kustomize $(K8S_OVERLAY) --load-restrictor=LoadRestrictionsNone

# --- Temizlik ---

clean:
	cd $(BACKEND_DIR) && mvnw.cmd clean
	if exist $(FRONTEND_DIR)\dist rmdir /s /q $(FRONTEND_DIR)\dist
