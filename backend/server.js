const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const bodyParser = require('body-parser');
const crypto = require('crypto');

const app = express();

const isProduction = process.env.NODE_ENV === 'production';
if (isProduction && !process.env.API_SECRET) {
    console.error('FATAL ERROR: API_SECRET must be set in the environment when NODE_ENV=production');
    process.exit(1);
}
const API_SECRET = process.env.API_SECRET || 'unity-tunnel-secret-key';

// Keep raw body for HMAC validation
app.use(bodyParser.json({
    verify: (req, res, buf) => {
        req.rawBody = buf.toString();
    }
}));

// HMAC Middleware
const verifySignature = (req, res, next) => {
    const signature = req.headers['x-signature'];
    if (!signature) {
        return res.status(401).json({ error: 'Missing signature' });
    }

    const expectedSignature = crypto
        .createHmac('sha256', API_SECRET)
        .update(req.rawBody || '')
        .digest('hex');

    try {
        const sigBuffer = Buffer.from(signature, 'utf8');
        const expectedBuffer = Buffer.from(expectedSignature, 'utf8');
        if (sigBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(sigBuffer, expectedBuffer)) {
            return res.status(401).json({ error: 'Invalid signature' });
        }
    } catch (e) {
        return res.status(401).json({ error: 'Invalid signature format' });
    }
    
    next();
};

// Apply signature verification to all routes
app.use(verifySignature);

// SQLite Database Setup
const db = new sqlite3.Database('./tunnel.db', (err) => {
    if (err) console.error(err.message);
    else {
        db.run(`CREATE TABLE IF NOT EXISTS users (
            device_id TEXT PRIMARY KEY,
            balance_seconds INTEGER DEFAULT 900,
            ads_today INTEGER DEFAULT 0,
            last_ad_reset_date TEXT
        )`);
        
        db.run(`CREATE TABLE IF NOT EXISTS active_sessions (
            session_id TEXT PRIMARY KEY,
            device_id TEXT,
            start_time INTEGER,
            last_heartbeat INTEGER
        )`);
    }
});

// Helper to get today's date string YYYYMMDD
const getTodayStr = () => new Date().toISOString().split('T')[0].replace(/-/g, '');

// Middleware to ensure user exists
const ensureUser = (deviceId, callback) => {
    db.get('SELECT * FROM users WHERE device_id = ?', [deviceId], (err, row) => {
        if (!row) {
            db.run('INSERT INTO users (device_id, last_ad_reset_date) VALUES (?, ?)', 
                [deviceId, getTodayStr()], (err) => callback(null));
        } else {
            // Check daily reset
            const today = getTodayStr();
            if (row.last_ad_reset_date !== today) {
                db.run('UPDATE users SET ads_today = 0, last_ad_reset_date = ? WHERE device_id = ?', 
                    [today, deviceId], () => callback(null));
            } else {
                callback(null);
            }
        }
    });
};

// Start Session
app.post('/session/start', (req, res) => {
    const { device_id, server_id } = req.body;
    ensureUser(device_id, () => {
        db.get('SELECT balance_seconds FROM users WHERE device_id = ?', [device_id], (err, row) => {
            if (row.balance_seconds <= 0) return res.status(403).json({ error: 'Zero balance' });
            
            // Fix: Invalidate existing active session for this device
            db.run('DELETE FROM active_sessions WHERE device_id = ?', [device_id], () => {
                const sessionId = `sess_${Date.now()}_${Math.floor(Math.random()*1000)}`;
                const now = Date.now();
                db.run('INSERT INTO active_sessions (session_id, device_id, start_time, last_heartbeat) VALUES (?, ?, ?, ?)', 
                    [sessionId, device_id, now, now], 
                    (err) => {
                        res.json({ session_id: sessionId, balance: row.balance_seconds });
                    });
            });
        });
    });
});

// Heartbeat
app.post('/session/heartbeat', (req, res) => {
    const { session_id, device_id } = req.body;
    const now = Date.now();
    
    db.get('SELECT last_heartbeat FROM active_sessions WHERE session_id = ? AND device_id = ?', [session_id, device_id], (err, row) => {
        if (!row) return res.status(404).json({ error: 'Session not found' });
        
        const elapsedSecs = Math.floor((now - row.last_heartbeat) / 1000);
        
        db.run('UPDATE active_sessions SET last_heartbeat = ? WHERE session_id = ?', [now, session_id], () => {
            db.run('UPDATE users SET balance_seconds = MAX(0, balance_seconds - ?) WHERE device_id = ?', [elapsedSecs, device_id], () => {
                db.get('SELECT balance_seconds FROM users WHERE device_id = ?', [device_id], (err, user) => {
                    if (user.balance_seconds <= 0) {
                        // Kill session if out of balance
                        db.run('DELETE FROM active_sessions WHERE session_id = ?', [session_id]);
                        res.json({ status: 'disconnected', remaining: 0 });
                    } else {
                        res.json({ status: 'active', remaining: user.balance_seconds });
                    }
                });
            });
        });
    });
});

// End Session
app.post('/session/end', (req, res) => {
    const { session_id } = req.body;
    db.run('DELETE FROM active_sessions WHERE session_id = ?', [session_id], () => {
        res.json({ success: true });
    });
});

// Reward Grant
app.post('/reward/grant', (req, res) => {
    const { device_id, reward_type } = req.body; // 'top_up' or 'double_up'
    
    // Fix: both reward types grant 3600 seconds
    const bonus = 3600;
    
    ensureUser(device_id, () => {
        db.get('SELECT ads_today FROM users WHERE device_id = ?', [device_id], (err, row) => {
            // Fix: ad cap is < 12
            if (row.ads_today >= 12) return res.status(403).json({ error: 'Ad cap reached' });
            
            db.run('UPDATE users SET balance_seconds = balance_seconds + ?, ads_today = ads_today + 1 WHERE device_id = ?', 
                [bonus, device_id], () => {
                    db.get('SELECT balance_seconds, ads_today FROM users WHERE device_id = ?', [device_id], (err, user) => {
                        res.json({ success: true, new_balance: user.balance_seconds, ads_today: user.ads_today });
                    });
            });
        });
    });
});

// Orphaned Session Cleanup Sweep
const CLEANUP_INTERVAL_MS = 60 * 1000; // Run every minute
const SESSION_TIMEOUT_MS = 120 * 1000; // 2 minutes without heartbeat = dead

setInterval(() => {
    const cutoff = Date.now() - SESSION_TIMEOUT_MS;
    db.run('DELETE FROM active_sessions WHERE last_heartbeat < ?', [cutoff], function(err) {
        if (!err && this.changes > 0) {
            console.log(`Cleaned up ${this.changes} orphaned session(s)`);
        }
    });
}, CLEANUP_INTERVAL_MS);

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Tunnel control plane running on port ${PORT}`);
});
