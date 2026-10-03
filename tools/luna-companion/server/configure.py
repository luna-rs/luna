"""Create a local shared token without printing it or adding it to the repository."""
from pathlib import Path
import os
import secrets


def configure():
    directory = Path.home() / ".luna-companion"
    directory.mkdir(mode=0o700, exist_ok=True)
    path = directory / "token"
    if path.exists():
        print("An existing Luna Companion token was preserved.")
        return
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(secrets.token_urlsafe(36) + "\n")
    print("Created the Luna Companion token in your user profile. No token was printed.")


if __name__ == "__main__":
    configure()
