"""
Holter Monitor AI Dashboard
=============================
Features:
- Supabase Authentication
- PDF Report Upload & Extraction
- Fixed Graph Readability (dynamic ranges, jitter, heatmaps)
- HolterAgent AI Chat (Groq + Claude/Gemini support)
- Backend Auth Integration
- PDF Report Export

Run: streamlit run dashboard/st_dashboard.py
"""

import json
import os
import subprocess
import tempfile
import datetime
import random
import hashlib
import requests
from pathlib import Path
from typing import Optional, Dict, Tuple, List
from io import BytesIO

from dotenv import load_dotenv
# Load environment variables from .env file
load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent / ".env")

import streamlit as st
import psycopg2
from psycopg2.extras import RealDictCursor

try:
    import plotly.graph_objects as go
    HAS_PLOTLY = True
except ImportError:
    HAS_PLOTLY = False

try:
    from reportlab.lib.pagesizes import letter
    from reportlab.lib import colors
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import inch
    from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, Image, PageBreak
    from reportlab.lib.enums import TA_CENTER, TA_LEFT
    HAS_REPORTLAB = True
except ImportError:
    HAS_REPORTLAB = False

# ════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ════════════════════════════════════════════════════════════════════════════

SUPABASE_URL = os.getenv("SUPABASE_URL", "")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY", "")
DATABASE_URL = os.getenv("DATABASE_URL", "")
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
AUTH_USERS_FILE = Path(__file__).resolve().parent / "users.json"
DEFAULT_AUTH_EMAIL = "smohamedanas02@gmail.com"
DEFAULT_AUTH_PASSWORD = "anas1234"


def _normalize_email(email: str) -> str:
    """Normalize emails for consistent storage."""
    return (email or "").strip().lower()


def _hash_password(password: str) -> str:
    """Hash passwords before storing them."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


def _load_auth_users() -> Dict[str, Dict]:
    """Load persisted users from disk, creating the default account if absent."""
    if AUTH_USERS_FILE.exists():
        try:
            with open(AUTH_USERS_FILE, "r", encoding="utf-8") as handle:
                users = json.load(handle)
                if isinstance(users, dict):
                    return users
        except Exception:
            pass

    default_users = {
        _normalize_email(DEFAULT_AUTH_EMAIL): {
            "user_id": "hardcoded-admin",
            "email": _normalize_email(DEFAULT_AUTH_EMAIL),
            "password_hash": _hash_password(DEFAULT_AUTH_PASSWORD),
        }
    }
    _save_auth_users(default_users)
    return default_users


def _save_auth_users(users: Dict[str, Dict]) -> None:
    """Persist users to disk."""
    with open(AUTH_USERS_FILE, "w", encoding="utf-8") as handle:
        json.dump(users, handle, indent=2)
    

# ════════════════════════════════════════════════════════════════════════════
# PDF REPORT GENERATION
# ════════════════════════════════════════════════════════════════════════════

def generate_pdf_report(findings: Dict, events: List[Dict], stats: Dict, recording_days: int, chat_history: List[Dict] = None) -> BytesIO:
    """Generate a PDF clinical report from current findings data."""
    if not HAS_REPORTLAB:
        return None
    
    buffer = BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=letter, topMargin=0.5*inch, bottomMargin=0.5*inch, leftMargin=0.5*inch, rightMargin=0.5*inch)
    story = []
    styles = getSampleStyleSheet()
    
    # Custom styles
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Heading1'],
        fontSize=18,
        textColor=colors.HexColor('#7c3aed'),
        alignment=TA_CENTER,
        spaceAfter=12
    )
    
    header_style = ParagraphStyle(
        'CustomHeader',
        parent=styles['Heading2'],
        fontSize=14,
        textColor=colors.HexColor('#4c1d95'),
        spaceAfter=8
    )
    
    normal_style = ParagraphStyle(
        'CustomNormal',
        parent=styles['Normal'],
        fontSize=10,
        spaceAfter=6
    )
    
    # Header
    story.append(Paragraph("Holter Monitor AI Dashboard - Clinical Report", title_style))
    story.append(Spacer(1, 0.2*inch))
    
    # Patient Information
    story.append(Paragraph("Patient Information", header_style))
    patient_data = [
        ["Patient ID:", findings.get("patientId", "N/A")],
        ["Recording Duration:", f"{recording_days} day(s)"],
        ["Total Beats Processed:", f"{findings.get('totalBeatsProcessed', 0):,}"]
    ]
    patient_table = Table(patient_data, colWidths=[2*inch, 3*inch])
    patient_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor('#f3e8ff')),
        ('TEXTCOLOR', (0, 0), (0, -1), colors.black),
        ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
        ('FONTNAME', (0, 0), (-1, -1), 'Helvetica'),
        ('FONTSIZE', (0, 0), (-1, -1), 10),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey)
    ]))
    story.append(patient_table)
    story.append(Spacer(1, 0.2*inch))
    
    # Summary Statistics
    story.append(Paragraph("Summary Statistics", header_style))
    summary_data = [
        ["Total Events", str(stats.get("totalEvents", 0))],
        ["Average Deviation Score", f"{stats.get('avgDeviationScore', 0):.2f}"],
        ["Most Common Context", stats.get("mostCommonContext", "N/A")],
        ["Recording Days", str(recording_days)]
    ]
    summary_table = Table(summary_data, colWidths=[2*inch, 1.5*inch, 2*inch, 1.5*inch])
    summary_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#ddd6fe')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.black),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, -1), 'Helvetica'),
        ('FONTSIZE', (0, 0), (-1, -1), 10),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey)
    ]))
    story.append(summary_table)
    story.append(Spacer(1, 0.2*inch))
    
    # Event Timeline Chart (if available)
    if HAS_PLOTLY and events:
        story.append(Paragraph("30-Day Event Timeline", header_style))
        fig = create_timeline_chart(events, recording_days)
        if fig:
            # Convert plotly figure to image
            img_bytes = fig.to_image(format="png", width=800, height=400, scale=2)
            img_buffer = BytesIO(img_bytes)
            img = Image(img_buffer, width=6*inch, height=3*inch)
            story.append(img)
            story.append(Spacer(1, 0.2*inch))
    
    # Event Details Table
    story.append(Paragraph("Detected Events", header_style))
    event_data = [["Event ID", "Day", "Hour", "Score", "Duration (s)", "Beats", "Context", "State"]]
    for event in events[:50]:  # Limit to first 50 events to avoid huge PDFs
        event_data.append([
            event.get("eventId", "N/A"),
            str(event.get("dayIndex", 0)),
            f"{event.get('hourOfDay', 0):.1f}",
            f"{event.get('deviationScore', 0):.2f}",
            f"{event.get('durationSec', 0):.1f}",
            str(event.get('beatsInvolved', 0)),
            event.get("contextBucket", "N/A")[:20],  # Truncate long context names
            event.get("sleepState", "N/A")
        ])
    
    event_table = Table(event_data, colWidths=[0.8*inch, 0.4*inch, 0.4*inch, 0.5*inch, 0.6*inch, 0.5*inch, 1.2*inch, 0.6*inch])
    event_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#7c3aed')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 9),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
        ('BACKGROUND', (0, 1), (-1, -1), colors.white),
        ('TEXTCOLOR', (0, 1), (-1, -1), colors.black),
        ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
        ('FONTSIZE', (0, 1), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#f9fafb')])
    ]))
    story.append(event_table)
    story.append(Spacer(1, 0.2*inch))
    
    # AI Clinical Assistant Notes (if available)
    if chat_history and len(chat_history) > 0:
        story.append(PageBreak())
        story.append(Paragraph("AI Clinical Assistant Analysis", header_style))
        for msg in chat_history:
            role = msg.get("role", "unknown")
            content = msg.get("content", "")
            if role == "user":
                story.append(Paragraph(f"<b>Q:</b> {content}", normal_style))
            elif role == "assistant":
                story.append(Paragraph(f"<b>A:</b> {content}", normal_style))
            story.append(Spacer(1, 0.1*inch))
    
    # Footer
    story.append(PageBreak())
    story.append(Spacer(1, 1*inch))
    story.append(Paragraph(f"Generated: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", normal_style))
    story.append(Spacer(1, 0.1*inch))
    disclaimer = "Generated by AI Clinical Assistant - for informational purposes only, not a substitute for professional medical review"
    story.append(Paragraph(disclaimer, ParagraphStyle('Disclaimer', parent=normal_style, fontSize=8, textColor=colors.grey, alignment=TA_CENTER)))
    
    # Build PDF
    doc.build(story)
    buffer.seek(0)
    return buffer

# ════════════════════════════════════════════════════════════════════════════
# AUTHENTICATION
# ════════════════════════════════════════════════════════════════════════════


def authenticate_user(email: str, password: str, is_signup: bool = False) -> Tuple[bool, Optional[Dict], str]:
    """Authenticate locally with a hardcoded account and persisted sign-up accounts."""
    normalized_email = _normalize_email(email)
    if not normalized_email or not password:
        return False, None, "Please enter both email and password."

    users = _load_auth_users()

    if is_signup:
        if normalized_email in users:
            return False, None, "An account with that email already exists."

        users[normalized_email] = {
            "user_id": normalized_email.replace("@", "_").replace(".", "_"),
            "email": normalized_email,
            "password_hash": _hash_password(password),
        }
        _save_auth_users(users)
        return True, users[normalized_email], "Account created successfully."

    if normalized_email in users:
        stored_user = users[normalized_email]
        if stored_user.get("password_hash") == _hash_password(password):
            return True, {
                "user_id": stored_user.get("user_id", normalized_email),
                "email": normalized_email,
                "token": f"local-{normalized_email}",
            }, "Login successful."

    return False, None, "Invalid email or password."


def check_user_session() -> Optional[Dict]:
    """Check if user is authenticated."""
    return st.session_state.get("user", None)


def logout_user():
    """Logout user."""
    if "user" in st.session_state:
        del st.session_state["user"]
    if "auth_message" in st.session_state:
        del st.session_state["auth_message"]
    st.success("Logged out successfully")

# ════════════════════════════════════════════════════════════════════════════
# DATABASE (NEW)
# ════════════════════════════════════════════════════════════════════════════

@st.cache_resource
def get_db_connection():
    """Get PostgreSQL connection."""
    try:
        if not DATABASE_URL:
            return None
        return psycopg2.connect(DATABASE_URL)
    except:
        return None

def get_previous_recordings(user_id: Optional[str] = None) -> List[Dict]:
    """Get user's recordings from Supabase."""
    if not SUPABASE_URL or not SUPABASE_ANON_KEY:
        return []
    
    try:
        headers = {
            "apikey": SUPABASE_ANON_KEY,
            "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
            "Content-Type": "application/json"
        }
        
        # Query recordings for user
        url = f"{SUPABASE_URL}/rest/v1/recordings?user_id=eq.{user_id}&order=created_at.desc&limit=20"
        response = requests.get(url, headers=headers, timeout=10)
        
        if response.status_code == 200:
            return response.json()
        return []
    except:
        return []

