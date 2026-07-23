import re

with open('backend/server.js', 'r') as f:
    content = f.read()

old_code = """// HMAC Middleware
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
};"""

new_code = """// HMAC Middleware
const verifySignature = (req, res, next) => {
    const signature = req.headers['x-signature'];
    const timestamp = req.headers['x-timestamp'];
    
    if (!signature || !timestamp) {
        return res.status(401).json({ error: 'Missing signature or timestamp' });
    }

    // Replay protection: check timestamp (within 5 minutes = 300 seconds)
    const currentUnixTime = Math.floor(Date.now() / 1000);
    const reqTime = parseInt(timestamp, 10);
    
    if (isNaN(reqTime)) {
        return res.status(401).json({ error: 'Invalid timestamp format' });
    }
    
    if (Math.abs(currentUnixTime - reqTime) > 300) {
        return res.status(401).json({ error: 'Request expired or timestamp out of bounds' });
    }

    const payloadToSign = `${timestamp}.${req.rawBody || ''}`;

    const expectedSignature = crypto
        .createHmac('sha256', API_SECRET)
        .update(payloadToSign)
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
};"""

content = content.replace(old_code, new_code)

with open('backend/server.js', 'w') as f:
    f.write(content)
