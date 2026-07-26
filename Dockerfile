FROM openjdk:17-slim

WORKDIR /build

# Copy project
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build
RUN ./gradlew clean build

# Output
CMD ["bash", "-c", "cp build/libs/*.jar /output/ && ls -la /output/"]