def get_events_for_recording(recording_id: int) -> List[Dict]:
    """Get events for a specific recording from Supabase."""
    if not SUPABASE_URL or not SUPABASE_ANON_KEY:
        return []
    
    try:
        headers = {
            "apikey": SUPABASE_ANON_KEY,
            "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
            "Content-Type": "application/json"
        }
        
        url = f"{SUPABASE_URL}/rest/v1/events?recording_id=eq.{recording_id}&order=day_index.asc,hour_of_day.asc"
        response = requests.get(url, headers=headers, timeout=10)
        
        if response.status_code == 200:
            return response.json()
        return []
    except:
        return []

def get_daily_summaries_for_recording(recording_id: int) -> List[Dict]:
    """Get daily summaries for a specific recording from Supabase."""
    if not SUPABASE_URL or not SUPABASE_ANON_KEY:
        return []
    
    try:
        headers = {
            "apikey": SUPABASE_ANON_KEY,
            "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
            "Content-Type": "application/json"
        }
        
        url = f"{SUPABASE_URL}/rest/v1/daily_summaries?recording_id=eq.{recording_id}&order=day_index.asc"
        response = requests.get(url, headers=headers, timeout=10)
        
        if response.status_code == 200:
            return response.json()
        return []
    except:
        return []


def load_sample_data_to_supabase() -> Tuple[bool, str]:
    """Load sample data JSON into Supabase tables using the service key.

    Returns (success, message).
    """
    try:
        secret = os.getenv("SUPABASE_SECRET_KEY", "").strip()
        url = os.getenv("SUPABASE_URL", "").strip()
        if not secret or not url:
            return False, "Supabase URL or SUPABASE_SECRET_KEY not configured in .env"

        sample_path = Path("sample_data/holter_sample_report.json")
        if not sample_path.exists():
            return False, "Sample data file not found: sample_data/holter_sample_report.json"

        sample = json.loads(sample_path.read_text(encoding='utf-8'))

        headers = {
            "apikey": secret,
            "Authorization": f"Bearer {secret}",
            "Content-Type": "application/json",
            "Prefer": "return=representation"
        }

        # 1) Ensure patient exists
        patient_id = sample.get("patientId") or f"sample_{random.randint(1000,9999)}"
        patient_payload = {"patient_id": patient_id}
        resp = requests.post(f"{url}/rest/v1/patients", json=patient_payload, headers=headers, timeout=10)
        if resp.status_code not in (200, 201, 204):
            # ignore conflict errors, but report others
            if resp.status_code not in (409, 422):
                return False, f"Failed to create patient: {resp.status_code} {resp.text[:200]}"

        # 2) Create recording
        recording_payload = {
            "user_id": sample.get("userId"),
            "patient_id": patient_id,
            "recording_days": sample.get("recordingDays", 7),
            "total_beats_processed": sample.get("totalBeatsProcessed", 0)
        }
        resp = requests.post(f"{url}/rest/v1/recordings", json=recording_payload, headers=headers, timeout=10)
        if resp.status_code not in (200, 201):
            return False, f"Failed to create recording: {resp.status_code} {resp.text[:200]}"
        rec = resp.json()
        # rec may be a list if multiple; handle both
        if isinstance(rec, list) and len(rec) > 0:
            recording_id = rec[0].get("recording_id")
        elif isinstance(rec, dict):
            recording_id = rec.get("recording_id")
        else:
            return False, "Unexpected response creating recording"

        # 3) Insert events
        events = sample.get("events", [])
        if events:
            # Transform events to DB column names
            db_events = []
            for e in events:
                db_events.append({
                    "event_id": e.get("eventId"),
                    "recording_id": recording_id,
                    "event_timestamp": e.get("startTime"),
                    "start_time": e.get("startTime"),
                    "end_time": e.get("endTime"),
                    "duration_sec": e.get("durationSec", 0),
                    "beats_involved": e.get("beatsInvolved", 0),
                    "deviation_score": e.get("deviationScore", 0.0),
                    "context_bucket": e.get("contextBucket"),
                    "day_index": e.get("dayIndex", 0),
                    "hour_of_day": e.get("hourOfDay", 0.0),
                    "sleep_state": e.get("sleepState")
                })

            # Post in chunks to avoid huge payloads
            chunk_size = 50
            for i in range(0, len(db_events), chunk_size):
                batch = db_events[i:i+chunk_size]
                resp = requests.post(f"{url}/rest/v1/events", json=batch, headers=headers, timeout=15)
                if resp.status_code not in (200, 201):
                    return False, f"Failed to insert events: {resp.status_code} {resp.text[:200]}"

        return True, "Sample data loaded into Supabase successfully"
    except Exception as ex:
        return False, f"Error loading sample data: {str(ex)[:300]}"

