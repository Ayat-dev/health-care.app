FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN apt-get update && apt-get install -y libgtk-3-0 libcanberra-gtk3-module && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/*.jar desktop-app.jar
COPY run-desktop.sh /app/run-desktop.sh
RUN chmod +x /app/run-desktop.sh
CMD ["/app/run-desktop.sh"]
