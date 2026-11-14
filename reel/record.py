"""
Records the demo clips for the Instagram Reel from the emulator, one scene per clip.
Run: python reel/record.py          (emulator running, Vamsify installed and set up)
Clips land in reel/clips/. A check screenshot is saved next to each clip.
"""
import os, re, subprocess, sys, time

ADB = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "clips")
os.makedirs(OUT, exist_ok=True)
APP = "com.edvenswa.autoaccept"
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*a, capture=True):
    return subprocess.run([ADB, *a], capture_output=capture, env=ENV)


def sh(*a):
    return adb("shell", *a)


def tap(x, y):
    sh("input", "tap", str(int(x)), str(int(y)))


def screen_xml():
    sh("uiautomator", "dump", "/sdcard/u.xml")
    return adb("exec-out", "cat", "/sdcard/u.xml").stdout.decode("utf-8", "ignore")


def find(label, tries=6):
    """Centre of the first node whose text equals label."""
    for attempt in range(tries):
        if attempt == 2:                      # not on screen yet: the app may have been pushed out by a low-memory emulator
            sh("am", "start", "-n", APP + "/.MainActivity")
            time.sleep(3)
        for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', screen_xml()):
            if m.group(1).strip().lower() == label.lower():
                return (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2
        time.sleep(1.2)
    raise SystemExit("not found on screen: " + label)


def go(label, wait=1.6):
    x, y = find(label)
    tap(x, y)
    time.sleep(wait)


def shot(name):
    png = adb("exec-out", "screencap", "-p").stdout
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(png)


def back(wait=1.4):
    sh("input", "keyevent", "4")
    time.sleep(wait)


def home():
    sh("am", "start", "-n", APP + "/.MainActivity")
    time.sleep(2.5)
    go("Home", 1.5)


class Rec:
    """screenrecord on the device; pulls the file when the block ends."""
    def __init__(self, name, seconds, size="1080x2400"):
        self.name, self.seconds, self.size = name, seconds, size

    def __enter__(self):
        sh("rm", "-f", "/sdcard/clip.mp4")
        self.p = subprocess.Popen([ADB, "shell", "screenrecord", "--size", self.size, "--bit-rate", "12000000",
                                   "--time-limit", str(self.seconds), "/sdcard/clip.mp4"], env=ENV,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.0)  # the recorder needs a moment before it is capturing
        return self

    def __exit__(self, *e):
        self.p.wait()
        time.sleep(1.0)
        adb("pull", "/sdcard/clip.mp4", os.path.join(OUT, self.name + ".mp4"))


def set_min_fare(value):
    go("Settings", 1.5)
    go("Ride filters", 2)
    x, y = find("Minimum fare (₹)")
    tap(x, y + 120)                      # the box under its label
    time.sleep(0.6)
    for _ in range(6):
        sh("input", "keyevent", "67")    # clear
    sh("input", "text", str(value))
    time.sleep(0.4)
    back(0.9)                            # closes the keyboard
    go("Save changes", 1.8)
    go("Home", 1.5)


def set_auto(on):
    xml = screen_xml()
    m = re.search(r'content-desc="Auto-accept"[^>]*checked="(true|false)"', xml) or \
        re.search(r'checked="(true|false)"[^>]*content-desc="Auto-accept"', xml)
    cur = m and m.group(1) == "true"
    if bool(cur) != on:
        tap(1170, 1170)
        time.sleep(1.8)


def ensure_service():
    """The sample screen only opens when the accessibility service is bound; a rough emulator boot can leave it off."""
    svc = APP + "/" + APP + ".AcceptService"
    for attempt in range(5):
        sh("settings", "put", "secure", "enabled_accessibility_services", svc)
        sh("settings", "put", "secure", "accessibility_enabled", "1")
        sh("am", "force-stop", APP)
        time.sleep(2)
        sh("settings", "put", "secure", "enabled_accessibility_services", svc)
        sh("am", "start", "-n", APP + "/.MainActivity")
        time.sleep(7)
        if "Off. Tap to turn it on" not in screen_xml():
            print("service on (attempt %d)" % (attempt + 1))
            return
        sh("settings", "put", "secure", "enabled_accessibility_services", "")
        time.sleep(2)
    raise SystemExit("the accessibility service would not turn on")


def reset():
    """Back to a known state: Home, auto-accept off, no fare limit."""
    # no force-stop here: Android switches an accessibility service off when its app is force-stopped
    sh("am", "start", "-n", APP + "/.MainActivity")
    time.sleep(3)
    go("Home", 1.5)
    set_auto(False)


def scene1():
    set_auto(False)
    go("Sample ride", 9)
    with Rec("1_hook_chips_on_every_card", 11):
        time.sleep(2.5)
        shot("1_hook_chips_on_every_card")
        sh("input", "swipe", "700", "2100", "700", "900", "1800")   # slow scroll to the next cards
        time.sleep(2.5)
        sh("input", "swipe", "700", "900", "700", "2100", "1800")
        time.sleep(2.0)
    back()


def scene2():
    set_min_fare(40)
    set_auto(True)
    go("Sample ride", 9)
    with Rec("2_why_not_accepted", 9):
        time.sleep(2.0)
        shot("2_why_not_accepted")
        time.sleep(5.0)
    back()
    set_auto(False)
    set_min_fare(0)


def scene3():
    set_auto(True)
    sx, sy = find("Sample ride")
    with Rec("3_auto_accept_and_trip_card", 18):
        time.sleep(1.5)
        tap(sx, sy)
        time.sleep(6.5)
        shot("3_auto_accept_and_trip_card")
        time.sleep(3.5)
        tap(951, 2005)                   # OK on the trip card: an overlay the UI dump cannot see, so tapped by position
        time.sleep(3.0)
    back()
    set_auto(False)


def scene4():
    go("Settings", 1.8)
    go("Chip style", 2.5)
    pts = {k: find(k) for k in ("Simple", "Standard", "Large", "Medium")}
    back(1.5)
    with Rec("4_chip_style_settings", 12):
        time.sleep(1.5)
        go("Chip style", 2.2)
        tap(*pts["Simple"]); time.sleep(1.4)
        tap(*pts["Large"]); time.sleep(1.8)
        shot("4_chip_style_settings")
        tap(*pts["Standard"]); time.sleep(1.2)
        tap(*pts["Medium"]); time.sleep(1.8)
    back(); back()


def main(want):
    ensure_service()
    reset()
    for n, fn in (("1", scene1), ("2", scene2), ("3", scene3), ("4", scene4)):
        if n in want:
            fn()
    print("done:", ", ".join(sorted(f for f in os.listdir(OUT) if f.endswith(".mp4"))))


if __name__ == "__main__":
    main(set(sys.argv[1:]) or {"1", "2", "3", "4"})