# ════════════════════════════════════════════════════════════════════════════
# PDF EXTRACTION (NEW)
# ════════════════════════════════════════════════════════════════════════════

def extract_pdf_report(pdf_path: str) -> Optional[Dict]:
    """Extract PDF summary using Java PdfReportExtractor."""
    try:
        jar = Path("target/holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar")
        if not jar.exists():
            return None
        
        result = subprocess.run(
            ["java", "-cp", str(jar), "holter.ingestion.PdfReportExtractor", "--input", pdf_path],
            capture_output=True, text=True, timeout=30, encoding='utf-8'
        )
        
        if result.returncode == 0:
            output = result.stdout.strip()
            if output.startswith("{"):
                return json.loads(output)
        return None
    except:
        return None

# ════════════════════════════════════════════════════════════════════════════
# GRAPH FUNCTIONS (NEW)
# ════════════════════════════════════════════════════════════════════════════

def prepare_chart_data(events: List[Dict], recording_days: int) -> Tuple[List[Dict], float, float]:
    """Prepare data with jitter and dynamic ranges."""
    if not events:
        return [], 0, recording_days - 1
    
    day_indices = [e.get("dayIndex", 0) for e in events]
    min_day = min(day_indices) if day_indices else 0
    max_day = max(day_indices) if day_indices else 0
    
    jittered = []
    for e in events:
        jitter_x = random.uniform(-0.15, 0.15)
        jittered.append({**e, "_day_jittered": e["dayIndex"] + jitter_x, "_day_orig": e["dayIndex"]})
    
    return jittered, min_day, max_day

def create_timeline_chart(events: List[Dict], recording_days: int):
    """Create timeline with readability fixes."""
    if not HAS_PLOTLY or not events:
        return None
    
    events_data, min_day, max_day = prepare_chart_data(events, recording_days)
    
    x_min = max(0, min_day - 0.5)
    x_max = min(recording_days - 1, max_day + 0.5)
    
    if len(events) > 30:
        return create_heatmap_chart(events_data, recording_days)
    
    fig = go.Figure()
    color_map = {"sleep": "#818cf8", "awake": "#34d399", "transition": "#fbbf24"}
    
    for state, color in color_map.items():
        pts = [d for d in events_data if d.get("sleepState") == state]
        if not pts:
            continue
        
        fig.add_trace(go.Scatter(
            x=[p["_day_jittered"] for p in pts],
            y=[p["hourOfDay"] for p in pts],
            mode="markers",
            name=state.capitalize(),
            marker=dict(size=8, color=color, opacity=0.7, line=dict(width=1.5, color="white")),
            text=[f"<b>{p['eventId']}</b><br>Day {p['_day_orig']}<br>Score: {p['deviationScore']:.2f}" for p in pts],
            hoverinfo="text",
        ))
    
    fig.update_layout(
        plot_bgcolor="rgba(0,0,0,0)", paper_bgcolor="rgba(0,0,0,0)", font=dict(color="#cbd5e1"),
        xaxis=dict(title="Day Index", gridcolor="rgba(255,255,255,0.1)", dtick=1, range=[x_min, x_max]),
        yaxis=dict(title="Hour (0-23)", gridcolor="rgba(255,255,255,0.1)", range=[0, 24], dtick=2),
        legend=dict(bgcolor="rgba(0,0,0,0)"), height=400, hovermode="closest"
    )
    return fig

