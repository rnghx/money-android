#!/usr/bin/env python3
"""Cloud-only, pinned tools. No signing material is downloaded or generated."""
import hashlib, pathlib, subprocess, tarfile, zipfile

root = pathlib.Path.home() / '.local/share/money-cloud'
cache = pathlib.Path.home() / '.cache/money-cloud'
root.mkdir(parents=True, exist_ok=True)
cache.mkdir(parents=True, exist_ok=True)

def archive(url, digest, algorithm='sha256'):
    target = cache / url.rsplit('/', 1)[1]
    if not target.exists() or hashlib.new(algorithm, target.read_bytes()).hexdigest() != digest:
        subprocess.run(['curl', '-fL', '--retry', '2', '--max-time', '300', url, '-o', str(target)], check=True)
    if hashlib.new(algorithm, target.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Checksum mismatch: ' + target.name)
    return target

if not (root / 'jdk-17.0.21+9/bin/javac').exists():
    p = archive('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz', '8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711')
    with tarfile.open(p) as t: t.extractall(root, filter='data')
if not (root / 'node-v22.23.4-linux-x64/bin/node').exists():
    p = archive('https://nodejs.org/dist/v22.23.4/node-v22.23.4-linux-x64.tar.xz', '1805bcd35e6815559bd2eb97db438d3fdb0eb3a5e6a977b7c1badca76337e9ce')
    with tarfile.open(p) as t: t.extractall(root, filter='data')
sdk = root / 'android-sdk'
if not (sdk / 'cmdline-tools/19.0/bin/sdkmanager').exists():
    p = archive('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip', '5fdcc763663eefb86a5b8879697aa6088b041e70', 'sha1')
    with zipfile.ZipFile(p) as z: z.extractall(sdk / 'cmdline-tools')
    (sdk / 'cmdline-tools/cmdline-tools').rename(sdk / 'cmdline-tools/19.0')
    for p in (sdk / 'cmdline-tools/19.0/bin').iterdir(): p.chmod(0o755)
trust = root / 'cacerts'
if not trust.exists():
    import shutil
    shutil.copyfile(root / 'jdk-17.0.21+9/lib/security/cacerts', trust)
    subprocess.run([str(root / 'jdk-17.0.21+9/bin/keytool'), '-importcert', '-noprompt', '-alias', 'cloud-environment-proxy', '-file', '/usr/local/share/ca-certificates/environment-proxy-ca.crt', '-keystore', str(trust), '-storepass', 'changeit'], check=True)
print('Pinned tools ready. Source scripts/cloud-environment/env.sh; see docs/CLOUD-DEVELOPMENT.md for SDK installation.')
