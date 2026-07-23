import re

with open('backend/server.js', 'r') as f:
    content = f.read()

old_code = """    if (signature !== expectedSignature) {
        return res.status(401).json({ error: 'Invalid signature' });
    }"""

new_code = """    try {
        const sigBuffer = Buffer.from(signature, 'utf8');
        const expectedBuffer = Buffer.from(expectedSignature, 'utf8');
        if (sigBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(sigBuffer, expectedBuffer)) {
            return res.status(401).json({ error: 'Invalid signature' });
        }
    } catch (e) {
        return res.status(401).json({ error: 'Invalid signature format' });
    }"""

content = content.replace(old_code, new_code)

with open('backend/server.js', 'w') as f:
    f.write(content)