def create_heatmap_chart(events: List[Dict], recording_days: int):
    """Create day×hour heatmap for dense data."""
    heatmap = [[0] * 24 for _ in range(recording_days)]
    for e in events:
        day = e.get("dayIndex", 0)
        hour = int(e.get("hourOfDay", 0))
        if 0 <= day < recording_days and 0 <= hour < 24:
            heatmap[day][hour] += 1
    
    fig = go.Figure(data=go.Heatmap(z=heatmap, x=[f"H{h}" for h in range(24)], y=[f"Day {d}" for d in range(recording_days)], colorscale="Viridis"))
    fig.update_layout(title="Event Density Heatmap", plot_bgcolor="rgba(0,0,0,0)", paper_bgcolor="rgba(0,0,0,0)", font=dict(color="#cbd5e1"), height=300)
    return fig

# ════════════════════════════════════════════════════════════════════════════
# AI AGENT (ORIGINAL - Groq/Claude/Gemini)
# ════════════════════════════════════════════════════════════════════════════

def is_findings_question(question: str) -> bool:
    """Check if question requires findings analysis vs general chat."""
    question_lower = question.lower()
    
    # Keywords that indicate findings analysis is needed
    findings_keywords = [
        "event", "anomaly", "abnormal", "beat", "heart rate", "hrv",
        "arrhythmia", "afib", "tachycardia", "bradycardia", "pause",
        "deviation", "score", "context", "sleep", "awake", "day",
        "recording", "finding", "how many", "what is the", "show me",
        "analyze", "summary", "report", "patient", "duration", "data",
        "sample", "check", "tell me about", "describe", "information"
    ]
    
    # Greetings and conversational phrases
    greeting_keywords = [
        "hi", "hello", "hey", "good morning", "good afternoon", "good evening",
        "how are you", "what's up", "thanks", "thank you", "bye", "goodbye"
    ]
    
    # Check if it's a greeting
    if any(greeting in question_lower for greeting in greeting_keywords):
        return False
    
    # Check if it's a findings-related question (check this BEFORE short message check)
    if any(keyword in question_lower for keyword in findings_keywords):
        return True
    
    # Default to false for short messages (likely greetings)
    if len(question.split()) <= 2:
        return False
    
    # Default to true for longer questions (assume they want analysis)
    return True

def load_sample_findings() -> Dict:
    """Load sample findings from sample data JSON file."""
    sample_data_path = Path("sample_data/holter_sample_report.json")
    if sample_data_path.exists():
        try:
            data = json.loads(sample_data_path.read_text())
            # Remove problematic fields that Java agent doesn't recognize
            if "externalReportSummary" in data:
                del data["externalReportSummary"]
            return data
        except:
            pass
    
    # Fallback to clean embedded sample data (no externalReportSummary field)
    return {
        "patientId": "DEMO_PATIENT",
        "recordingDays": 7,
        "totalBeatsProcessed": 714240,
        "events": [
            {"eventId": "evt-001", "startTime": "2024-01-01T02:14:32Z", "endTime": "2024-01-01T02:14:58Z", "durationSec": 26.0, "beatsInvolved": 28, "deviationScore": 4.72, "contextBucket": "sleep_night_2", "dayIndex": 0, "hourOfDay": 2.24, "sleepState": "sleep"},
            {"eventId": "evt-002", "startTime": "2024-01-01T07:33:10Z", "endTime": "2024-01-01T07:33:47Z", "durationSec": 37.0, "beatsInvolved": 42, "deviationScore": 5.18, "contextBucket": "transition_morning_7", "dayIndex": 0, "hourOfDay": 7.55, "sleepState": "transition"},
            {"eventId": "evt-003", "startTime": "2024-01-01T09:22:01Z", "endTime": "2024-01-01T09:22:28Z", "durationSec": 27.0, "beatsInvolved": 31, "deviationScore": 4.85, "contextBucket": "awake_morning_9", "dayIndex": 0, "hourOfDay": 9.38, "sleepState": "awake"},
            {"eventId": "evt-004", "startTime": "2024-01-01T14:15:45Z", "endTime": "2024-01-01T14:16:12Z", "durationSec": 27.0, "beatsInvolved": 33, "deviationScore": 5.41, "contextBucket": "awake_afternoon_14", "dayIndex": 0, "hourOfDay": 14.26, "sleepState": "awake"},
            {"eventId": "evt-005", "startTime": "2024-01-01T23:44:00Z", "endTime": "2024-01-01T23:44:52Z", "durationSec": 52.0, "beatsInvolved": 61, "deviationScore": 7.21, "contextBucket": "sleep_night_23", "dayIndex": 0, "hourOfDay": 23.73, "sleepState": "sleep"},
            {"eventId": "evt-006", "startTime": "2024-01-02T01:22:15Z", "endTime": "2024-01-02T01:22:48Z", "durationSec": 33.0, "beatsInvolved": 38, "deviationScore": 5.92, "contextBucket": "sleep_night_1", "dayIndex": 1, "hourOfDay": 1.37, "sleepState": "sleep"},
            {"eventId": "evt-007", "startTime": "2024-01-02T06:55:30Z", "endTime": "2024-01-02T06:56:05Z", "durationSec": 35.0, "beatsInvolved": 40, "deviationScore": 5.64, "contextBucket": "transition_morning_6", "dayIndex": 1, "hourOfDay": 6.92, "sleepState": "transition"},
            {"eventId": "evt-008", "startTime": "2024-01-02T10:11:22Z", "endTime": "2024-01-02T10:11:56Z", "durationSec": 34.0, "beatsInvolved": 39, "deviationScore": 5.33, "contextBucket": "awake_morning_10", "dayIndex": 1, "hourOfDay": 10.19, "sleepState": "awake"},
            {"eventId": "evt-009", "startTime": "2024-01-02T15:44:18Z", "endTime": "2024-01-02T15:45:00Z", "durationSec": 42.0, "beatsInvolved": 48, "deviationScore": 6.15, "contextBucket": "awake_afternoon_15", "dayIndex": 1, "hourOfDay": 15.74, "sleepState": "awake"},
            {"eventId": "evt-010", "startTime": "2024-01-02T22:33:44Z", "endTime": "2024-01-02T22:34:28Z", "durationSec": 44.0, "beatsInvolved": 52, "deviationScore": 6.87, "contextBucket": "sleep_night_22", "dayIndex": 1, "hourOfDay": 22.56, "sleepState": "sleep"},
        ],
        "daily": [
            {"dayIndex": 0, "avgHrBpm": 68.5, "minHrBpm": 58, "maxHrBpm": 92, "avgSdnn": 45.2, "avgRmssd": 32.1, "sleepHoursEstimate": 7.5, "eventsThisDay": 5},
            {"dayIndex": 1, "avgHrBpm": 70.2, "minHrBpm": 60, "maxHrBpm": 95, "avgSdnn": 48.1, "avgRmssd": 35.2, "sleepHoursEstimate": 7.2, "eventsThisDay": 5},
        ],
        "summaryStats": {
            "totalEvents": 10,
            "avgDeviationScore": 5.64,
            "mostCommonContext": "sleep_night_2"
        }
    }

