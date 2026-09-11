# 1. Build the Astro pages straight into the Spring Boot static folder
FROM node:22-alpine AS web
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar with the pages inside it
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /src/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3. Run on a plain JRE. application.yml reads $PORT, so Render/Heroku-style platforms just work.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/backend/target/chapa-cuts.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
