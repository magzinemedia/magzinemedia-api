FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# poppler-utils (pdftoppm/pdfinfo) renders PDF pages to images in a native
# subprocess instead of the JVM heap — much lower memory overhead than
# PDFBox's in-process Java2D rendering, which was OOMing on Render's free tier.
RUN apt-get update && apt-get install -y --no-install-recommends poppler-utils \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Render's free tier gives the container ~512MB total. The JVM's container-aware
# default (25% of that for max heap, ~128MB) was too small for PDF page
# rendering and caused OutOfMemoryError partway through processing — raise the
# heap ceiling explicitly and use SerialGC, which has much lower memory
# overhead than the default G1 collector at this scale.
ENTRYPOINT ["java", "-Xmx384m", "-XX:+UseSerialGC", "-Djava.awt.headless=true", "-jar", "app.jar"]