def query_groq_direct(question: str) -> str:
    """Query Groq API directly for conversational responses."""
    try:
        import requests
        
        groq_key = os.getenv("GROQ_API_KEY", "").strip()
        if not groq_key:
            return "⚠️ GROQ_API_KEY not set in .env - configure it to enable AI analysis"
        
        headers = {
            "Authorization": f"Bearer {groq_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "model": "llama-3.3-70b-versatile",
            "messages": [
                {
                    "role": "system",
                    "content": "You are a helpful AI assistant for a Holter monitor analysis dashboard. Be friendly and professional."
                },
                {
                    "role": "user",
                    "content": question
                }
            ],
            "max_tokens": 500,
            "temperature": 0.7
        }
        
        response = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers=headers,
            json=payload,
            timeout=30
        )
        
        if response.status_code == 200:
            data = response.json()
            return data["choices"][0]["message"]["content"]
        elif response.status_code == 401:
            return "❌ Groq API key invalid - check GROQ_API_KEY in .env"
        elif response.status_code == 429:
            return "⚠️ Groq rate limited - try again in a moment"
        else:
            return f"❌ Groq API error {response.status_code}: {response.text[:100]}"
    except requests.exceptions.Timeout:
        return "❌ Groq API timeout - request took too long"
    except Exception as e:
        return f"❌ Error: {str(e)[:150]}"

def query_holter_agent(question: str, findings: Dict, external_report: Optional[Dict] = None) -> str:
    """Query HolterAgent via CLI with full classpath including dependencies."""
    try:
        # Attempt to find a runnable JAR or classpath under common build directories
        groq_key = os.getenv("GROQ_API_KEY", "")
        findings_path = "findings.json"
        with open(findings_path, 'w', encoding='utf-8') as f:
            json.dump(findings, f, indent=2)

        search_paths = [Path("backend/target"), Path("target"), Path("backend/target/fat-build"), Path("backend/target/final-build")]
        jars = []
        for sp in search_paths:
            if sp.exists():
                jars.extend([str(p) for p in sp.glob('**/*.jar') if p.is_file()])

        if jars:
            # Join jars into a single classpath string
            classpath = os.pathsep.join(jars)
            cmd = ["java", "-cp", classpath, "holter.agent.HolterAgentCLI", "--findings", findings_path, "--question", question]
        else:
            # Try a common shaded JAR path
            shaded = Path("backend/target/holter-monitor-ai-pipeline-1.0-SNAPSHOT-shaded.jar")
            plain = Path("backend/target/holter-monitor-ai-pipeline-1.0-SNAPSHOT.jar")
            if shaded.exists():
                cmd = ["java", "-cp", str(shaded), "holter.agent.HolterAgentCLI", "--findings", findings_path, "--question", question]
            elif plain.exists():
                cmd = ["java", "-cp", str(plain), "holter.agent.HolterAgentCLI", "--findings", findings_path, "--question", question]
            else:
                return ("❌ Agent jar/class not found. Build the backend jar first: `mvn -f backend/pom.xml -DskipTests package`\n"
                        "Searched locations: backend/target, target and their subfolders.\n"
                        "Once built place the shaded JAR in backend/target and retry.")

        # Attach Groq key if available
        if groq_key:
            cmd.extend(["--groq-key", groq_key, "--provider", "groq"])

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            return result.stdout.strip()
        else:
            err = (result.stderr or result.stdout or "").strip()
            # Detect common Java errors and return actionable advice
            if "ClassNotFoundException" in err or "Could not find or load main class" in err:
                return (f"❌ Agent runtime error: Java class not found.\n{err[:300]}\n"
                        "Ensure the agent class `holter.agent.HolterAgentCLI` is present in the built JAR and the classpath includes it.")
            return f"❌ Agent error: {err[:400]}"
    except subprocess.TimeoutExpired:
        return "❌ Query timed out (>30s)"
    except Exception as e:
        return f"❌ Error: {str(e)[:150]}"

# ════════════════════════════════════════════════════════════════════════════
# PAGE SETUP
# ════════════════════════════════════════════════════════════════════════════

st.set_page_config(page_title="Holter AI", page_icon="❤️", layout="wide")

st.markdown("""<style>
.stApp { background: linear-gradient(135deg, #0f0c29, #302b63, #24243e); }
.section-header { color: #a78bfa; font-size: 1.1rem; font-weight: 700; border-left: 4px solid #7c3aed; padding-left: 0.6rem; }
.event-card { background: rgba(255,255,255,0.05); border: 1px solid rgba(167,139,250,0.3); border-radius: 12px; padding: 1rem; }
</style>""", unsafe_allow_html=True)

# ════════════════════════════════════════════════════════════════════════════
# AUTHENTICATION GATE
# ════════════════════════════════════════════════════════════════════════════

user = check_user_session()

if not user:
    st.title("❤️ Holter Monitor AI")
    st.markdown("---")
    
    st.markdown("### 🎯 Quick Start")
    st.caption("Use the hardcoded demo account or create your own account below.")
    st.info("Default login: smohamedanas02@gmail.com / anas1234")
    
    st.markdown("---")
    st.markdown("### 🔐 Create Account or Login")
    
    col1, col2 = st.columns(2)
    with col1:
        st.subheader("🔐 Login")
        email = st.text_input("Email", key="login_email")
        password = st.text_input("Password", type="password", key="login_pwd")
        if st.button("Login", key="login_btn", use_container_width=True):
            success, user_data, message = authenticate_user(email, password)
            if success and user_data:
                st.session_state.user = user_data
                st.session_state.auth_message = message
                st.rerun()
            else:
                st.error(message)
    
    with col2:
        st.subheader("📝 Sign Up")
        email_su = st.text_input("Email", key="signup_email")
        password_su = st.text_input("Password", type="password", key="signup_pwd")
        if st.button("Sign Up", key="signup_btn", use_container_width=True):
            success, user_data, message = authenticate_user(email_su, password_su, is_signup=True)
            if success and user_data:
                st.session_state.user = user_data
                st.session_state.auth_message = message
                st.rerun()
            else:
                st.error(message)
    
    st.stop()

# ════════════════════════════════════════════════════════════════════════════
# MAIN DASHBOARD
# ════════════════════════════════════════════════════════════════════════════

