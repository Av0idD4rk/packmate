import gzip
import importlib.util
from pathlib import Path
import socket
import threading
import unittest


SCRIPT_PATH = Path(__file__).with_name("http_gzip_expansion.py")
SPEC = importlib.util.spec_from_file_location("http_gzip_expansion", SCRIPT_PATH)
POC = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(POC)


class HttpGzipExpansionTest(unittest.TestCase):
    def test_configured_decoded_size(self):
        request, compressed_size = POC.build_request("localhost:8080", "/test", 3_500_123)
        headers, compressed = request.split(b"\r\n\r\n", 1)

        self.assertEqual(compressed_size, len(compressed))
        self.assertIn(f"Content-Length: {len(compressed)}".encode(), headers)
        self.assertIn(b"X-Packmate-Decoded-Bytes: 3500123", headers)
        self.assertEqual(3_500_123, len(gzip.decompress(compressed)))

    def test_suffixes(self):
        self.assertEqual(100 * 1024**2, POC.byte_count("100MiB"))
        self.assertEqual(10_000_000, POC.byte_count("10MB"))
        self.assertEqual(4096, POC.byte_count("4096"))

    def test_sends_complete_request_to_tcp_listener(self):
        received = bytearray()
        ready = threading.Event()
        address = []

        def server():
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen(1)
                address.append(listener.getsockname())
                ready.set()
                connection, _ = listener.accept()
                with connection:
                    while chunk := connection.recv(65536):
                        received.extend(chunk)

        thread = threading.Thread(target=server, daemon=True)
        thread.start()
        self.assertTrue(ready.wait(2))

        request, _ = POC.build_request("localhost", "/test", 2 * 1024**2)
        POC.send_request(address[0][0], address[0][1], request, 2)
        thread.join(2)

        self.assertFalse(thread.is_alive())
        self.assertEqual(request, bytes(received))


if __name__ == "__main__":
    unittest.main()
