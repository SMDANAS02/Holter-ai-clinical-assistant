# Multi-stage Dockerfile for Holter Monitor AI Dashboard
# Includes both Python (Streamlit) and Java (HolterAgentCLI)

# Stage 1: Build Java backend
FROM maven:3.9-eclipse-temurin-17 AS java-builder

WORKDIR /app/backend
COPY backend/pom.xml .
COPY backend/src ./src

# Build the Java JAR
RUN mvn clean package -DskipTests

# Stage 2: Build Python frontend with Java runtime
FROM python:3.11-slim

# Install Java runtime (needed for HolterAgentCLI)
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Python requirements
COPY dashboard/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy Streamlit dashboard
COPY dashboard/ ./dashboard/

# Copy Java JAR from builder stage
COPY --from=java-builder /app/backend/target/holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar ./backend/
COPY --from=java-builder /app/backend/target/holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar ./backend/

# Copy sample data
COPY sample_data/ ./sample_data/

# Copy environment template
COPY .env.example .

# Expose Streamlit port
EXPOSE 8501

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV STREAMLIT_SERVER_PORT=8501
ENV STREAMLIT_SERVER_HEADLESS=true

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8501/_stcore/health || exit 1

# Run Streamlit
CMD ["streamlit", "run", "dashboard/st_dashboard.py", "--server.port=8501", "--server.headless=true", "--server.address=0.0.0.0"]
