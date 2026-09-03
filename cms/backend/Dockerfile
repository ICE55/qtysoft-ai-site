# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
# 以非 root 用户运行，降低容器风险
RUN useradd -m -u 10001 cms && chown -R cms:cms /app
USER cms
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