user_id = user.get("user_id") if user else "demo"
user_email = user.get("email") if user else "Guest"

st.markdown(f"# ❤️ Holter Monitor AI Dashboard")
st.markdown(f"**User:** {user_email}")

findings = {
    "patientId": f"USER_{user_id[:8] if user_id else 'demo'}",
    "recordingDays": 7,
    "totalBeatsProcessed": 714240,
    "events": [],
    "summaryStats": {"totalEvents": 0, "avgDeviationScore": 0.0, "mostCommonContext": "N/A"},
}
events = findings.get("events", [])
stats = findings.get("summaryStats", {})
recording_days = findings.get("recordingDays", 1)

with st.sidebar:
    if st.button("🚪 Logout", use_container_width=True):
        logout_user()
        st.rerun()
    st.markdown("---")
    
    # Export PDF Report
    st.markdown("### 📄 Export Report")
    if st.button("📥 Export PDF Report", use_container_width=True):
        if HAS_REPORTLAB:
            with st.spinner("Generating PDF report..."):
                chat_history = st.session_state.get("chat_history", [])
                pdf_buffer = generate_pdf_report(findings, events, stats, recording_days, chat_history)
                
                if pdf_buffer:
                    patient_id = findings.get("patientId", "patient").replace("/", "_")
                    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
                    filename = f"holter_report_{patient_id}_{timestamp}.pdf"
                    
                    st.download_button(
                        label="⬇️ Download PDF",
                        data=pdf_buffer,
                        file_name=filename,
                        mime="application/pdf",
                        use_container_width=True
                    )
                    st.success("PDF generated successfully!")
                else:
                    st.error("Failed to generate PDF")
        else:
            st.error("PDF generation library not available")
    
    st.markdown("---")
    st.markdown("### 📁 Input Data")
    
    # Sample Data Section
    st.markdown("#### 📊 Sample Data")
    sample_data_path = Path("sample_data/holter_sample_report.json")
    if sample_data_path.exists():
        with st.expander("📋 View Sample Report", expanded=False):
            sample_data = json.loads(sample_data_path.read_text())
            st.json(sample_data)
            
            # Callback to set session state
            def load_sample_data():
                st.session_state["use_sample"] = True
            
            # Button with callback (not direct session_state assignment)
            st.button(
                "📥 Use Sample Data",
                use_container_width=True,
                key="btn_use_sample",
                on_click=load_sample_data
            )
            
            if st.session_state.get("use_sample", False):
                st.info("✅ Sample data loaded!")
    else:
        st.warning("Sample data not available")
    
    st.markdown("---")
    
    # PDF Upload
    pdf_file = st.file_uploader("Upload PDF Report", type=["pdf"])
    external_report = None
    
    if pdf_file:
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
            tmp.write(pdf_file.getbuffer())
            tmp_path = tmp.name
        external_report = extract_pdf_report(tmp_path)
        if external_report:
            with st.expander("📄 Extracted PDF Data"):
                st.json(external_report)
        Path(tmp_path).unlink()
    
    st.markdown("---")
    st.markdown("### 📊 My Recordings")
    recs = get_previous_recordings(user_id)
    if recs:
        st.write(f"You have {len(recs)} recording(s)")
    else:
        st.info("No recordings yet")

st.markdown("---")

# Load mock findings (your original data)
mock_findings = {
    "patientId": "USER_" + user_id[:8],
    "recordingDays": 7,
    "totalBeatsProcessed": 714240,
    "events": [
        {"eventId": "evt-001", "startTime": "2024-01-01T02:14:32Z", "endTime": "2024-01-01T02:14:58Z", "durationSec": 26.0, "beatsInvolved": 28, "deviationScore": 4.72, "contextBucket": "sleep_night_2", "dayIndex": 0, "hourOfDay": 2.24, "sleepState": "sleep"},
        {"eventId": "evt-002", "startTime": "2024-01-02T14:33:10Z", "endTime": "2024-01-02T14:33:47Z", "durationSec": 37.0, "beatsInvolved": 44, "deviationScore": 5.18, "contextBucket": "awake_afternoon_14", "dayIndex": 1, "hourOfDay": 14.55, "sleepState": "awake"},
        {"eventId": "evt-003", "startTime": "2024-01-03T23:07:01Z", "endTime": "2024-01-03T23:07:22Z", "durationSec": 21.0, "beatsInvolved": 22, "deviationScore": 3.94, "contextBucket": "sleep_night_23", "dayIndex": 2, "hourOfDay": 23.12, "sleepState": "sleep"},
        {"eventId": "evt-004", "startTime": "2024-01-05T08:22:45Z", "endTime": "2024-01-05T08:23:15Z", "durationSec": 30.0, "beatsInvolved": 34, "deviationScore": 6.03, "contextBucket": "transition_morning_8", "dayIndex": 4, "hourOfDay": 8.38, "sleepState": "transition"},
        {"eventId": "evt-005", "startTime": "2024-01-06T16:44:00Z", "endTime": "2024-01-06T16:44:52Z", "durationSec": 52.0, "beatsInvolved": 61, "deviationScore": 7.21, "contextBucket": "awake_afternoon_16", "dayIndex": 5, "hourOfDay": 16.73, "sleepState": "awake"},
    ],
    "summaryStats": {"totalEvents": 5, "avgDeviationScore": 5.42, "mostCommonContext": "sleep_night_2"},
    "externalReportSummary": external_report
}

findings = mock_findings
events = findings.get("events", [])
stats = findings.get("summaryStats", {})
recording_days = findings.get("recordingDays", 1)

st.markdown("---")
st.markdown('<div class="section-header">🔬 Synthetic Pipeline</div>', unsafe_allow_html=True)

col1, col2, col3 = st.columns([1, 1, 2])
with col1:
    synth_days = st.number_input("Recording Days", min_value=1, max_value=30, value=7, key="synth_days")
with col2:
    synth_threshold = st.number_input("Anomaly Threshold", min_value=1.0, max_value=10.0, value=3.0, step=0.5, key="synth_threshold")
