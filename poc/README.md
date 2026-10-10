# HTTP gzip expansion PoC

`http_gzip_expansion.py` sends an HTTP POST whose gzip body expands to the
requested number of bytes. It is intended to demonstrate that Packmate's
current `HttpProcessor` has no decoded-body limit.

Start any TCP/HTTP listener on a port configured in Packmate as an HTTP service,
then send the request. For example:

```powershell
python -m http.server 8080
python poc/http_gzip_expansion.py --port 8080 --decompressed-bytes 100MiB
```

The HTTP listener may answer with an error; Packmate still captures the request.
The connection must complete or reach Packmate's stream timeout before Packmate
processes it. Watch the Packmate JVM's heap while increasing the value gradually.

The argument accepts exact bytes or `KiB`, `MiB`, `GiB`, `KB`, `MB`, `GB`.
It controls the decoded body only; the compressed request stays much smaller
because it consists of repeated `A` bytes. TCP is a byte stream, so the OS may
split the request across several network packets.

Run this only against a listener and Packmate instance you control.
