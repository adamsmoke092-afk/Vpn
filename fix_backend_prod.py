import re

with open('backend/server.js', 'r') as f:
    content = f.read()

secret_regex = r"(const API_SECRET = process\.env\.API_SECRET \|\| 'unity-tunnel-secret-key';)"
match = re.search(secret_regex, content)
if match:
    new_code = """const isProduction = process.env.NODE_ENV === 'production';
if (isProduction && !process.env.API_SECRET) {
    console.error('FATAL ERROR: API_SECRET must be set in the environment when NODE_ENV=production');
    process.exit(1);
}
const API_SECRET = process.env.API_SECRET || 'unity-tunnel-secret-key';"""
    content = content[:match.start(1)] + new_code + content[match.end(1):]
    
with open('backend/server.js', 'w') as f:
    f.write(content)