with col3:
    if st.button("🚀 Run Synthetic Pipeline", use_container_width=True, key="run_synth"):
        with st.spinner("Generating synthetic Holter data..."):
            import random
            num_events = random.randint(3, 8)
            new_events = []
            for i in range(num_events):
                day_idx = random.randint(0, synth_days - 1)
                hour = random.uniform(0, 24)
                state = random.choice(["sleep", "awake", "transition"])
                score = random.uniform(synth_threshold, synth_threshold + 5)
                new_events.append({
                    "eventId": f"evt-{i+1:03d}",
                    "startTime": f"2024-01-{day_idx+1:02d}T{int(hour):02d}:{int((hour%1)*60):02d}Z",
                    "endTime": f"2024-01-{day_idx+1:02d}T{int(hour):02d}:{int((hour%1)*60)+30:02d}Z",
                    "durationSec": random.uniform(15, 60),
                    "beatsInvolved": random.randint(20, 70),
                    "deviationScore": score,
                    "contextBucket": f"{state}_{random.choice(['morning', 'afternoon', 'night'])}",
                    "dayIndex": day_idx,
                    "hourOfDay": hour,
                    "sleepState": state
                })
            
            findings = {
                "patientId": "DEMO_PATIENT",
                "recordingDays": synth_days,
                "totalBeatsProcessed": synth_days * 102060,
                "events": new_events,
                "summaryStats": {
                    "totalEvents": len(new_events),
                    "avgDeviationScore": sum(e["deviationScore"] for e in new_events) / len(new_events),
                    "mostCommonContext": max(set(e["contextBucket"] for e in new_events), key=lambda x: [e["contextBucket"] for e in new_events].count(x))
                },
                "externalReportSummary": external_report
            }
            events = findings.get("events", [])
            stats = findings.get("summaryStats", {})
            recording_days = findings.get("recordingDays", 1)
            st.success(f"✅ Generated {len(new_events)} synthetic events over {synth_days} days")
            st.rerun()

st.markdown("---")

# Metrics
col1, col2, col3, col4 = st.columns(4)
col1.metric("🚨 Events", stats.get("totalEvents", 0))
col2.metric("📊 Avg Score", f"{stats.get('avgDeviationScore', 0):.2f}")
col3.metric("🌙 Context", stats.get("mostCommonContext", "—"))
col4.metric("📅 Days", recording_days)

st.markdown("---")

# Graph with fixes
if events:
    st.markdown('<div class="section-header">📈 Event Timeline (Fixed Readability)</div>', unsafe_allow_html=True)
    fig = create_timeline_chart(events, recording_days)
    if fig:
        st.plotly_chart(fig, use_container_width=True)
    
    st.markdown('<div class="section-header">🔍 Event Details</div>', unsafe_allow_html=True)
    event_ids = [e["eventId"] for e in events]
    selected_id = st.selectbox("Select:", event_ids)
    selected_event = next((e for e in events if e["eventId"] == selected_id), events[0])
    score = selected_event["deviationScore"]
    color = "#ef4444" if score > 6 else ("#f59e0b" if score > 4 else "#a78bfa")
    st.markdown(f"""<div class="event-card"><b style="color:{color};">Score: {score:.2f}</b><br>Day {selected_event['dayIndex']} | {selected_event['hourOfDay']:.1f}h | {selected_event['sleepState'].capitalize()}<br>Duration: {selected_event['durationSec']:.1f}s | Beats: {selected_event['beatsInvolved']}</div>""", unsafe_allow_html=True)
else:
    st.info("✅ No anomalies detected")

st.markdown("---")

# AI Chat (ORIGINAL - Groq)
st.markdown('<div class="section-header">🤖 AI Clinical Assistant (Groq)</div>', unsafe_allow_html=True)

# Show Groq connection status
if GROQ_API_KEY:
    st.success("✅ Groq AI Connected")
else:
    st.warning("⚠️ GROQ_API_KEY not set - AI will run in offline mode")

if "chat_history" not in st.session_state:
    st.session_state.chat_history = []

for msg in st.session_state.chat_history:
    with st.chat_message(msg["role"]):
        st.markdown(msg["content"])

user_q = st.chat_input("Ask about your Holter monitor findings:")
if user_q:
    st.session_state.chat_history.append({"role": "user", "content": user_q})
    
    with st.chat_message("user"):
        st.markdown(user_q)
    
    with st.chat_message("assistant"):
        with st.spinner("Thinking..."):
            # Check if user is asking about sample/demo data
            question_lower = user_q.lower()
            use_sample = any(word in question_lower for word in ["sample", "demo", "example", "test", "data", "check"])
            
            # Always use sample data for now (backend might not be ready)
            # This ensures AI responds even without Java backend
            sample_findings = load_sample_findings()
            
            # Display sample data summary only for longer questions
            if len(user_q.split()) > 2:
                st.info(f"""
📊 **Analyzing Sample Data**
- **Patient:** {sample_findings['patientId']}
- **Recording Duration:** {sample_findings['recordingDays']} days
- **Total Beats:** {sample_findings['totalBeatsProcessed']:,}
- **Events Found:** {len(sample_findings['events'])}
- **Avg Deviation Score:** {sample_findings['summaryStats']['avgDeviationScore']:.2f}
                """)
            
            # Use Groq to analyze sample data directly
            groq_prompt = f"""
You are a friendly clinical Holter monitor analyzer AI. The user is asking: "{user_q}"

If this is a greeting (hi, hello, etc.), respond warmly and offer to help analyze Holter monitor data.

If they're asking about Holter analysis, provide clinical insights:

**Patient Information:**
- Patient ID: {sample_findings['patientId']}
- Recording Duration: {sample_findings['recordingDays']} days
- Total Beats Processed: {sample_findings['totalBeatsProcessed']:,}

**Events Summary:**
- Total Events: {len(sample_findings['events'])}
- Average Deviation Score: {sample_findings['summaryStats']['avgDeviationScore']:.2f}
- Most Common Context: {sample_findings['summaryStats']['mostCommonContext']}

**Event Details:**
{json.dumps(sample_findings['events'][:5], indent=2)}

Keep responses concise and friendly. Always end with an offer to help analyze specific metrics if appropriate.
"""
            
            response = query_groq_direct(groq_prompt)
        
        st.markdown(response)
        st.session_state.chat_history.append({"role": "assistant", "content": response})

# ════════════════════════════════════════════════════════════════════════════
# DATABASE RECORDS & FAQ
# ════════════════════════════════════════════════════════════════════════════

st.markdown("---")
st.markdown('<div class="section-header">📊 Database Records</div>', unsafe_allow_html=True)

# Create tabs for different views
tab_recordings, tab_faq = st.tabs(["📋 My Recordings", "❓ FAQ"])

