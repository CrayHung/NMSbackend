# 1. 使用 Maven 建置階段 (改用 eclipse-temurin 版本)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. 執行階段 (改用 eclipse-temurin 映像檔)
FROM eclipse-temurin:17-jdk
WORKDIR /app
# 從 build 階段複製 jar 檔
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]