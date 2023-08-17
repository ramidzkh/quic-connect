# Quic Connect

Connect to Minecraft servers using QUIC

## Usage

### For users

Put `quic://` at the beginning of the server hostname to only use Quic Connect

The mod follows this order when resolving:

1. If the hostname starts with `mincraft://`, do NOT use QUIC
2. If the hostname starts with `quic://`, use ONLY use QUIC
3. If the hostname has `_quic_connect._udp.` SRV record, follow if QUIC is preferred or forced

### For servers owners

In your `server.properties`, change `quic-port` to any other port.
Ensure that UDP port is available and accessible.
To use SRV records, make one under `_quic_connect._udp.hostname.`

QUIC requires TLS certificates to be used.
A PKCS#8 private key file in PEM format is required in `config/quic-connect/key.pem`
An X.509 certificate chain file in PEM format is required in `config/quic-connect/certificate.pem`

To generate self-signed certificates using OpenSSL:

1. `openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 -out key.pem`
2. `openssl req -key key.pem -new -x509 -days 365 -out certificate.pem`

## License

Everything is under the [LGPLv3][lgpl], but assets are under [CC BY-NC-SA 3.0][cc]

[lgpl]: https://spdx.org/licenses/LGPL-3.0-or-later.html

[cc]: https://creativecommons.org/licenses/by-nc-sa/3.0/
