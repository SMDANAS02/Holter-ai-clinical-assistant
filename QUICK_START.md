# 🚀 Holter Monitor AI Pipeline - Quick Start Guide

## ⚡ 30-Second Setup

### Windows Users (Easiest)
```
1. Double-click: INSTALL.bat
2. Double-click: START.bat
3. Open: http://localhost:8501
```

### Mac/Linux Users
```bash
chmod +x INSTALL.sh START.sh
./INSTALL.sh
./START.sh
```

### PowerShell Users
```powershell
.\INSTALL.ps1
.\START.ps1
```

---

## 📋 What These Scripts Do

### INSTALL Script
✅ Checks Java installation  
✅ Checks Python installation  
✅ Creates .env file from template  
✅ Creates backend config.properties  
✅ Installs Python dependencies  
✅ Creates required directories  

### START Script
✅ Loads environment variables  
✅ Runs ECG pipeline (1 day synthetic data)  
✅ Generates findings.json  
✅ Launches Streamlit dashboard  
✅ Opens http://localhost:8501  

---

## 🔧 System Requirements

| Component | Requirement | Check |
|-----------|------------|-------|
| **Java** | 17+ | `java -version` |
| **Python** | 3.8+ | `python --version` |
| **RAM** | 2GB minimum | Task Manager |
| **Disk** | 500MB free | File Explorer |

---

## 📝 Step-by-Step Installation

### Step 1: Run Installation
**Windows (CMD):**
```cmd
INSTALL.bat
```

**Windows (PowerShell):**
```powershell
.\INSTALL.ps1
```

**Mac/Linux:**
```bash
bash INSTALL.sh
```

**What happens:**
- ✅ Verifies Java is installed
- ✅ Verifies Python is installed
- ✅ Creates `.env` file
- ✅ Creates backend config
- ✅ Installs Python packages

### Step 2: Add API Keys (Optional)
Edit `.env` file and add your API keys:

```env
ANTHROPIC_API_KEY=sk-ant-xxxxx    # Claude (primary)
GOOGLE_API_KEY=xxxxx              # Gemini (fallback)
GROQ_API_KEY=gsk-xxxxx            # Groq (fast)
```

💡 **Tip**: Without API keys, the system uses rule-based responses.

### Step 3: Start the Project
**Windows (CMD):**
```cmd
START.bat
```

**Windows (PowerShell):**
```powershell
.\START.ps1
```

**Mac/Linux:**
```bash
bash START.sh
```

**What happens:**
- 🖥️ Backend: Runs ECG pipeline (synthetic data)
- 📊 Frontend: Launches Streamlit dashboard
- 🌐 Opens http://localhost:8501 automatically

---

## 🎯 First Time Running

### Expected Output

**Backend Processing:**
```
2026-07-05 14:10:23.342 [main] INFO  holter.HolterPipeline - === Holter Monitor AI Pipeline ===
2026-07-05 14:10:23.369 [main] INFO  holter.HolterPipeline - [1/5] Data ingestion...
2026-07-05 14:10:24.066 [main] INFO  holter.HolterPipeline - [2/5] Beat extraction...
2026-07-05 14:10:25.791 [main] DEBUG holter.signal.PanTompkinsDetector - Pan-Tompkins detected 93386 R-peaks
2026-07-05 14:10:25.918 [main] INFO  holter.HolterPipeline - [3/5] Context enrichment...
2026-07-05 14:10:27.759 [main] INFO  holter.HolterPipeline - [4/5] Baseline modelling...
2026-07-05 14:10:28.161 [main] INFO  holter.HolterPipeline - [5/5] Anomaly detection...
2026-07-05 14:10:29.638 [main] INFO  holter.HolterPipeline - === Pipeline complete ===
✅ Events detected: 1473
📁 Output written: findings.json
```

**Frontend Launch:**
```
  You can now view your Streamlit app in your browser.

  Local URL: http://localhost:8501
  Network URL: http://192.168.1.x:8501
```

---

## 🌐 Access the Dashboard

After startup, open your browser:

**Local Machine:**
```
http://localhost:8501
```

**From Another Computer:**
```
http://<your-ip>:8501
```

**Default Credentials:** None (no authentication by default)

---

## 📊 Dashboard Features

