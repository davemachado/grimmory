# Trusting a Reverse Proxy's Forwarded Headers

Grimmory runs behind a reverse proxy (nginx, Traefik, Caddy, Cloudflare Tunnel, etc.) in
most self-hosted deployments. To correctly attribute requests to the real client - for
login rate limiting, audit logging, and anything else that reads the request's IP address -
Grimmory needs to know it can trust the `X-Forwarded-For` header your proxy sets.

## ⚠️ Security

**Only trust proxies you control.** Grimmory will use the right-most untrusted
`X-Forwarded-For` entry as the client's IP once a peer is trusted. If you trust an address
range that isn't actually your proxy, a client could spoof its own IP and dodge login rate
limiting or pollute the audit log.

## Does this affect you?

Grimmory enables Spring Boot's forwarded-header support by default
(`server.forward-headers-strategy: native`), with a trusted-proxy list that covers the
standard private IP ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) and loopback.
Every default Docker Compose bridge network falls inside `172.16.0.0/12`, so **if your
reverse proxy runs as a container on the same Compose network as Grimmory, this already
works with no configuration.**

You only need to configure anything if your proxy's peer address falls **outside** those
ranges - for example:

- The proxy runs with Docker host networking (`network_mode: host`) or a macvlan network.
- The proxy is an external load balancer or CDN edge (e.g. a cloud LB with a public or
  non-RFC1918 address) sitting in front of Grimmory.
- A Cloudflare Tunnel (or similar) sidecar is attached on a custom subnet.

**Symptom if this isn't configured correctly:** one user's failed login attempts can
trigger a 429 (`Too many failed login attempts`) for *every* user behind the same
untrusted proxy, since Grimmory falls back to treating the proxy's own address as the
client IP.

## Configuration

Set `INTERNAL_PROXIES` to a regex matching your proxy's address(es), **in addition to**
the default ranges (append with `|`, don't replace the default unless you know you want
to):

```bash
# Default Grimmory already trusts (RFC1918 ranges + loopback):
# 10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1

# Example: also trust a specific external load balancer at 203.0.113.5
INTERNAL_PROXIES=10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1|203\.0\.113\.5
```

### Docker Compose Example

```yaml
services:
  grimmory:
    image: ghcr.io/grimmory-tools/grimmory:latest
    environment:
      - INTERNAL_PROXIES=10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1|203\.0\.113\.5
    # ... rest of configuration ...
```

See also: [Forward Auth with Reverse Proxy](forward-auth-with-proxy.md) if your proxy is
also handling authentication (SSO/Authelia/Authentik) rather than just TLS termination.
