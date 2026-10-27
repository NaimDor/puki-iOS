"""The brain's files: what Puki.exe carries and unpacks, and what install.py copies into the
install folder (<folder>/brain), where Puki runs them. Every local module they import has to be
on this list (tests/test_bundle.py checks), or the brain fails to start where it's installed."""

FILES = ["bundle.py", "common.py", "doorbell.py", "brain.py", "persona.py", "safety.py", "reports.py", "packs.py", "memory.py", "pronunciation.py", "pronunciation.tsv", "tls.py", "pins.json",
         "dashboard.py", "dashboard.html", "qr.py", "parakeet.py", "install.py", "setup_gui.py", "tray.py",
         "config.example.json", "requirements.txt", "update_signers"]
