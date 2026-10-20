#!/usr/bin/env python3
"""Exercise the packaged addon in disposable, normally installed NeoForge games.

Requires Java 21, Python 3, and an available graphical desktop (or Xvfb).
Run ./gradlew preparePackagedVerification first. All game files stay under run/neoforge.
This does not use the user's launcher profile, credentials, or personal worlds.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'run/neoforge'
REPORTS = ROOT / 'build/neoforge/reports/runtime'
PROPERTIES = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
NEO = PROPERTIES['neo_version']
MC = PROPERTIES['minecraft_version']
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java')
OS = {'Darwin': 'osx', 'Linux': 'linux', 'Windows': 'windows'}[platform.system()]
INSTALLER_SHA256 = '311475c8315ed0be6b5f1dbbf5a377b6c0976457c0bd5aa6d19b0fe25fd77148'
SUITES = ('core', 'features', 'review-regressions', 'lifecycle', 'restart', 'integrated', 'emi', 'jei')


def sha(path, algorithm='sha256'):
    return hashlib.new(algorithm, path.read_bytes()).hexdigest()


RUNNER_SHA256 = sha(Path(__file__))


def download(url, path, expected=None, algorithm='sha1'):
    if path.is_file() and (expected is None or sha(path, algorithm) == expected):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.download')
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=90) as response, temporary.open('wb') as out:
                shutil.copyfileobj(response, out)
            if expected and sha(temporary, algorithm) != expected:
                raise RuntimeError(f'Checksum mismatch: {url}')
            temporary.replace(path)
            return
        except Exception:
            temporary.unlink(missing_ok=True)
            if attempt == 2:
                raise
            time.sleep(1)


def installed(side):
    game = BASE / f'production-{side}'
    expected = game / (f'libraries/net/neoforged/neoforge/{NEO}/unix_args.txt' if side == 'server'
                       else f'versions/neoforge-{NEO}/neoforge-{NEO}.json')
    if expected.is_file():
        return game
    if NEO != '21.1.233':
        raise RuntimeError('Update the pinned installer checksum before certifying a different loader.')
    installer = BASE / f'production-install/neoforge-{NEO}-installer.jar'
    download(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEO}/neoforge-{NEO}-installer.jar',
             installer, INSTALLER_SHA256, 'sha256')
    game.mkdir(parents=True, exist_ok=True)
    if side == 'client' and not (game / 'launcher_profiles.json').exists():
        (game / 'launcher_profiles.json').write_text('{"profiles":{}}\n')
    with (REPORTS / f'install-{side}.log').open('w') as log:
        subprocess.run([JAVA, '-jar', str(installer), '--install' + side.title(), str(game)],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=600)
    if not expected.is_file():
        raise RuntimeError(f'Installer did not create {expected}')
    return game


def allowed(rules):
    if not rules:
        return True
    result = False
    for rule in rules:
        if rule.get('features'):
            continue  # No account, demo, quick-play, or launcher feature flags in this fixture.
        os_rule = rule.get('os', {})
        if os_rule.get('name', OS) != OS:
            continue
        if 'arch' in os_rule and os_rule['arch'] != platform.machine().replace('arm64', 'aarch64'):
            continue
        if 'version' in os_rule and not re.search(os_rule['version'], platform.release()):
            continue
        result = rule['action'] == 'allow'
    return result


def client_command(game, flags, name='WRBCoreQA'):
    vanilla = json.loads((game / f'versions/{MC}/{MC}.json').read_text())
    neo = json.loads((game / f'versions/neoforge-{NEO}/neoforge-{NEO}.json').read_text())
    libraries = {}
    for lib in vanilla['libraries'] + neo['libraries']:
        if allowed(lib.get('rules')):
            parts = lib['name'].split(':')
            libraries[tuple(parts[:2] + parts[3:])] = lib
    files = []
    downloads = []
    for lib in libraries.values():
        artifact = lib.get('downloads', {}).get('artifact')
        if not artifact:
            raise RuntimeError(f'Missing artifact download metadata: {lib["name"]}')
        path = game / 'libraries' / artifact['path']
        files.append(path)
        downloads.append((artifact['url'], path, artifact.get('sha1')))
    assets = Path(os.environ.get('WRB_ASSETS_DIR', str(Path.home() / '.gradle/caches/neoformruntime/assets')))
    index = vanilla['assetIndex']
    index_path = assets / 'indexes' / (index['id'] + '.json')
    # Reuse a populated Gradle cache read-only; otherwise install assets in the disposable game.
    if not index_path.is_file():
        assets = game / 'assets'
        index_path = assets / 'indexes' / (index['id'] + '.json')
        download(index['url'], index_path, index['sha1'])
    if sha(index_path, 'sha1') != index['sha1']:
        raise RuntimeError('Asset index checksum mismatch')
    for entry in json.loads(index_path.read_text())['objects'].values():
        h = entry['hash']
        path = assets / 'objects' / h[:2] / h
        if not path.is_file():
            if assets != game / 'assets':
                raise RuntimeError('Cached assets are incomplete; set WRB_ASSETS_DIR to a complete cache or use an empty path.')
            downloads.append((f'https://resources.download.minecraft.net/{h[:2]}/{h}', path, h))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda item: download(*item), downloads))
    files.append(game / f'versions/{MC}/{MC}.jar')
    offline = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
    offline[6] = (offline[6] & 15) | 48
    offline[8] = (offline[8] & 63) | 128
    variables = dict(version_name=f'neoforge-{NEO}', library_directory=str(game / 'libraries'),
                     classpath_separator=os.pathsep, classpath=os.pathsep.join(map(str, files)),
                     natives_directory=str(game / 'natives'), launcher_name='WRB packaged verification',
                     launcher_version='1', auth_player_name=name, game_directory=str(game),
                     assets_root=str(assets), assets_index_name=index['id'],
                     auth_uuid=str(uuid.UUID(bytes=bytes(offline))), auth_access_token='0',
                     clientid='', auth_xuid='', user_type='legacy', version_type='release')
    (game / 'natives').mkdir(exist_ok=True)

    def expand(values):
        result = []
        for value in values:
            if isinstance(value, dict):
                if not allowed(value.get('rules')):
                    continue
                value = value['value']
            for part in value if isinstance(value, list) else [value]:
                result.append(re.sub(r'\$\{([^}]+)}', lambda m: variables[m[1]], part))
        return result

    # Unlike the official launcher, this runner keeps the inherited vanilla JAR's
    # original filename. Exclude it from NeoForge's module discovery by that name.
    return ([JAVA, '-Xmx3G'] + flags + expand(vanilla['arguments']['jvm']) + expand(neo['arguments']['jvm'])
            + [f'-DignoreList=client-extra,{MC}.jar']
            + [neo['mainClass']] + expand(vanilla['arguments']['game']) + expand(neo['arguments']['game']))


def launch(command, game, log_path):
    log = log_path.open('w')
    try:
        return subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    finally:
        log.close()


def await_ready(process, log):
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Server exited before readiness; see {log}')
        if 'WRB_CORE waiting for QA client' in log.read_text(errors='replace'):
            return
        time.sleep(.5)
    raise RuntimeError(f'Server did not become ready; see {log}')


def finish(process, seconds=240):
    try:
        code = process.wait(timeout=seconds)
    except subprocess.TimeoutExpired:
        raise RuntimeError('Game verification timed out')
    if code != 0:
        raise RuntimeError(f'Game exited with code {code}')


def read_result(path, expected, suite):
    if not path.is_file():
        raise RuntimeError(f'Missing fresh report: {path}')
    report = json.loads(path.read_text())
    if report.get('passed') is not True or report.get('artifactSha256') != expected or report.get('suite') != suite:
        raise RuntimeError(f'Invalid runtime evidence in {path}: {report.get("error", report)}')
    if not report.get('checks'):
        raise RuntimeError(f'Empty verification report: {path}')
    return report


def run(suite):
    if sha(Path(__file__)) != RUNNER_SHA256:
        raise RuntimeError('The runtime runner changed during execution; restart verification with the saved file.')
    inputs = json.loads((ROOT / 'build/neoforge/reports/release/runtime-inputs.json').read_text())
    artifact = Path(inputs['artifact'])
    if sha(artifact) != inputs['sha256']:
        raise RuntimeError('Artifact changed since preparePackagedVerification')
    server = installed('server')
    client = installed('client')
    matrix_path = REPORTS / 'matrix.json'
    matrix = json.loads(matrix_path.read_text()) if matrix_path.exists() else {}
    runner_hash = sha(Path(__file__))
    if (matrix.get('sha256') != inputs['sha256'] or matrix.get('fixtureSha256') != inputs['fixtureSha256']
            or matrix.get('runnerSha256') != runner_hash or matrix.get('dependencies') != inputs['dependencies']):
        matrix = dict(sha256=inputs['sha256'], minecraft=MC, neoforge=NEO,
                      fixtureSha256=inputs['fixtureSha256'], runnerSha256=runner_hash,
                      dependencies=inputs['dependencies'], suites={})
    # A failed rerun must invalidate a previous success before launching anything.
    matrix['passed'] = False
    matrix['suites'].pop(suite, None)
    matrix_path.write_text(json.dumps(matrix, indent=2) + '\n')
    (REPORTS / f'{suite}.json').unlink(missing_ok=True)
    for game in (server, client):
        staged = game / 'mods' / artifact.name
        if not staged.is_file() or sha(staged) != inputs['sha256']:
            raise RuntimeError('Run ./gradlew preparePackagedVerification before starting the games')
        expected_mods = dict(inputs['dependencies'], **{inputs['fixture']: inputs['fixtureSha256']})
        for name, digest in expected_mods.items():
            if not (game / 'mods' / name).is_file() or sha(game / 'mods' / name) != digest:
                raise RuntimeError(f'Staged verification dependency differs: {game / "mods" / name}')
        for name in inputs['viewers']:
            (game / 'mods' / name).unlink(missing_ok=True)
        extras = {p.name for p in (game / 'mods').glob('*.jar')} - set(expected_mods) - {artifact.name}
        if extras:
            raise RuntimeError(f'Unexpected mods in disposable fixture: {extras}')
        (game / 'core-verification.json').unlink(missing_ok=True)
        (game / 'integrated-server-verification.json').unlink(missing_ok=True)
        (game / '.wrb-disposable-verification').write_text('Managed verification fixture; not a personal world.\n')
        # Fzzy saves received server settings in the disposable client profile.
        # Reset them so running integrated immediately after core cannot inherit
        # core's temporary native-workbench tier override.
        config = game / 'config/taczweaponblueprints'
        if config.exists():
            shutil.rmtree(config)
    viewer = {}
    if suite in ('emi', 'jei'):
        viewer = {name: digest for name, digest in inputs['viewers'].items() if name.startswith(suite + '-')}
        if len(viewer) != 1:
            raise RuntimeError(f'Expected one pinned {suite} runtime')
        for name, digest in viewer.items():
            source = BASE / 'verification-viewers' / name
            if sha(source) != digest:
                raise RuntimeError(f'Viewer dependency checksum mismatch: {name}')
            shutil.copy2(source, client / 'mods' / name)
    peer = None
    if suite in ('lifecycle', 'restart'):
        peer = BASE / 'production-peer'
        peer.mkdir(exist_ok=True)
        for directory in ('versions', 'libraries'):
            link = peer / directory
            if not link.exists():
                link.symlink_to(client / directory, target_is_directory=True)
            if link.resolve() != (client / directory).resolve():
                raise RuntimeError(f'Unexpected peer library location: {link}')
        if (peer / 'mods').exists():
            shutil.rmtree(peer / 'mods')
        shutil.copytree(client / 'mods', peer / 'mods')
        (peer / 'core-verification.json').unlink(missing_ok=True)
        if (peer / 'config/taczweaponblueprints').exists():
            shutil.rmtree(peer / 'config/taczweaponblueprints')
    if suite == 'restart':
        previous = matrix['suites'].get('lifecycle', {})
        snapshot = server / 'lifecycle-expected.snbt'
        if (not previous.get('passed') or not snapshot.is_file()
                or previous.get('savedStateSha256') != sha(snapshot)):
            raise RuntimeError('Run the lifecycle suite successfully with this artifact and fixture before restart.')
    world_name = f'packaged-{"lifecycle" if suite == "restart" else suite}-world'
    if suite not in ('restart', 'integrated'):
        # Every scenario starts independently. Only restart consumes a prior
        # scenario's saved world; integrated opens a copy of the completed core world.
        world = server / world_name
        if world.exists():
            shutil.rmtree(world)
    if suite == 'integrated':
        source = server / 'packaged-core-world'
        target = client / 'saves/packaged-integrated-world'
        if not matrix['suites'].get('core', {}).get('passed') or not (source / 'level.dat').is_file():
            raise RuntimeError('Run the core suite first to create the disposable world used by integrated verification.')
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(source, target, ignore=shutil.ignore_patterns('session.lock'))
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port=25594
online-mode=false
level-name={world_name}
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
view-distance=3
simulation-distance=3
max-players=4
motd=WRB packaged verification
''')
    for game in (client, peer) if peer else (client,):
        (game / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\nmaxFps:60\n')
    flags = ['-Dwrb.verifyPackaged=true', '-Dwrb.expectedArtifactHash=' + inputs['sha256']]
    actual_suite = suite
    if suite in ('features', 'emi', 'jei'):
        flags.append('-Dwrb.verifyFeatures=true')
        actual_suite = 'features'
    if suite == 'review-regressions':
        flags += ['-Dwrb.verifyReviewFixes=true', '-Dwrb.reviewServerDirectory=' + str(server)]
    if suite in ('lifecycle', 'restart', 'integrated'):
        flags.append('-Dwrb.verify' + suite.title() + '=true')
    server_log = REPORTS / f'{suite}-server.log'
    client_log = REPORTS / f'{suite}-client.log'
    client_cmd = client_command(client, flags)
    server_cmd = [JAVA, '-Xmx2G'] + flags + ['@user_jvm_args.txt', f'@libraries/net/neoforged/neoforge/{NEO}/unix_args.txt', '--nogui']
    processes = []
    try:
        if suite != 'integrated':
            print(f'Starting packaged {suite} server', flush=True)
            processes.append(launch(server_cmd, server, server_log))
            if suite == 'review-regressions':
                finish(processes[0])
            else:
                await_ready(processes[0], server_log)
        print(f'Starting packaged {suite} client', flush=True)
        processes.append(launch(client_cmd, client, client_log))
        main_client = processes[-1]
        if peer:
            print(f'Starting packaged {suite} second player', flush=True)
            processes.append(launch(client_command(peer, flags, 'WRBPeerQA'), peer, REPORTS / f'{suite}-peer.log'))
        finish(main_client, 480)
        for process in processes:
            finish(process, 45)
        result = dict(passed=True,
                      server=read_result(client / 'integrated-server-verification.json' if suite == 'integrated'
                                         else server / 'core-verification.json', inputs['sha256'], actual_suite),
                      client=read_result(client / 'core-verification.json', inputs['sha256'], actual_suite),
                      dependencies=inputs['dependencies'], viewer=viewer, fixtureSha256=inputs['fixtureSha256'])
        if peer:
            result['peer'] = read_result(peer / 'core-verification.json', inputs['sha256'], actual_suite)
        if suite == 'lifecycle':
            result['savedStateSha256'] = sha(server / 'lifecycle-expected.snbt')
        (REPORTS / f'{suite}.json').write_text(json.dumps(result, indent=2) + '\n')
        matrix['suites'][suite] = result
        matrix['passed'] = all(matrix['suites'].get(key, {}).get('passed') is True for key in SUITES)
        matrix_path.write_text(json.dumps(matrix, indent=2) + '\n')
        print(f'PASS {suite}: {len(result["server"]["checks"])} server checks, {len(result["client"]["checks"])} client checks', flush=True)
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=[*SUITES, 'all'], default='core')
    args = parser.parse_args()
    if not JAVA:
        raise SystemExit('Set JAVA_HOME to a Java 21 installation')
    version = subprocess.run([JAVA, '-version'], capture_output=True, text=True, check=True)
    if not re.search(r'version "21[.\"]', version.stderr + version.stdout):
        raise SystemExit('The packaged runtime matrix requires Java 21')
    REPORTS.mkdir(parents=True, exist_ok=True)
    for suite in SUITES if args.suite == 'all' else (args.suite,):
        run(suite)
