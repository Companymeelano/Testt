from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = "/home/user/Testt"


class PreviewHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/":
            self.path = "/design/proposals/dashboard-direction.html"
        super().do_GET()


server = ThreadingHTTPServer(
    ("0.0.0.0", 4173), partial(PreviewHandler, directory=ROOT)
)
server.serve_forever()
