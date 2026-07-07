# Holter Monitor ΓÇö Student Project

This repository contains a student-built Holter monitor analysis pipeline and a Streamlit dashboard for visualising flagged cardiac events. It is intended for research and educational use only.

Key points:
- Language: Java 17 (Maven build)
- Dashboard: Streamlit (Python)
- Purpose: detect and summarise short-duration, patient-specific anomalous ECG events

Quick start (development):

```powershell
# Build backend
mvn -f backend/pom.xml -DskipTests package

# Run the pipeline locally (example synthetic input)
java -jar backend/target/holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar --input synthetic --output findings.json

# Run the dashboard
python -m venv .venv
& ".venv\Scripts\Activate.ps1"
pip install -r dashboard/requirements.txt
streamlit run dashboard/st_dashboard.py
```

Project layout (top-level):

- `backend/` ΓÇö Java source, Maven `pom.xml`, database sync and pipeline
- `dashboard/` ΓÇö Streamlit app and Python requirements
- `target/` ΓÇö build outputs (ignored by `.gitignore`)
- `docs/` ΓÇö documentation and archived notes (non-essential docs)

Notes:
- This is a student project; code may be experimental and is not production hardened.
- Keep environment secrets out of the repo: do not commit `.env` files or API keys.

## License

Add license information or contact the author for reuse permissions.
- **OkHttp**: HTTP client for Anthropic API calls

