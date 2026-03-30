# 1. Aşama: Build (Derleme)
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Önce bağımlılıkları indirir (Docker önbelleğini verimli kullanmak için)
COPY pom.xml .
RUN mvn dependency:go-offline

# Sonra kodları kopyalayıp projeyi derler
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Aşama: Run (Çalıştırma)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Build aşamasında oluşan jar dosyasını kopyalar
COPY --from=builder /app/target/*.jar app.jar

# Uygulamanın çalışacağı port
EXPOSE 8081

# Spring Boot'u ayağa kaldıran komut
ENTRYPOINT ["java", "-jar", "app.jar"]