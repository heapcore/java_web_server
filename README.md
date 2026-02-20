# java_web_server

> **WARNING:** This repository may be unstable or non-functional. Use at your own risk.

Minimal educational HTTP server in Java.

The server listens on port `8080`, serves files from the local `web/` directory,
and supports basic static file responses.

## Run

1. Build:
   `mvn compile`
2. Run:
   `mvn exec:java -Dexec.mainClass=com.javawebserver.Main`
3. Optional custom port:
   `mvn exec:java -Dexec.mainClass=com.javawebserver.Main -Dexec.args="9090"`

## Notes

- This is a legacy learning project and may be incomplete.
- `GET` is implemented for static files.
- `HEAD` is implemented as `GET` without response body.
- `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` are recognized and return
  `501 Not Implemented` JSON stubs.

## License

See `LICENSE`.