| Feature | Description |
|---------|-------------|
| **Timeline** | 30-day ECG visualization |
| **Events** | Detected anomalies with details |
| **Statistics** | Daily HR, HRV, sleep hours |
| **AI Chat** | Ask clinical questions |
| **File Upload** | Process your own ECG files |

---

## ⚙️ Customization

### Run Backend Only
```powershell
.\START.ps1 -Service backend
```

### Run Frontend Only
```powershell
.\START.ps1 -Service frontend
```

### Change Processing Duration
Edit the START script and change:
```powershell
--days 1  # Change to 7, 30, etc.
```

### Adjust Anomaly Threshold
Edit `backend/config.properties`:
```properties
thresholdDeviation=3.0  # Lower = more sensitive
```

---

## 🐛 Troubleshooting

### Java Not Found
```
❌ Error: Java not found
✅ Solution: Install Java 17 from oracle.com
```

### Python Not Found
```
❌ Error: Python not found (for frontend)
✅ Solution: Install Python 3.8+ from python.org
```

### Streamlit Not Found
```
❌ Error: streamlit: command not found
✅ Solution: Run: pip install streamlit
```

### Port 8501 Already in Use
```
❌ Error: Address already in use
✅ Solution: Kill existing process or change port:
   streamlit run frontend/dashboard/st_dashboard.py --server.port=8502
```

### Out of Memory
```
❌ Error: OutOfMemoryError: Java heap space
✅ Solution: Increase heap in START script:
   Change: java -Xmx2g
   To: java -Xmx4g (for 4GB)
```

---

## 📁 File Structure After Install

```
project/
├── .env                    # Your API keys (created)
├── backend/
│   ├── src/              # Java source
│   ├── pom.xml
│   └── config.properties # Your settings (created)
├── frontend/
│   └── dashboard/
│       └── st_dashboard.py
├── logs/                 # Application logs (created)
├── data/                 # Data files (created)
├── findings.json         # Pipeline output (created)
├── INSTALL.bat           # Installation script
├── INSTALL.ps1
├── START.bat             # Launcher script
└── START.ps1
```

---

## 🚀 Performance

| Task | Time | Resources |
|------|------|-----------|
| 1-day ECG | ~5 seconds | 2GB RAM |
| 7-day ECG | ~35 seconds | 2GB RAM |
| 30-day ECG | ~150 seconds | 3GB RAM |
| Dashboard Load | <1 second | Minimal |

---

## 📞 Support

### Check Logs
```
logs/holter-pipeline.log  # Application logs
```

### Verify Setup
```bash
# Check Java
java -version

# Check Python
python --version

# Check pip packages
pip list | grep streamlit
```

### Manual Backend Run
```powershell
java -Xmx2g -cp "target/classes;target/fat-build" `
  holter.HolterPipeline `
  --input synthetic `
  --output findings.json `
  --days 1
```

### Manual Frontend Run
```bash
streamlit run frontend/dashboard/st_dashboard.py
```

---

## ✅ Verification Checklist

After installation:
- [ ] Java 17+ installed (`java -version`)
- [ ] Python 3.8+ installed (`python --version`)
- [ ] `.env` file exists and readable
- [ ] `backend/config.properties` exists
- [ ] `frontend/dashboard/requirements.txt` exists
- [ ] START script runs without errors
- [ ] Dashboard opens at http://localhost:8501
- [ ] findings.json is generated

---

## 🎯 Next Steps

1. **Explore the Dashboard**
   - View ECG timeline
   - Check detected events
   - Review daily statistics

2. **Add Your Data**
   - Use file upload feature
   - Analyze your own ECG recordings

3. **Configure AI Agent**
   - Add API keys to .env
   - Ask clinical questions
   - Generate narratives

4. **Customize Settings**
   - Adjust thresholds in config
   - Change processing parameters
   - Fine-tune anomaly detection

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **README.md** | Project overview |
| **QUICK_START.md** | This file |
| **PROJECT_STRUCTURE.md** | Directory layout |
| **PROJECT_TECH_STACK.md** | Technology reference |
| **.kiro/specs/** | Detailed design docs |

---

## 🎓 Learning Resources

- **ECG Analysis**: Learn basics of cardiac signals
- **Signal Processing**: Understand filtering and peak detection
- **Machine Learning**: Baseline learning concepts
- **Web Development**: Streamlit dashboard techniques

---

**Version**: 1.0  
**Last Updated**: 2026-07-05  
**Status**: Ready to Use ✅
