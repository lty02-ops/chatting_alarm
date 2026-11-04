#!/usr/bin/env python3
"""Upload OAuth keys from the repository .env without printing secret values."""
import json
import os
import shlex
import subprocess
import tempfile
from pathlib import Path


def main():
    env_file = Path(__file__).resolve().parents[2] / ".env"
    keys = (
        "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
        "KAKAO_CLIENT_ID", "KAKAO_CLIENT_SECRET",
        "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET",
    )
    values = {}
    for number, line in enumerate(env_file.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, separator, raw = line.partition("=")
        key = key.strip()
        if not separator or key not in keys:
            continue
        try:
            tokens = shlex.split(raw.strip(), comments=True, posix=True)
        except ValueError:
            raise SystemExit(f"Invalid quoting on line {number}; no values uploaded.") from None
        if len(tokens) != 1:
            raise SystemExit(f"Expected one nonempty value for {key}; no values uploaded.")
        values[key] = tokens[0]
    missing = [key for key in keys if not values.get(key)]
    if missing:
        raise SystemExit("Missing keys (no values uploaded): " + ", ".join(missing))
    # AWS CLI expects a regular JSON file. Restrict access and delete it afterward.
    with tempfile.TemporaryDirectory(prefix="chatting-alarm-secret-") as directory:
        payload = Path(directory) / "payload.json"
        descriptor = os.open(str(payload), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump({"SecretString": json.dumps(values)}, stream)
        result = subprocess.run(
            ["aws", "secretsmanager", "put-secret-value",
             "--secret-id", "chatting-alarm/dev/backend",
             "--region", "ap-northeast-2",
             "--cli-input-json", "file://" + str(payload),
             "--query", "VersionId", "--output", "text"],
            check=False,
        )
    if result.returncode:
        raise SystemExit(result.returncode)
    print("Backend secret saved successfully.")


if __name__ == "__main__":
    main()
