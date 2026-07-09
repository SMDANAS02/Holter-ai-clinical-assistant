# Holter AI Dashboard - UI & AI Fix Instructions

## Current Issues
1. **Groq API key invalid** - showing "Groq API key invalid - check GROQ_API_KEY in .env"
2. **UI is broken** - dark theme too dark, messages hard to read, layout needs restructuring
3. **Chat messages misaligned** - error icons not properly styled

## Fix Priority
1. **First: Fix Groq API connection** (blocking AI)
2. **Second: Redesign UI/UX** (visual issues)

---

## PART 1: FIX GROQ API (CRITICAL)

### Root Cause
The API key in Render environment is either:
- Missing or empty
- Invalid/expired
- Wrong format

### Solution
**In Render Dashboard:**
1. Go to your service: https://dashboard.render.com
2. Settings → Environment Variables
3. Check if `GROQ_API_KEY` exists
4. If missing or empty:
   - Get new key from: https://console.groq.com (login)
   - Copy the key (starts with `gsk_`)
   - Add/update: `GROQ_API_KEY=gsk_YOUR_ACTUAL_KEY_HERE`
5. Redeploy the service

**Code Change Required:**
File: `dashboard/st_dashboard.py` - Function: `query_groq_direct()`

Replace the error handling to show exact issue:
```python
# Around line 655, replace:
if not groq_key:
    st.warning("⚠️ GROQ_API_KEY environment variable not found")
    return "⚠️ GROQ_API_KEY not configured..."

# With:
if not groq_key:
    st.error("🔴 CRITICAL: GROQ_API_KEY is missing in Render environment")
    st.markdown("""
    **To fix:**
    1. Get key from https://console.groq.com
    2. Add to Render: Settings → Environment → GROQ_API_KEY
    3. Redeploy service
    """)
    return None  # Don't continue
```

---

## PART 2: FIX UI/UX (DESIGN)

### Issues to Fix

#### 1. Chat Message Display
**File:** `dashboard/st_dashboard.py` - Lines ~1050-1090 (chat input/output section)

**Current problem:** Messages use generic chat bubbles with bad styling

**Fix:** Replace with better message styling:
```python
# Replace the current chat display with:
for msg in st.session_state.chat_history:
    if msg["role"] == "user":
        with st.chat_message("user", avatar="👤"):
            st.write(msg["content"])
    else:
        with st.chat_message("assistant", avatar="🤖"):
            if "error" in msg["content"].lower() or "❌" in msg["content"]:
                st.error(msg["content"])
            else:
                st.success(msg["content"])  # Keep errors visible, green for success
```

#### 2. Dark Theme Too Dark
**File:** `dashboard/st_dashboard.py` - Lines ~760-770 (CSS styling)

**Current:** Background is pure black `#0f0c29`

**Fix:** Change color scheme to readable dark theme:
```python
st.markdown("""<style>
.stApp { 
    background: linear-gradient(135deg, #1a1626, #2d1b3d, #1a1626);
}
.stChatMessage {
    padding: 1rem;
    border-radius: 8px;
    margin: 0.5rem 0;
}
</style>""", unsafe_allow_html=True)
```

#### 3. Sidebar Layout
**File:** `dashboard/st_dashboard.py` - Lines ~800-850 (sidebar section)

**Current problem:** Too much content, not organized

**Fix:** Add expanders for cleaner layout:
```python
with st.sidebar:
    st.markdown("### ⚙️ Controls")
    
    with st.expander("📊 Pipeline Settings", expanded=True):
        synth_days = st.number_input("Recording Days", 1, 30, 7)
        threshold = st.number_input("Threshold", 1.0, 10.0, 3.0)
    
    with st.expander("📁 Data", expanded=False):
        # PDF upload and sample data here
    
    with st.expander("📋 Recordings", expanded=False):
        # My recordings here
```

#### 4. Error Message Styling
**File:** `dashboard/st_dashboard.py` - Anywhere error returned

**Current:** Red error icons with dark text = unreadable

**Fix:** Use st.error() for errors, st.success() for success:
```python
if response.status_code == 401:
    st.error("❌ Groq API key invalid or expired")
    st.info("Get new key: https://console.groq.com → Copy key → Add to Render Settings")
    return None
```

---

## PART 3: IMPLEMENTATION CHECKLIST

### Code Changes (in order)
1. ✅ Fix `query_groq_direct()` - add better error messaging
2. ✅ Replace CSS styling - brighter dark theme
3. ✅ Refactor chat display - proper st.chat_message() usage
4. ✅ Reorganize sidebar - use expanders
5. ✅ Fix error displays - use st.error()/st.success()
6. ✅ Test locally before pushing

### Testing Steps
1. Run locally: `streamlit run dashboard/st_dashboard.py`
2. Login with: `smohamedanas02@gmail.com` / `anas1234`
3. Type "hi" in chat - should see response or clear error message
4. Check UI readability - no dark-on-dark text
5. Verify layout - sidebar organized, messages clear

### Deployment
1. Commit all changes with message: "Redesign UI/UX and fix Groq API integration"
2. Push to main: `git push origin main`
3. Render auto-deploys
4. In Render settings, verify `GROQ_API_KEY` is set
5. Test on live URL

---

## Key Files to Modify
- **dashboard/st_dashboard.py** - Main file
  - Lines 648-705: `query_groq_direct()` function
  - Lines 760-770: CSS styling
  - Lines 800-850: Sidebar layout
  - Lines 1050-1090: Chat display section

## Time Estimate
- Groq fix: 5 min
- UI redesign: 15 min
- Testing: 5 min
- Total: 25 min

---

## Success Criteria
✅ Chat shows "Groq AI Connected" in top right
✅ Typing "hi" shows AI response (not error)
✅ UI is readable (light text on dark background)
✅ Sidebar is organized with collapsible sections
✅ Error messages are clear and actionable
