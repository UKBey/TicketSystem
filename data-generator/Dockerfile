# 1. Asama: Build — maven-shade ile calistirilabilir uber-jar uretilir.
# Java 21 toolchain, Java 17 hedef bytecode (pom.xml maven.compiler.target=17) derler.
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Once bagimliliklar (Docker katman onbellegi icin ayri adim).
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Sonra kaynaklar derlenir. setup.json ve tickets/*.json src/main/resources
# altinda oldugu icin shaded jar'a gomulur — calisma aninda mount gerekmez.
COPY src ./src
RUN mvn -B clean package -DskipTests

# 2. Asama: Run — yalnizca JRE + shaded jar.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Shaded uber-jar; sürümden bağımsız glob. `data-generator-*` deseni shade'in
# bıraktığı `original-data-generator-*.jar`'ı (original- önekiyle) eşleştirmez,
# yani tek dosya seçilir ve sürüm bump'larında bu satır güncellenmek zorunda kalmaz.
COPY --from=builder /app/target/data-generator-*.jar app.jar

# users.json (kimlik bilgileri + seed kullanici listeleri) calisma aninda
# /app/users.json olarak compose volume ile mount edilir; jar'a gomulmez.
# Ag adresleri GEN_BASE_URL / GEN_DB_URL env'leriyle compose'tan verilir.
ENTRYPOINT ["java", "-jar", "app.jar"]