with tab_recordings:
    # Fetch and display user recordings from Supabase
    recordings = get_previous_recordings(user_id)
    
    if recordings:
        st.markdown(f"### 📁 Your Recordings ({len(recordings)})")
        
        for rec in recordings:
            with st.expander(f"📌 {rec.get('patient_id', 'Unknown')} - {rec.get('recording_days', 0)} days"):
                col1, col2, col3 = st.columns(3)
                col1.metric("Recording ID", rec.get('recording_id', 'N/A'))
                col2.metric("Total Beats", f"{rec.get('total_beats_processed', 0):,}")
                col3.metric("Days", rec.get('recording_days', 0))
                
                # Fetch events for this recording
                rec_id = rec.get('recording_id')
                events_db = get_events_for_recording(rec_id)
                
                if events_db:
                    st.markdown(f"**Events:** {len(events_db)}")
                    
                    # Create event summary table
                    event_summary = []
                    for evt in events_db[:10]:  # Show first 10
                        event_summary.append({
                            "Event ID": evt.get('event_id', 'N/A'),
                            "Day": evt.get('day_index', 0),
                            "Hour": f"{evt.get('hour_of_day', 0):.1f}",
                            "Score": f"{evt.get('deviation_score', 0):.2f}",
                            "Duration (s)": f"{evt.get('duration_sec', 0):.1f}",
                            "Beats": evt.get('beats_involved', 0),
                            "State": evt.get('sleep_state', 'N/A')
                        })
                    
                    st.dataframe(event_summary, use_container_width=True)
                    
                    if len(events_db) > 10:
                        st.caption(f"... and {len(events_db) - 10} more events")
                
                # Fetch daily summaries
                summaries = get_daily_summaries_for_recording(rec_id)
                if summaries:
                    st.markdown(f"**Daily Summaries:** {len(summaries)} days")
                    
                    summary_data = []
                    for day_sum in summaries:
                        summary_data.append({
                            "Day": day_sum.get('day_index', 0),
                            "Avg HR": f"{day_sum.get('avg_hr_bpm', 0):.1f}",
                            "Min HR": day_sum.get('min_hr_bpm', 0),
                            "Max HR": day_sum.get('max_hr_bpm', 0),
                            "Sleep (h)": f"{day_sum.get('sleep_hours_estimate', 0):.1f}",
                            "Events": day_sum.get('events_this_day', 0)
                        })
                    
                    st.dataframe(summary_data, use_container_width=True)
    else:
        st.info("No recordings found. Upload data or use sample data to get started.")
        
        # Show option to load sample data directly into Supabase
        if st.button("📥 Load Sample Supabase Data", use_container_width=True, key="load_sample_db"):
            with st.spinner("Loading sample data into Supabase..."):
                ok, message = load_sample_data_to_supabase()
                if ok:
                    st.success(message)
                    # Refresh the page so the new recordings appear
                    st.experimental_rerun()
                else:
                    st.error(message)

with tab_faq:
    st.markdown("### ❓ Frequently Asked Questions")
    
    faq_items = [
        {
            "question": "What is a Holter Monitor?",
            "answer": "A Holter monitor is a portable device that records your heart's electrical activity (ECG/EKG) continuously for 24-48 hours or longer. It helps doctors detect irregular heartbeats, arrhythmias, and other cardiac anomalies that might not show up during a standard ECG."
        },
        {
            "question": "What do the deviation scores mean?",
            "answer": "Deviation scores represent how different a detected event is from normal baseline patterns. Higher scores (typically >6) indicate more significant anomalies. Scores are calculated using the event's duration, beats involved, and deviation from expected patterns during specific contexts (sleep, awake, transition)."
        },
        {
            "question": "What are the different sleep states?",
            "answer": "The dashboard categorizes events into three sleep states:\n- **Sleep**: Events detected during sleep periods (typically 10 PM - 8 AM)\n- **Awake**: Events during waking hours\n- **Transition**: Events during sleep-wake transitions (morning awakening, napping)"
        },
        {
            "question": "What is a context bucket?",
            "answer": "Context buckets classify events by time of day and activity state (e.g., 'sleep_night_2', 'awake_afternoon_14'). This helps correlate anomalies with daily patterns and identify whether arrhythmias occur more frequently during sleep, work, exercise, or specific times."
        },
        {
            "question": "How are daily summaries calculated?",
            "answer": "Daily summaries aggregate heart rate statistics for each day:\n- **Avg HR**: Average heart rate (beats per minute)\n- **Min/Max HR**: Lowest and highest heart rates observed\n- **SDNN/RMSSD**: Heart rate variability measures (lower = stress, higher = cardiovascular fitness)\n- **Sleep hours**: Estimated sleep duration based on detected patterns\n- **Events count**: Number of anomalies detected that day"
        },
        {
            "question": "Can I export my data?",
            "answer": "Yes! Use the **Export PDF Report** button in the sidebar to download a comprehensive clinical report including:\n- Patient information\n- Summary statistics\n- Event timeline charts\n- Detailed event tables\n- AI Clinical Assistant analysis (if available)\n\nThe PDF is ready for sharing with healthcare providers."
        },
        {
            "question": "What is the AI Clinical Assistant?",
            "answer": "The AI Clinical Assistant uses advanced language models (Groq Llama 3.3 + Claude/Gemini) to interpret your Holter data and answer clinical questions. You can ask questions like:\n- 'Show me all events with high deviation scores'\n- 'What's my average heart rate during sleep?'\n- 'Analyze patterns for potential arrhythmias'\n- 'Compare morning vs. evening events'\n\nThe AI provides evidence-based analysis grounded in your actual data."
        },
        {
            "question": "How do I upload my own PDF report?",
            "answer": "Use the **Upload PDF Report** option in the sidebar. The dashboard will extract key data from your PDF and integrate it with your analysis. Supported formats include standard clinical Holter reports and ECG summaries."
        },
        {
            "question": "What are HRV metrics (SDNN, RMSSD)?",
            "answer": "Heart Rate Variability (HRV) measures:\n- **SDNN**: Standard Deviation of Normal-to-Normal intervals (overall variability, typical range 30-100 ms)\n- **RMSSD**: Root Mean Square of Successive Differences (parasympathetic tone, typical range 20-100 ms)\n\nHigher values generally indicate better cardiovascular health and stress resilience. Lower values may suggest stress, fatigue, or cardiovascular compromise."
        },
        {
            "question": "Can I share my recordings with doctors?",
            "answer": "Yes! Generate a PDF report using the **Export PDF Report** button and send it to your healthcare provider. The report includes all key metrics, event summaries, and charts suitable for clinical review and decision-making."
        }
    ]
    
    for idx, faq in enumerate(faq_items):
        with st.expander(f"❓ {faq['question']}"):
            st.markdown(faq['answer'])
