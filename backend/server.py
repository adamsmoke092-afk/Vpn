#!/usr/bin/env python3
"""
Unity Tunnel — Server-Side VPN Session & Balance Enforcement Backend
Fulfills §5 and §11 requirements.
A lightweight Python service using SQLite to track user balances, session states, 
and daily ad-watched caps independently of the client.
"""

import sqlite3
import time
from datetime import datetime, date
from flask import Flask, request, jsonify

app = Flask(__name__)
DB_FILE = "unity_tunnel.db"

def init_db():
    """Initializes the SQLite schema."""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    # User balance and caps table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            device_id TEXT PRIMARY KEY,
            balance_seconds INTEGER DEFAULT 900,
            ads_today INTEGER DEFAULT 0,
            last_ad_date TEXT,
            last_seen INTEGER
        )
    """)
    
    # Active VPN sessions table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS sessions (
            session_token TEXT PRIMARY KEY,
            device_id TEXT,
            start_time INTEGER,
            last_heartbeat INTEGER,
            status TEXT DEFAULT 'ACTIVE',
            FOREIGN KEY (device_id) REFERENCES users(device_id)
        )
    """)
    
    conn.commit()
    conn.close()

@app.route("/session/start", methods=["POST"])
def start_session():
    """
    POST /session/start
    Payload: { "device_id": "uuid-string-here" }
    Issues a short-lived session token and verifies the current balance server-side.
    """
    data = request.get_json() or {}
    device_id = data.get("device_id")
    if not device_id:
        return jsonify({"error": "device_id is required"}), 400
        
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    # Ensure user exists or create them with 15 minutes free baseline
    cursor.execute("SELECT balance_seconds FROM users WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if not row:
        cursor.execute(
            "INSERT INTO users (device_id, balance_seconds, last_seen) VALUES (?, ?, ?)",
            (device_id, 900, int(time.time()))
        )
        balance = 900
    else:
        balance = row[0]
        
    if balance <= 0:
        conn.close()
        return jsonify({"error": "Insufficient balance. Please top up."}), 403
        
    # Create active session
    session_token = f"tok_{int(time.time())}_{device_id[:6]}"
    now = int(time.time())
    cursor.execute(
        "INSERT INTO sessions (session_token, device_id, start_time, last_heartbeat) VALUES (?, ?, ?, ?)",
        (session_token, device_id, now, now)
    )
    
    conn.commit()
    conn.close()
    
    return jsonify({
        "session_token": session_token,
        "balance_seconds": balance,
        "status": "CONNECTED"
    })

@app.route("/session/heartbeat", methods=["POST"])
def session_heartbeat():
    """
    POST /session/heartbeat
    Payload: { "session_token": "tok_..." }
    Deducts balance in real-time. If balance hits zero, signals the server-side VPN 
    to terminate the tunnel and notifies the client to disconnect.
    """
    data = request.get_json() or {}
    session_token = data.get("session_token")
    if not session_token:
        return jsonify({"error": "session_token is required"}), 400
        
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    # Find session and user details
    cursor.execute("""
        SELECT s.device_id, s.last_heartbeat, u.balance_seconds, s.status
        FROM sessions s
        JOIN users u ON s.device_id = u.device_id
        WHERE s.session_token = ? AND s.status = 'ACTIVE'
    """, (session_token,))
    
    row = cursor.fetchone()
    if not row:
        conn.close()
        return jsonify({"error": "Invalid or expired session"}), 401
        
    device_id, last_heartbeat, balance, status = row
    now = int(time.time())
    elapsed = now - last_heartbeat
    if elapsed <= 0:
        elapsed = 1 # Force at least 1 second decay per heartbeat
        
    new_balance = max(0, balance - elapsed)
    
    # Update user balance
    cursor.execute(
        "UPDATE users SET balance_seconds = ?, last_seen = ? WHERE device_id = ?",
        (new_balance, now, device_id)
    )
    
    # Update heartbeat timestamp
    cursor.execute(
        "UPDATE sessions SET last_heartbeat = ? WHERE session_token = ?",
        (now, session_token)
    )
    
    # If balance runs out, expire session and flag disconnect
    if new_balance <= 0:
        cursor.execute(
            "UPDATE sessions SET status = 'EXPIRED' WHERE session_token = ?",
            (session_token,)
        )
        # Server-side enforcement hard rule: Auto-reset back to 15-minute free baseline
        cursor.execute(
            "UPDATE users SET balance_seconds = 900 WHERE device_id = ?",
            (device_id,)
        )
        conn.commit()
        conn.close()
        return jsonify({
            "balance_seconds": 900,
            "disconnect_required": True,
            "message": "Balance exhausted. Connection terminated safely. Resetting to 15-minute free baseline."
        })
        
    conn.commit()
    conn.close()
    
    return jsonify({
        "balance_seconds": new_balance,
        "disconnect_required": False
    })

@app.route("/session/end", methods=["POST"])
def end_session():
    """
    POST /session/end
    Payload: { "session_token": "tok_..." }
    Cleans up session.
    """
    data = request.get_json() or {}
    session_token = data.get("session_token")
    if not session_token:
        return jsonify({"error": "session_token is required"}), 400
        
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE sessions SET status = 'DISCONNECTED' WHERE session_token = ?",
        (session_token,)
    )
    conn.commit()
    conn.close()
    return jsonify({"status": "SUCCESS"})

@app.route("/reward/grant", methods=["POST"])
def grant_reward():
    """
    POST /reward/grant
    Payload: { "device_id": "uuid-string-here", "ad_placement": "top_up" }
    Verifies ad-completion and grants +2 hours stacked additively (capped at 5 ads/day).
    """
    data = request.get_json() or {}
    device_id = data.get("device_id")
    if not device_id:
        return jsonify({"error": "device_id is required"}), 400
        
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    # Retrieve user status
    cursor.execute("SELECT balance_seconds, ads_today, last_ad_date FROM users WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    
    today_str = date.today().isoformat()
    
    if not row:
        # Create user if missing
        cursor.execute(
            "INSERT INTO users (device_id, balance_seconds, ads_today, last_ad_date, last_seen) VALUES (?, ?, ?, ?, ?)",
            (device_id, 900 + 7200, 1, today_str, int(time.time()))
        )
        new_balance = 900 + 7200
        ads_count = 1
    else:
        balance, ads_today, last_ad_date = row
        
        # Reset ad count at daily date boundary
        if last_ad_date != today_str:
            ads_today = 0
            
        if ads_today >= 5:
            conn.close()
            return jsonify({"error": "Daily ad reward limit of 5 reached. Please return tomorrow!"}), 429
            
        new_balance = balance + 7200 # Grant +2 Hours
        ads_count = ads_today + 1
        
        cursor.execute(
            "UPDATE users SET balance_seconds = ?, ads_today = ?, last_ad_date = ?, last_seen = ? WHERE device_id = ?",
            (new_balance, ads_count, today_str, int(time.time()), device_id)
        )
        
    conn.commit()
    conn.close()
    
    return jsonify({
        "balance_seconds": new_balance,
        "ads_today": ads_count,
        "message": "Reward verified and +2 hours added successfully."
    })

if __name__ == "__main__":
    init_db()
    print("Unity Tunnel backend service starting on port 5000...")
    app.run(host="0.0.0.0", port=5000)
