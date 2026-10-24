$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
New-Item -ItemType Directory -Force out/remote-test | Out-Null
$jdk = (Get-ChildItem out/setup/jdk -Directory | Select-Object -First 1).FullName
$sdk = (Resolve-Path out/setup/platforms/android-34-ext12/android.jar).Path
$source = Get-Content app/src/io/freemytv/RemoteKeys.java -Raw
$fragment = [regex]::Match($source, '(?s)        int keyCode = .*?(?=        boolean ours)').Value
if (!$fragment) { throw 'Remote key normalization block not found' }
$java = @"
import android.view.KeyEvent;
public class SonyRemoteTest {
 static final int KEY_YOUTUBE_TCL = 4065;
 static class Prefs { static final int KEY_YOUTUBE = 190, SONY_KEY = 100000; }
 static class Event {
  int key, scan; Event(int k, int s) {key=k;scan=s;}
  int getKeyCode() {return key;} int getScanCode() {return scan;}
 }
 static int normalize(boolean SONY, int key, int scan) {
  Event e = new Event(key,scan);
$fragment
  return keyCode;
 }
 static void check(boolean sony, int key, int scan, int expected) {
  int actual=normalize(sony,key,scan);
  if(actual!=expected) throw new AssertionError("key="+key+" scan="+scan+" expected="+expected+" actual="+actual);
 }
 public static void main(String[] args) {
  check(true,290,583,100583); // First TV Netflix, captured from the physical remote.
  check(true,289,485,100485); // First TV YouTube, captured from the physical remote.
  check(true,191,583,100583); // Older Sony Netflix.
  check(true,196,485,100485); // Older Sony YouTube.
  check(true,195,587,100587); // Older Sony Google Play.
  check(false,290,583,290);  // Other brands keep their codes.
  check(true,24,583,24);     // Volume is never remapped.
  System.out.println("Sony remote: 7 checks passed");
 }
}
"@
Set-Content out/remote-test/SonyRemoteTest.java $java -Encoding utf8
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $sdk -d out/remote-test out/remote-test/SonyRemoteTest.java
if ($LASTEXITCODE -ne 0) { throw 'Remote test compilation failed' }
& "$jdk/bin/java.exe" -cp "out/remote-test;$sdk" SonyRemoteTest
if ($LASTEXITCODE -ne 0) { throw 'Sony remote regression test failed' }
