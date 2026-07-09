# Dockerfile for Holter Monitor AI Dashboard
# Python-only Streamlit deployment (Java backend optional)

FROM python:3.11-slim

WORKDIR /app

# Copy Python requirements
COPY dashboard/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy Streamlit dashboard
COPY dashboard/ ./dashboard/

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
