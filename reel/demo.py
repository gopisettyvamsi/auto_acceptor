"""
One continuous take for the Reel, on the emulator running the DEMO build (reel/build_demo.py):
  1  open Vamsify from the phone's home screen
  2  turn on auto-accept, set the filters and the response delay
  3  switch to the rider app (the mock "Rider Demo")
  4  chips on every card, with the reason for each
  5  auto-accept takes the right ride and shows the trip card
Touches are shown as dots. Output: reel/clips/full_demo.mp4 (+ check stills).
Run: python reel/demo.py
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "clips")
os.makedirs(OUT, exist_ok=True)
APP = "com.edvenswa.autoaccept"
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, env=ENV)


def sh(*a):
    return adb("shell", *a)


def tap(x, y):
    sh("input", "tap", str(int(x)), str(int(y)))


def nodes():
    sh("uiautomator", "dump", "/sdcard/u.xml")
    xml = adb("exec-out", "cat", "/sdcard/u.xml").stdout.decode("utf-8", "ignore")
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            out.append((n.get("text", ""), n.get("content-desc", ""), tuple(int(v) for v in m.groups())))
    return out


def find(label, tries=8):
    """Centre of the first node whose text or content description equals label."""
    for attempt in range(tries):
        for text, desc, (l, t, r, b) in nodes():
            if label.lower() in (text.strip().lower(), desc.strip().lower()):
                return (l + r) // 2, (t + b) // 2
        time.sleep(1.2)
    raise SystemExit("not found on screen: " + label)


def go(label, wait=1.6):
    tap(*find(label))
    time.sleep(wait)


def shot(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p").stdout)


def back(wait=1.0):
    sh("input", "keyevent", "4")
    time.sleep(wait)


def type_into(label, value):
    x, y = find(label)
    tap(x, y + 120)                       # the box under its label
    time.sleep(0.6)
    for _ in range(6):
        sh("input", "keyevent", "67")     # clear what is there
    sh("input", "text", str(value))
    time.sleep(0.7)
    back(0.6)                             # closes the keyboard


def ensure_service():
    """Android switches an accessibility service off when its app is force-stopped, so turn it on last and check."""
    svc = APP + "/" + APP + ".AcceptService"
    for _ in range(5):
        sh("settings", "put", "secure", "enabled_accessibility_services", svc)
        sh("settings", "put", "secure", "accessibility_enabled", "1")
        sh("am", "force-stop", APP)
        time.sleep(2)
        sh("settings", "put", "secure", "enabled_accessibility_services", svc)
        sh("am", "start", "-n", APP + "/.MainActivity")
        time.sleep(7)
        # ask Android itself: the Home screen drawn at launch can be stale until the service finishes connecting
        if "label=Vamsify" in sh("dumpsys", "accessibility").stdout.decode("utf-8", "ignore"):
            return
        sh("settings", "put", "secure", "enabled_accessibility_services", "")
        time.sleep(2)
    raise SystemExit("the accessibility service would not turn on")


def use_light_mode():
    """Settings > Display & language > turn Dark mode off, then save (the app redraws itself)."""
    go("Settings", 1.8)
    go("Display & language", 2.2)
    go("Dark mode", 1.0)                  # the checkbox is on by default; one tap turns it off
    go("Save changes", 3.5)               # the screen is redrawn in light mode


def fresh_state():
    """First-run state: nothing saved, set up, service on, battery unrestricted."""
    sh("pm", "clear", APP)
    sh("am", "start", "-n", APP + "/.MainActivity")
    time.sleep(6)
    for label in ("Continue", "Continue", "Continue", "Get started"):
        go(label, 2.2)
    try:
        go("Allow", 2.5)                  # the notification permission, when Android asks
    except SystemExit:
        pass
    ensure_service()
    use_light_mode()                      # the video is recorded in light mode; this happens before recording starts
    sh("dumpsys", "deviceidle", "whitelist", "+" + APP)   # "battery unrestricted", as a real driver would set it
    sh("settings", "put", "system", "show_touches", "1")
    sh("input", "keyevent", "3")          # the phone's home screen
    time.sleep(3)


class Take:
    """One screenrecord session that can be ended as soon as the steps are done."""
    def __init__(self, name, limit=170):
        self.name, self.limit = name, limit

    def __enter__(self):
        sh("rm", "-f", "/sdcard/take.mp4")
        self.p = subprocess.Popen([ADB, "shell", "screenrecord", "--size", "1080x2400", "--bit-rate", "12000000",
                                   "--time-limit", str(self.limit), "/sdcard/take.mp4"], env=ENV,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.2)
        return self

    def __exit__(self, *e):
        time.sleep(1.0)
        sh("pkill", "-2", "screenrecord")   # finish the file properly instead of waiting out the limit
        try:
            self.p.wait(timeout=20)
        except subprocess.TimeoutExpired:
            self.p.kill()
        time.sleep(1.5)
        adb("pull", "/sdcard/take.mp4", os.path.join(OUT, self.name + ".mp4"))


def main():
    fresh_state()
    icon = find("Vamsify")                    # looked up before recording, so the take opens without dead air
    shot("demo_1_launcher")
    with Take("full_demo"):
        # 1) the phone's home screen, then open Vamsify from it
        time.sleep(2.2)
        tap(*icon)
        time.sleep(3.2)
        shot("demo_1b_home")

        # 2) auto-accept on, then the filters and the response delay
        go("Auto-accept", 2.5)
        shot("demo_2a_auto_on")
        go("Filters", 2.5)
        type_into("Minimum fare (₹)", 30)
        type_into("Maximum pickup distance (km)", 2)
        type_into("Minimum earnings per km (₹)", 9)
        shot("demo_2b_filters")
        go("Save changes", 2.2)
        go("Accept speed", 2.4)
        type_into("Response delay (seconds)", 6)   # long enough to see the chips before the tap
        go("Save changes", 2.2)

        # 3) leave Vamsify and open the rider app from the app drawer
        sh("input", "keyevent", "3")
        time.sleep(2.2)
        sh("input", "swipe", "672", "2500", "672", "700", "350")
        time.sleep(2.0)
        shot("demo_3_drawer")
        go("Rider Demo", 5.0)

        # 4) chips on every card, with the reason for each (the response delay keeps them on screen before the tap)
        shot("demo_4_chips")
        time.sleep(2.0)
        shot("demo_4b_chips_pending")

        # 5) auto-accept takes the right ride: the trip card appears after the response delay
        time.sleep(2.5)
        shot("demo_5_trip_card")
        time.sleep(3.2)
        tap(951, 2005)                        # OK on the trip card (an overlay the UI dump cannot see)
        time.sleep(2.2)
    print("done: full_demo.mp4")


if __name__ == "__main__":
    main()
