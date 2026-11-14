"""Puts the real app (public/auto-acceptor.apk) back on the emulator after a demo recording, and turns the touch dots off."""
import os, subprocess

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
env = dict(os.environ, MSYS_NO_PATHCONV="1")
subprocess.run([ADB, "shell", "settings", "put", "system", "show_touches", "0"], env=env)
print(subprocess.run([ADB, "install", "-r", os.path.join(PROJECT, "public", "auto-acceptor.apk")], capture_output=True, text=True, env=env).stdout.strip())
