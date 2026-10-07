"""Deterministic Open-Meteo-compatible response for opt-in live-stack E2E."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        payload = {
            "timezone": "Asia/Kolkata",
            "current": {
                "time": "2026-10-06T12:00",
                "temperature_2m": 25.0,
                "relative_humidity_2m": 40,
                "precipitation": 0.0,
                "weather_code": 1,
                "pressure_msl": 1013.0,
                "surface_pressure": 1004.0,
                "cloud_cover": 15,
                "dew_point_2m": 12.0,
                "wind_speed_10m": 3.0,
                "wind_direction_10m": 120,
                "wind_gusts_10m": 5.0,
                "visibility": 10000.0,
                "uv_index": 2.0,
            },
        }
        content = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, _format, *_args):
        return


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8100
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
