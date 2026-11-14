"""
Builds a DEMO-ONLY copy of the app for the emulator and installs it. Never publish this build.

What differs from the real app (only in the temporary copy, never in the project):
  - a second launcher icon, "Rider Demo", that opens the mock "3 Orders" screen as its own app
    (stands in for a real rider app on an emulator that has none);
  - the setup check counts that mock as the driver app, so Home reads "Ready" instead of a warning.

Run: python reel/build_demo.py     (emulator running). Restore the real app with: python reel/restore_app.py
"""
import io, os, re, shutil, subprocess, sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "demo_build")
ADB = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub1(s, old, new):
    assert s.count(old) == 1, ("expected one match", old[:80], s.count(old))
    return s.replace(old, new)


if os.path.exists(DST):
    shutil.rmtree(DST)
os.makedirs(os.path.join(DST, "public"))
shutil.copytree(os.path.join(PROJECT, "app"), os.path.join(DST, "app"))
for f in ("build.ps1", "debug.keystore"):
    shutil.copy(os.path.join(PROJECT, f), os.path.join(DST, f))

# icon for the mock rider app: a yellow disc with a car
car = re.search(r'android:pathData="([^"]+)"', rd(os.path.join(PROJECT, "app", "res", "drawable", "ic_car.xml"))).group(1)
wr(os.path.join(DST, "app", "res", "drawable", "demo_icon.xml"),
   '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" '
   'android:viewportWidth="108" android:viewportHeight="108">'
   '<path android:fillColor="#FFC61A" android:pathData="M54,54m-54,0a54,54 0 1,1 108,0a54,54 0 1,1 -108,0"/>'
   '<group android:scaleX="2.6" android:scaleY="2.6" android:translateX="23" android:translateY="23">'
   '<path android:fillColor="#111111" android:pathData="' + car + '"/></group></vector>')

man = os.path.join(DST, "app", "AndroidManifest.xml")
m = rd(man)
m = sub1(m, '<activity android:name=".SampleActivity" android:exported="false"/>',
         '<activity android:name=".SampleActivity" android:exported="false" android:taskAffinity="com.edvenswa.demo.rider"/>\n'
         '        <activity-alias android:name=".RiderDemo" android:targetActivity=".SampleActivity" android:exported="true"\n'
         '            android:label="Rider Demo" android:icon="@drawable/demo_icon">\n'
         '            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>\n'
         '        </activity-alias>')
wr(man, m)

ma = os.path.join(DST, "app", "src", "com", "edvenswa", "autoaccept", "MainActivity.java")
j = rd(ma)
j = sub1(j, "    boolean driverAppInstalled() {\n", "    boolean driverAppInstalled() {\n        if (true) return true; // DEMO BUILD ONLY: the mock rider app counts as the driver app\n")
wr(ma, j)

r = subprocess.run(["powershell", "-NoProfile", "-File", os.path.join(DST, "build.ps1")], cwd=DST, capture_output=True, text=True)
out = r.stdout + r.stderr
if "error:" in out or not os.path.exists(os.path.join(DST, "public", "auto-acceptor.apk")):
    print(out[-1500:]); sys.exit("demo build failed")

env = dict(os.environ, MSYS_NO_PATHCONV="1")
print(subprocess.run([ADB, "install", "-r", os.path.join(DST, "public", "auto-acceptor.apk")], capture_output=True, text=True, env=env).stdout.strip())
print("demo build installed (emulator only). The project's own APK is untouched.")
