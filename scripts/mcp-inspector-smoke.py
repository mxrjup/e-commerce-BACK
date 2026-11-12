"""M05: exercise the local Docker stack with the real MCP Inspector CLI.

Run with MCP_ADMIN_TOKEN, or --demo-login for the repository's local demo user.
Requires Python 3 and Node 22.7.5+. No Python packages are needed.
"""
import argparse
import base64
import hashlib
import http.cookiejar
import json
import os
from html.parser import HTMLParser
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class LoginForm(HTMLParser):
    action = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form" and attrs.get("id") == "kc-form-login":
            self.action = attrs["action"]


def demo_login():
    """Authorization Code + PKCE; does not enable password grants in Keycloak."""
    base = "http://localhost:8180/realms/ecommerce/protocol/openid-connect"
    redirect = "http://localhost:5173/mcp-smoke"
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = secrets.token_urlsafe(24)
    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cookies), NoRedirect())
    query = urllib.parse.urlencode(dict(client_id="web-bo", redirect_uri=redirect,
        response_type="code", scope="openid", code_challenge=challenge,
        code_challenge_method="S256", state=state))
    with opener.open(base + "/auth?" + query, timeout=15) as response:
        form = LoginForm()
        form.feed(response.read().decode())
    if not form.action:
        raise RuntimeError("Keycloak login form unavailable")
    if urllib.parse.urlparse(form.action).netloc != "localhost:8180":
        raise RuntimeError("Demo login is restricted to local Keycloak")
    # Browsers allow Secure cookies on localhost HTTP; urllib does not.
    # This helper is restricted to the repository's loopback demo server.
    for cookie in cookies:
        cookie.secure = False
    data = urllib.parse.urlencode(dict(username="admin.demo@ecommerce.test",
        password="demo1234", credentialId="")).encode()
    try:
        opener.open(form.action, data=data, timeout=15)
    except urllib.error.HTTPError as response:
        if response.code not in (302, 303):
            import re
            page = response.read().decode()
            message = re.search(r'<[^>]+id="kc-error-message"[^>]*>(.*?)</div>', page, re.S)
            detail = re.sub(r'<[^>]+>', '', message[1]).strip() if message else "Check demo account and cookies"
            raise RuntimeError("Demo login failed (HTTP " + str(response.code) + "): " + detail) from None
        location = urllib.parse.urlparse(response.headers["Location"])
        params = urllib.parse.parse_qs(location.query)
        if params.get("state") != [state] or not params.get("code"):
            raise RuntimeError("Invalid Keycloak callback")
        data = urllib.parse.urlencode(dict(grant_type="authorization_code", client_id="web-bo",
            redirect_uri=redirect, code=params["code"][0], code_verifier=verifier)).encode()
        with opener.open(base + "/token", data=data, timeout=15) as token_response:
            return json.load(token_response)["access_token"]
    raise RuntimeError("Demo login did not return an authorization code")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--demo-login", action="store_true")
    args = parser.parse_args()
    admin = demo_login() if args.demo_login else os.environ.get("MCP_ADMIN_TOKEN")
    if not admin:
        parser.error("Set MCP_ADMIN_TOKEN or use --demo-login on the local demo stack")
    npx = shutil.which("npx.cmd" if os.name == "nt" else "npx")
    if not npx:
        parser.error("Node/npm is required")
    base = "http://localhost:8080"

    def api(path, body):
        request = urllib.request.Request(base + path, data=json.dumps(body).encode(),
            headers={"Authorization": "Bearer " + admin, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    issued = []
    with tempfile.TemporaryDirectory(prefix="mcp-inspector-") as directory:
        config = Path(directory) / "config.json"

        def inspect(token, method, *extra, rejected=False):
            config.write_text(json.dumps({"mcpServers": {"local": {
                "type": "streamable-http", "url": base + "/mcp"}}}), encoding="utf-8")
            result = subprocess.run([npx, "-y", "@modelcontextprotocol/inspector@0.16.8",
                "--cli", "--config", str(config), "--server", "local", "--method", method,
                *extra, "--header", "Authorization: Bearer " + token],
                capture_output=True, text=True, timeout=90)
            if rejected:
                if result.returncode == 0 or "401" not in result.stderr + result.stdout:
                    raise AssertionError("Inspector should reject the token with 401")
                return None
            if result.returncode:
                # Never print the command, config or token on failure.
                raise RuntimeError("Inspector failed for " + method + ": " +
                    (result.stderr + result.stdout).replace(token, "[redacted]")[-1500:])
            return json.loads(result.stdout)

        def issue(scopes):
            created = api("/api/admin/mcp/tokens", {"name": "M05 Inspector smoke", "scopes": scopes})
            issued.append(created["id"])
            return created

        try:
            created = issue(["catalog:read", "orders:read"])
            token = created["token"]
            tools = inspect(token, "tools/list")
            assert {t["name"] for t in tools["tools"]} == {"get_product_stock", "get_pending_shipments"}
            print("PASS initialize + tools/list via gateway", flush=True)
            prompts = inspect(token, "prompts/list")
            assert {p["name"] for p in prompts["prompts"]} == {"product_stock", "pending_shipments", "stock_coverage"}
            print("PASS prompts/list (3 prompts)", flush=True)
            prompt = inspect(token, "prompts/get", "--prompt-name", "product_stock",
                "--prompt-args", "productName=Logitech G Pro")
            assert "Logitech G Pro" in json.dumps(prompt) and "get_product_stock" in json.dumps(prompt)
            print("PASS prompts/get with product argument", flush=True)
            for name, field, expected in [("get_product_stock", "stock", 24),
                                          ("get_pending_shipments", "pendingUnits", 7)]:
                result = inspect(token, "tools/call", "--tool-name", name,
                    "--tool-arg", "productName=Logitech G Pro")
                assert not result.get("isError")
                data = json.loads(result["content"][0]["text"])
                assert data["source"] == "seed" and data["matches"][0][field] == expected
                print("PASS " + name + " (demo " + str(expected) + ")", flush=True)
            limited = issue(["catalog:read"])
            denied = inspect(limited["token"], "tools/call", "--tool-name", "get_pending_shipments",
                "--tool-arg", "productName=Logitech G Pro")
            assert denied.get("isError") and "orders:read" in json.dumps(denied)
            assert "pendingUnits" not in json.dumps(denied)
            print("PASS missing scope returns MCP tool error without business data", flush=True)
            inspect("mcp_invalid", "tools/list", rejected=True)
            api("/api/admin/mcp/tokens/" + str(created["id"]) + "/revoke", {})
            inspect(token, "tools/list", rejected=True)
            print("PASS unknown and revoked tokens rejected (401)", flush=True)
        finally:
            for token_id in issued:
                api("/api/admin/mcp/tokens/" + str(token_id) + "/revoke", {})
    print("PASS M05; test tokens revoked and temporary credentials removed", flush=True)


if __name__ == "__main__":
    main()
