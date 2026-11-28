#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""通驿物流系统 · 一键初始化（Python 3.9+，仅标准库）。

只做配置和环境准备：检查 Java / Node.js、准备 MySQL 3308、
初始化或增量升级数据库、安装前端依赖并按需构建。
不会启动 Spring Boot，也不会打开浏览器；启动请使用 start.cmd / start.ps1。

除显式传入 --reset-db 外，本脚本不会 DROP 或重建数据库，也不会清空业务数据。
"""

import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WEB_DIR = ROOT / "web"
STATIC_DIR = ROOT / "src" / "main" / "resources" / "static"
STATIC_INDEX = STATIC_DIR / "index.html"
ENV_LOCAL = WEB_DIR / ".env.local"
ENV_EXAMPLE = WEB_DIR / ".env.example"

DB_HOST = "127.0.0.1"
DB_PORT = 3308
DB_NAME = "logistics"
DB_USER = "root"
DB_PASSWORD = "123456"
DB_CONTAINER = "logistics-mysql"
DB_IMAGE = "mysql:8.0"

SCHEMA_SQL = "sql/schema.sql"
SEED_SQL = "sql/seed.sql"
ALTER_PRICING_SQL = "sql/alter_pricing.sql"
ALTER_FINAL_SIX_SQL = "sql/alter_final_six.sql"
ALTER_TRACK_SQL = "sql/alter_track_map.sql"
ALTER_TRACK_GCJ_SQL = "sql/alter_track_map_gcj02.sql"

KEY_TABLES = (
    "station",
    "route_edge",
    "operator",
    "postman",
    "delivery_order",
    "order_route",
)

TOTAL_STEPS = 7


class InitError(Exception):
    def __init__(self, message, next_step=""):
        super(InitError, self).__init__(message)
        self.message = message
        self.next_step = next_step


class State(object):
    def __init__(self):
        self.step = 0
        self.java_summary = "未检查"
        self.node_summary = "未检查"
        self.npm_summary = "未检查"
        self.npm_path = ""
        self.db_desc = "127.0.0.1:{}".format(DB_PORT)
        self.db_status = "未检查"
        self.deps_status = "未检查"
        self.build_status = "未检查"
        self.key_status = "尚未检查"
        self.installed_tools = []


STATE = State()


# ---------------------------------------------------------------------------
# 输出
# ---------------------------------------------------------------------------
def _configure_output():
    for stream in (sys.stdout, sys.stderr):
        try:
            if not stream.isatty():
                stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def step(text):
    STATE.step += 1
    print("")
    print("[{}/{}] {}".format(STATE.step, TOTAL_STEPS, text))


def ok(text):
    print("  [OK] " + text)


def info(text):
    print("  " + text)


def warn(text):
    print("  [注意] " + text)


def fail(message, next_step=""):
    raise InitError(message, next_step)


# ---------------------------------------------------------------------------
# 子进程与输出处理
# ---------------------------------------------------------------------------
def decode_output(data):
    if not data:
        return ""
    if isinstance(data, str):
        return data
    if data.startswith(b"\xff\xfe") or data.startswith(b"\xfe\xff"):
        try:
            return data.decode("utf-16", errors="replace")
        except Exception:
            pass
    for encoding in ("utf-8", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")


def run_cmd(args, cwd=None, env=None, timeout=60, input_bytes=None):
    cmd = [str(item) for item in args]
    kwargs = {
        "cwd": str(cwd) if cwd else None,
        "env": env,
        "stdout": subprocess.PIPE,
        "stderr": subprocess.STDOUT,
        "timeout": timeout,
    }
    if input_bytes is None:
        kwargs["stdin"] = subprocess.DEVNULL
    else:
        kwargs["input"] = input_bytes
    try:
        completed = subprocess.run(cmd, **kwargs)
    except FileNotFoundError:
        fail(
            "找不到命令：{}。".format(cmd[0]),
            "请确认该命令已安装并在 PATH 中，然后重新运行 init.py。",
        )
    except subprocess.TimeoutExpired:
        fail(
            "命令执行超时（{} 秒）：{}。".format(timeout, " ".join(cmd)),
            "请检查网络、Docker 或数据库状态后重试。",
        )
    except OSError as exc:
        fail(
            "无法执行命令 {}：{}。".format(cmd[0], exc),
            "请检查安装和权限后重试。",
        )
    return completed.returncode, decode_output(completed.stdout or b"")


def run_stream(args, cwd, timeout):
    cmd = [str(item) for item in args]
    print("  $ " + " ".join(cmd))
    try:
        completed = subprocess.run(
            cmd,
            cwd=str(cwd) if cwd else None,
            stdin=subprocess.DEVNULL,
            timeout=timeout,
        )
    except FileNotFoundError:
        fail(
            "找不到命令：{}。".format(cmd[0]),
            "请确认 npm 已随 Node.js 安装并在 PATH 中，然后重新运行 init.py。",
        )
    except subprocess.TimeoutExpired:
        fail(
            "命令执行超时（{} 秒）：{}。".format(timeout, " ".join(cmd)),
            "请检查网络与 npm 配置后重试。",
        )
    except OSError as exc:
        fail(
            "无法执行命令 {}：{}。".format(cmd[0], exc),
            "请检查 Node.js / npm 安装和权限后重试。",
        )
    return completed.returncode


def tail_text(text, max_lines=30, max_chars=6000):
    lines = [line.rstrip() for line in text.splitlines() if line.strip()]
    if len(lines) > max_lines:
        lines = lines[-max_lines:]
    result = "\n".join(lines)
    if len(result) > max_chars:
        result = "..." + result[-max_chars:]
    return result


def print_detail(text, prefix="    "):
    detail = tail_text(text)
    if not detail:
        return
    for line in detail.splitlines():
        print(prefix + line)


def compact_error(text):
    cleaned = [line.strip() for line in text.splitlines() if line.strip()]
    if not cleaned:
        return "（无输出）"
    return " | ".join(cleaned[-3:])


def meaningful_lines(text):
    lines = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        low = line.lower()
        if (
            low.startswith("mysql:")
            or low.startswith("warning:")
            or low.startswith("[warning]")
        ):
            continue
        lines.append(line)
    return lines


def which(cmd):
    return shutil.which(cmd)


# ---------------------------------------------------------------------------
# 版本检查辅助
# ---------------------------------------------------------------------------
JAVA_VERSION_RE = re.compile(r'version\s+"([^"]+)"', re.IGNORECASE)
NODE_VERSION_RE = re.compile(r"v(\d+)\.(\d+)\.(\d+)")


def manual_jdk_next_step():
    return (
        "请手动安装 JDK 21（任选其一）：\n"
        "  - Microsoft OpenJDK 21：https://learn.microsoft.com/java/openjdk/download\n"
        "  - Eclipse Temurin 21：https://adoptium.net/temurin/releases/?version=21\n"
        "安装后重新打开终端，确认 java -version 输出 21，再运行 init.py。"
    )


def manual_node_next_step():
    return (
        "请手动安装 Node.js 18+（建议 LTS）：https://nodejs.org/en/download\n"
        "安装后重新打开终端，确认 node --version 与 npm --version 可用，再运行 init.py。"
    )


def parse_java_version(text):
    match = JAVA_VERSION_RE.search(text)
    if not match:
        return 0, ""
    raw = match.group(1).strip()
    number = re.match(r"(\d+)(?:\.(\d+))?", raw)
    if not number:
        return 0, raw
    major = int(number.group(1))
    if major == 1 and number.group(2):
        major = int(number.group(2))
    return major, raw


def detect_java(exe):
    code, output = run_cmd([str(exe), "-version"], timeout=20)
    if code != 0:
        return None
    major, raw = parse_java_version(output)
    if major <= 0:
        return None
    return {"exe": str(exe), "major": major, "version": raw}


def java_home_java(home):
    home_path = Path(os.path.expanduser(str(home)))
    exe_name = "java.exe" if os.name == "nt" else "java"
    exe = home_path / "bin" / exe_name
    return exe if exe.is_file() else None


def java_home_javac(home):
    home_path = Path(os.path.expanduser(str(home)))
    exe_name = "javac.exe" if os.name == "nt" else "javac"
    exe = home_path / "bin" / exe_name
    return exe if exe.is_file() else None


def find_windows_jdk_home():
    candidates = []
    java_home_env = os.environ.get("JAVA_HOME", "").strip().strip('"')
    if java_home_env:
        candidates.append(Path(os.path.expanduser(java_home_env)))

    bases = []
    for key in ("ProgramFiles", "ProgramFiles(x86)"):
        value = os.environ.get(key)
        if value:
            bases.append(Path(value))
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        bases.append(Path(local_app_data) / "Programs")
        bases.append(Path(local_app_data))

    patterns = (
        "Microsoft/jdk-21*",
        "Eclipse Adoptium/jdk-21*",
        "Java/jdk-21*",
        "Amazon Corretto/jdk21*",
        "Zulu/zulu-21*",
    )
    for base in bases:
        for pattern in patterns:
            try:
                matches = sorted(base.glob(pattern))
            except OSError:
                matches = []
            for item in matches:
                candidates.append(item)

    seen = set()
    for home in candidates:
        key = str(home).lower()
        if key in seen:
            continue
        seen.add(key)
        exe = java_home_java(home)
        if not exe:
            continue
        java_info = detect_java(exe)
        if java_info and java_info["major"] >= 21:
            return home
    return None


def winget_available():
    return which("winget") or which("winget.exe")


def try_winget_install(package_id, label):
    winget = winget_available()
    if not winget:
        return False
    info("尝试用 winget 非交互安装 {}（{}）...".format(label, package_id))
    code, output = run_cmd(
        [
            winget,
            "install",
            "--id",
            package_id,
            "-e",
            "--silent",
            "--accept-source-agreements",
            "--accept-package-agreements",
        ],
        timeout=900,
    )
    if code == 0:
        ok("winget 安装 {} 完成。".format(label))
        return True
    warn("winget 安装 {} 失败（退出码 {}）。".format(label, code))
    print_detail(output)
    return False


def install_jdk21_with_winget():
    """返回 (JDK 21 安装目录或 None, 是否执行过 winget 安装)。仅 Windows 调用。"""
    if os.name != "nt" or not winget_available():
        return None, False

    installed = False
    home = None
    for package_id, label in (
        ("Microsoft.OpenJDK.21", "Microsoft OpenJDK 21"),
        ("EclipseAdoptium.Temurin.21.JDK", "Eclipse Temurin 21"),
    ):
        if try_winget_install(package_id, label):
            installed = True
            home = find_windows_jdk_home()
            if home:
                break
            warn("winget 报告安装成功，但暂未定位到 JDK 21；尝试下一个候选。")
    if not home:
        home = find_windows_jdk_home()
    return home, installed


def ensure_java():
    step("检查 JDK 21+")

    home_env = os.environ.get("JAVA_HOME", "").strip().strip('"')
    home_path = None
    if home_env:
        home_path = Path(os.path.expanduser(home_env))
        home_exe = java_home_java(home_path)
        if not home_exe:
            fail(
                "JAVA_HOME 已设置为 {}，但找不到 bin/java（或 bin/java.exe）。".format(home_env),
                "请修正或删除 JAVA_HOME 后重新运行 init.py；Maven Wrapper 会优先使用 JAVA_HOME。",
            )
        home_info = detect_java(home_exe)
        if home_info is None:
            fail(
                "无法识别 JAVA_HOME 指向的 Java 版本：{}。".format(home_env),
                "请把 JAVA_HOME 改为 JDK 21 的安装目录后重新运行 init.py。",
            )
        if home_info["major"] < 21:
            warn(
                "JAVA_HOME 指向的 Java 主版本为 {}，需要 JDK 21；Maven 会优先使用 JAVA_HOME。".format(
                    home_info["major"]
                )
            )
            if os.name == "nt" and winget_available():
                info("尝试用 winget 安装 JDK 21 ...")
                new_home, installed = install_jdk21_with_winget()
                if new_home:
                    if installed:
                        STATE.installed_tools.append("JDK 21")
                    fail(
                        "JDK 21 已安装/找到：{}，但 JAVA_HOME 仍指向旧 Java {}。".format(
                            new_home, home_info["major"]
                        ),
                        "请把 JAVA_HOME 改为 {}（或删除旧的 JAVA_HOME），重新打开终端后再运行 init.py。".format(
                            new_home
                        ),
                    )
            fail(
                "JAVA_HOME 指向的 Java 主版本为 {}，需要 JDK 21。".format(home_info["major"]),
                "请把 JAVA_HOME 改为 JDK 21 的安装目录（或删除旧的 JAVA_HOME），重新打开终端后再运行 init.py。\n"
                "若尚未安装 JDK 21：\n"
                "  - Microsoft OpenJDK 21：https://learn.microsoft.com/java/openjdk/download\n"
                "  - Eclipse Temurin 21：https://adoptium.net/temurin/releases/?version=21",
            )

        if not java_home_javac(home_path):
            warn("JAVA_HOME 下未找到 javac，请确认安装的是完整 JDK 21。")

        path_java = which("java")
        path_info = detect_java(path_java) if path_java else None
        if path_info and path_info["major"] < 21:
            warn(
                "PATH 中的 Java 主版本为 {}，但 Maven 会使用 JAVA_HOME 的 JDK {}，继续。".format(
                    path_info["major"], home_info["major"]
                )
            )
        if not path_info:
            os.environ["PATH"] = (
                str(home_path / "bin") + os.pathsep + os.environ.get("PATH", "")
            )
            info("PATH 中没有可用的 java，已把 JAVA_HOME 的 bin 目录加入本进程 PATH。")

        java_exe = home_path / "bin" / ("java.exe" if os.name == "nt" else "java")
        STATE.java_summary = "{}（JAVA_HOME：{}）".format(home_info["version"], home_path)
        ok("Java {}：{}".format(home_info["version"], java_exe))
        return

    path_java = which("java")
    path_info = detect_java(path_java) if path_java else None
    if path_info and path_info["major"] >= 21:
        javac = Path(path_java).with_name("javac.exe" if os.name == "nt" else "javac")
        if not javac.is_file():
            warn("未在 java 同目录找到 javac，请确认 PATH 中的 Java 是完整 JDK 21。")
        STATE.java_summary = "{}（PATH：{}）".format(path_info["version"], path_java)
        ok("Java {}：{}".format(path_info["version"], path_java))
        return

    if os.name == "nt":
        existing_home = find_windows_jdk_home()
        if existing_home:
            existing_exe = java_home_java(existing_home)
            existing_info = detect_java(existing_exe)
            if existing_info and existing_info["major"] >= 21:
                os.environ["JAVA_HOME"] = str(existing_home)
                os.environ["PATH"] = (
                    str(existing_home / "bin") + os.pathsep + os.environ.get("PATH", "")
                )
                warn(
                    "在标准安装目录找到 JDK 21，但它不在当前 PATH / JAVA_HOME 中；本次已临时使用 {}。".format(
                        existing_home
                    )
                )
                warn("如果新终端仍找不到 java，请把 JAVA_HOME 设为该目录。")
                STATE.java_summary = "{}（{}）".format(
                    existing_info["version"], existing_home
                )
                ok("Java {}：{}".format(existing_info["version"], existing_exe))
                return

        if not winget_available():
            fail("未找到可用的 JDK 21，且系统没有 winget。", manual_jdk_next_step())
        info("未找到可用的 JDK 21，尝试用 winget 安装 ...")
        new_home, installed = install_jdk21_with_winget()
        if not new_home:
            fail("winget 未能安装或定位 JDK 21。", manual_jdk_next_step())
        if installed:
            STATE.installed_tools.append("JDK 21")
        new_exe = java_home_java(new_home)
        new_info = detect_java(new_exe)
        if not new_info or new_info["major"] < 21:
            fail(
                "安装后仍无法确认 JDK 21（{}）。".format(new_home),
                manual_jdk_next_step(),
            )
        os.environ["JAVA_HOME"] = str(new_home)
        os.environ["PATH"] = (
            str(Path(new_home) / "bin") + os.pathsep + os.environ.get("PATH", "")
        )
        STATE.java_summary = "{}（{}）".format(new_info["version"], new_home)
        ok("Java {}：{}".format(new_info["version"], new_exe))
        if installed:
            warn(
                "已通过 winget 安装 JDK 21；如果新终端仍找不到 java，请重新打开终端或把 JAVA_HOME 设为 {}。".format(
                    new_home
                )
            )
        else:
            warn(
                "已在标准目录找到 JDK 21；如果新终端仍找不到 java，请把 JAVA_HOME 设为 {}。".format(
                    new_home
                )
            )
        return

    if path_java:
        detail = (
            "当前 Java 主版本为 {}，需要 21+。".format(path_info["major"])
            if path_info
            else "无法识别 PATH 中的 java 版本。"
        )
    else:
        detail = "未找到 java。"
    fail("未找到可用的 JDK 21。{}".format(detail), manual_jdk_next_step())


def detect_node(exe):
    code, output = run_cmd([str(exe), "--version"], timeout=20)
    if code != 0:
        return None
    match = NODE_VERSION_RE.search(output)
    if not match:
        return None
    return {
        "exe": str(exe),
        "major": int(match.group(1)),
        "version": match.group(0),
    }


def find_windows_node():
    bases = []
    for key in ("ProgramFiles", "ProgramFiles(x86)"):
        value = os.environ.get(key)
        if value:
            bases.append(Path(value))
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        bases.append(Path(local_app_data) / "Programs")
    for base in bases:
        exe = base / "nodejs" / "node.exe"
        if exe.is_file():
            return exe
    return None


def find_npm_for_node(node_exe):
    node_dir = Path(node_exe).parent
    for name in ("npm.cmd", "npm.exe", "npm"):
        candidate = node_dir / name
        if candidate.is_file():
            return str(candidate)
    npm = which("npm")
    if npm:
        return npm
    return None


def ensure_node():
    step("检查 Node.js / npm（建议 18+）")

    node_exe = which("node") or which("node.exe")
    node_info = detect_node(node_exe) if node_exe else None

    if node_info and node_info["major"] < 18:
        if os.name == "nt" and winget_available():
            warn(
                "当前 Node.js 主版本为 {}，Vite 6 需要 18+；尝试用 winget 安装 LTS。".format(
                    node_info["major"]
                )
            )
            if try_winget_install("OpenJS.NodeJS.LTS", "Node.js LTS"):
                STATE.installed_tools.append("Node.js LTS")
                installed_node = find_windows_node()
                if not installed_node:
                    fail(
                        "winget 报告安装完成，但未能自动定位 node.exe。",
                        "请关闭并重新打开终端后运行 node --version 检查；"
                        "若仍找不到，请重新安装 Node.js LTS 或把安装目录加入 PATH。",
                    )
                new_info = detect_node(installed_node)
                if new_info and new_info["major"] >= 18:
                    node_info = new_info
                    warn(
                        "已通过 winget 安装 Node.js LTS；如果新终端仍找不到 node，"
                        "请重新打开终端或把 {} 加入 PATH。".format(Path(installed_node).parent)
                    )
        if not node_info or node_info["major"] < 18:
            fail(
                "当前 Node.js 主版本为 {}，需要 18+。".format(
                    node_info["major"] if node_info else "未知"
                ),
                manual_node_next_step(),
            )

    if not node_info:
        if os.name == "nt":
            existing_node = find_windows_node()
            if existing_node:
                existing_info = detect_node(existing_node)
                if existing_info and existing_info["major"] >= 18:
                    node_info = existing_info
                    warn(
                        "在标准安装目录找到 Node.js，但它不在当前 PATH 中；本次已临时使用 {}。".format(
                            existing_node
                        )
                    )
                    warn(
                        "如果新终端仍找不到 node，请把 {} 加入 PATH。".format(
                            Path(existing_node).parent
                        )
                    )

        if not node_info:
            if os.name != "nt":
                fail("未找到 Node.js。", manual_node_next_step())
            if not winget_available():
                fail("未找到 Node.js，且系统没有 winget。", manual_node_next_step())
            info("未找到 Node.js，尝试用 winget 安装 LTS ...")
            if not try_winget_install("OpenJS.NodeJS.LTS", "Node.js LTS"):
                fail("winget 未能安装 Node.js LTS。", manual_node_next_step())
            STATE.installed_tools.append("Node.js LTS")
            installed_node = find_windows_node()
            if not installed_node:
                fail(
                    "winget 报告安装完成，但未能自动定位 node.exe。",
                    "请关闭并重新打开终端后运行 node --version 检查；"
                    "若仍找不到，请重新安装 Node.js LTS 或把安装目录加入 PATH。",
                )
            node_info = detect_node(installed_node)
            if not node_info:
                fail("安装后无法运行 node.exe。", manual_node_next_step())
            warn(
                "已通过 winget 安装 Node.js LTS；如果新终端仍找不到 node，"
                "请重新打开终端或把 {} 加入 PATH。".format(Path(installed_node).parent)
            )

    if node_info["major"] < 18:
        fail(
            "当前 Node.js 主版本为 {}，需要 18+。".format(node_info["major"]),
            manual_node_next_step(),
        )

    node_dir = Path(node_info["exe"]).parent
    os.environ["PATH"] = str(node_dir) + os.pathsep + os.environ.get("PATH", "")
    STATE.node_summary = "{}（{}）".format(node_info["version"], node_info["exe"])
    ok("Node.js {}：{}".format(node_info["version"], node_info["exe"]))

    npm_path = find_npm_for_node(node_info["exe"])
    if not npm_path:
        fail(
            "未找到 npm。",
            "请重新安装 Node.js（会同时安装 npm），或确认 npm 在 PATH 中后重试。",
        )
    code, output = run_cmd([npm_path, "--version"], timeout=30)
    npm_version = ""
    if code == 0:
        lines = meaningful_lines(output)
        if lines:
            npm_version = lines[-1]
    if not npm_version:
        fail(
            "无法读取 npm 版本：{}。".format(npm_path),
            "请重新安装 Node.js / npm 后重试。",
        )
    STATE.npm_path = npm_path
    STATE.npm_summary = "{}（{}）".format(npm_version, npm_path)
    ok("npm {}：{}".format(npm_version, npm_path))


# ---------------------------------------------------------------------------
# Docker / MySQL
# ---------------------------------------------------------------------------
class DockerInfo(object):
    def __init__(self, exe, daemon_ready, detail=""):
        self.exe = exe
        self.daemon_ready = daemon_ready
        self.detail = detail

    @classmethod
    def detect(cls):
        exe = which("docker") or which("docker.exe")
        if not exe:
            return None
        try:
            code, output = run_cmd(
                [exe, "version", "--format", "{{.Server.Version}}"], timeout=30
            )
        except InitError as exc:
            # Docker 是可选项：引擎无响应时按“不可用”处理，
            # 不要让 3308 上已有的 MySQL 因 Docker 卡住而无法使用。
            return cls(exe, False, exc.message)
        return cls(exe, code == 0, output.strip())


def docker_container_exists(docker):
    code, _ = run_cmd(
        [docker.exe, "inspect", "--type", "container", DB_CONTAINER], timeout=20
    )
    return code == 0


def docker_container_running(docker):
    code, output = run_cmd(
        [docker.exe, "inspect", "-f", "{{.State.Running}}", DB_CONTAINER], timeout=20
    )
    return code == 0 and output.strip().lower() == "true"


def docker_container_maps_3308(docker):
    code, output = run_cmd(
        [
            docker.exe,
            "inspect",
            "-f",
            "{{json .HostConfig.PortBindings}}",
            DB_CONTAINER,
        ],
        timeout=20,
    )
    if code != 0:
        return False
    text = output.strip()
    if not text:
        return False
    try:
        bindings = json.loads(text)
    except ValueError:
        return False
    if not isinstance(bindings, dict):
        return False
    entries = bindings.get("3306/tcp")
    if not entries:
        return False
    if not isinstance(entries, list):
        entries = [entries]
    for entry in entries:
        if isinstance(entry, dict) and str(entry.get("HostPort", "")) == str(DB_PORT):
            return True
    return False


def port_open(port=DB_PORT, timeout=0.8):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.settimeout(timeout)
        return sock.connect_ex((DB_HOST, port)) == 0
    except OSError:
        return False
    finally:
        try:
            sock.close()
        except OSError:
            pass


def wait_port(timeout_seconds, label="MySQL 3308"):
    start = time.monotonic()
    next_notice = 15.0
    while time.monotonic() - start < timeout_seconds:
        if port_open():
            return True
        elapsed = time.monotonic() - start
        if elapsed >= next_notice:
            info("等待 {} 就绪，已 {} 秒 ...".format(label, int(elapsed)))
            next_notice += 15.0
        time.sleep(1.5)
    return False


class Database(object):
    def __init__(self, mode, docker_exe=None, mysql_exe=None):
        self.mode = mode
        self.docker_exe = docker_exe
        self.mysql_exe = mysql_exe

    def _local_args(self):
        return [
            self.mysql_exe,
            "--protocol=TCP",
            "-h" + DB_HOST,
            "-P" + str(DB_PORT),
            "-u" + DB_USER,
            "--default-character-set=utf8mb4",
            "--connect-timeout=5",
        ]

    def _local_env(self):
        env = os.environ.copy()
        env["MYSQL_PWD"] = DB_PASSWORD
        return env

    def query(self, sql, timeout=60):
        if self.mode == "docker":
            args = [
                self.docker_exe,
                "exec",
                "-e",
                "MYSQL_PWD=" + DB_PASSWORD,
                DB_CONTAINER,
                "mysql",
                "-u" + DB_USER,
                "--default-character-set=utf8mb4",
                "--connect-timeout=5",
                "-N",
                "-B",
                "-e",
                sql,
            ]
            return run_cmd(args, timeout=timeout)
        args = self._local_args() + ["-N", "-B", "-e", sql]
        return run_cmd(args, env=self._local_env(), timeout=timeout)

    def run_file(self, path, timeout=600):
        try:
            data = path.read_bytes()
        except OSError as exc:
            fail(
                "读取 SQL 文件失败：{}（{}）。".format(path, exc),
                "请确认仓库完整且文件可读。",
            )
        if data.startswith(b"\xef\xbb\xbf"):
            data = data[3:]
        if self.mode == "docker":
            args = [
                self.docker_exe,
                "exec",
                "-i",
                "-e",
                "MYSQL_PWD=" + DB_PASSWORD,
                DB_CONTAINER,
                "mysql",
                "-u" + DB_USER,
                "--default-character-set=utf8mb4",
                "--connect-timeout=5",
            ]
            return run_cmd(args, input_bytes=data, timeout=timeout)
        args = self._local_args()
        return run_cmd(args, env=self._local_env(), input_bytes=data, timeout=timeout)

    def query_lines(self, sql, timeout=60):
        code, output = self.query(sql, timeout=timeout)
        if code != 0:
            print_detail(output)
            fail(
                "MySQL 查询失败。",
                "请确认 MySQL 8 正在运行、root / 123456 可登录；脚本不会自动删库。"
                "最近错误：{}".format(compact_error(output)),
            )
        return meaningful_lines(output)

    def query_value(self, sql, timeout=60):
        lines = self.query_lines(sql, timeout=timeout)
        if not lines:
            return None
        return lines[-1]

    def require_value(self, sql, timeout=60):
        value = self.query_value(sql, timeout=timeout)
        if value is None:
            fail(
                "MySQL 查询没有返回结果：{}。".format(sql),
                "请手动检查数据库状态后重试。",
            )
        return value


def wait_db_access(db, timeout_seconds):
    start = time.monotonic()
    next_notice = 15.0
    last_error = ""
    while time.monotonic() - start < timeout_seconds:
        code, output = db.query("SELECT 1;", timeout=20)
        if code == 0:
            lines = meaningful_lines(output)
            if any(line == "1" for line in lines):
                return True, ""
        last_error = compact_error(output)
        elapsed = time.monotonic() - start
        if elapsed >= next_notice:
            info("等待 MySQL 账号可用，已 {} 秒 ...".format(int(elapsed)))
            next_notice += 15.0
        time.sleep(2)
    return False, last_error


def resolve_database():
    step("准备 MySQL 3308")

    docker = DockerInfo.detect()
    mysql_exe = which("mysql") or which("mysql.exe")
    port_up = port_open()

    if docker:
        if docker.daemon_ready:
            info("Docker：{}（引擎已就绪）".format(docker.exe))
        else:
            info("Docker：{}（引擎未就绪）".format(docker.exe))
    else:
        info("Docker：未找到（若 3308 已有 MySQL 8 则不需要）")
    if mysql_exe:
        info("mysql 客户端：{}".format(mysql_exe))
    else:
        info("mysql 客户端：未找到（导入 SQL 会优先使用 docker exec）")

    if port_up:
        managed = False
        if docker and docker.daemon_ready:
            try:
                if (
                    docker_container_exists(docker)
                    and docker_container_running(docker)
                    and docker_container_maps_3308(docker)
                ):
                    managed = True
            except InitError:
                warn("Docker 容器检查超时，改为探测 3308 / 使用本机 mysql 客户端。")
                managed = False

        if managed:
            db = Database("docker", docker_exe=docker.exe)
            STATE.db_desc = "127.0.0.1:3308（Docker 容器 {}）".format(DB_CONTAINER)
            ok(
                "3308 端口可连接，使用容器 {}（docker exec 执行 SQL）。".format(
                    DB_CONTAINER
                )
            )
        elif mysql_exe:
            db = Database("mysql", mysql_exe=mysql_exe)
            STATE.db_desc = "127.0.0.1:3308（本机 mysql 客户端）"
            ok("3308 端口可连接，使用本机 mysql 客户端。")
        else:
            fail(
                "3308 端口已在监听，但既没有可管理的 logistics-mysql 容器，也没有 mysql 客户端。",
                "请安装 mysql 客户端或启动 Docker Desktop 后重试；"
                "若确认数据库已初始化，也可以直接运行 start.cmd。",
            )

        ready, last_error = wait_db_access(db, 60)
        if not ready:
            fail(
                "3308 端口有服务，但无法用 root / 123456 连接 MySQL。",
                "请确认账号密码与 src/main/resources/application.yaml 一致。"
                "最近错误：{}".format(last_error or "（无输出）"),
            )
        ok("MySQL 账号可用（root / 123456）。")
        return db

    # 3308 未就绪
    if not docker:
        fail(
            "MySQL 端口 3308 未启动，且本机没有 Docker。",
            "请先启动 MySQL 8（监听 127.0.0.1:3308，root / 123456），"
            "或安装并启动 Docker Desktop 后重试。",
        )
    if not docker.daemon_ready:
        fail(
            "MySQL 端口 3308 未启动，Docker 引擎也不可用。",
            "请启动 Docker Desktop 并等待它完全就绪后重试；"
            "或自行启动监听 3308 的 MySQL 8。",
        )

    container_exists = docker_container_exists(docker)
    container_running = docker_container_running(docker) if container_exists else False
    container_maps = (
        docker_container_maps_3308(docker) if container_exists else False
    )

    if container_exists and not container_maps:
        fail(
            "已存在名为 {} 的容器，但它没有把 3306 映射到宿主机 3308。".format(
                DB_CONTAINER
            ),
            "脚本不会自动删除容器。请先确认该容器用途并备份，"
            "手动改名或重建后再运行 init.py。",
        )

    if container_exists:
        if not container_running:
            info("正在启动已有容器 {} ...".format(DB_CONTAINER))
            code, output = run_cmd([docker.exe, "start", DB_CONTAINER], timeout=120)
            if code != 0:
                print_detail(output)
                fail(
                    "启动容器 {} 失败。".format(DB_CONTAINER),
                    "请运行 docker logs {} 查看原因，"
                    "并确认 Docker Desktop 正常后重试。".format(DB_CONTAINER),
                )
            ok("容器 {} 已启动。".format(DB_CONTAINER))
        else:
            info("容器 {} 已在运行，等待端口就绪 ...".format(DB_CONTAINER))
    else:
        info(
            "未找到容器 {}，正在创建 MySQL 8 容器（首次会下载镜像，可能较慢）...".format(
                DB_CONTAINER
            )
        )
        args = [
            docker.exe,
            "run",
            "-d",
            "--name",
            DB_CONTAINER,
            "-p",
            "3308:3306",
            "-e",
            "MYSQL_ROOT_PASSWORD=" + DB_PASSWORD,
            "-e",
            "TZ=Asia/Shanghai",
            DB_IMAGE,
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_0900_ai_ci",
        ]
        code, output = run_cmd(args, timeout=900)
        if code != 0:
            print_detail(output)
            fail(
                "创建 MySQL 容器失败。",
                "请检查 Docker Desktop 是否正常、3308 是否被占用、"
                "网络能否拉取 mysql:8.0 镜像，然后重试。",
            )
        ok("容器 {} 已创建。".format(DB_CONTAINER))
        container_id = output.strip()[:12]
        if container_id:
            info("容器 ID：{}".format(container_id))

    info("正在等待 MySQL 3308 就绪（最多 300 秒）...")
    if not wait_port(300, "MySQL 3308"):
        fail(
            "等待 MySQL 3308 就绪超时。",
            "请运行 docker logs {} 查看日志；"
            "首次拉取镜像失败或 Docker Desktop 未就绪都可能导致超时。".format(
                DB_CONTAINER
            ),
        )

    db = Database("docker", docker_exe=docker.exe)
    ready, last_error = wait_db_access(db, 120)
    if not ready:
        fail(
            "MySQL 3308 已监听，但 root / 123456 无法登录。",
            "若该容器不是本项目创建的，请自行检查密码。"
            "最近错误：{}".format(last_error or "（无输出）"),
        )

    STATE.db_desc = "127.0.0.1:3308（Docker 容器 {}）".format(DB_CONTAINER)
    ok("MySQL 已就绪（容器 {}，root / 123456）。".format(DB_CONTAINER))
    return db


# ---------------------------------------------------------------------------
# 数据库初始化 / 增量升级
# ---------------------------------------------------------------------------
def column_count(db, table, columns):
    quoted = ",".join("'{}'".format(column) for column in columns)
    sql = (
        "SELECT COUNT(*) FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA='{}' AND TABLE_NAME='{}' "
        "AND COLUMN_NAME IN ({});"
    ).format(DB_NAME, table, quoted)
    value = db.require_value(sql)
    try:
        return int(value)
    except (TypeError, ValueError):
        fail(
            "无法解析列数量查询结果：{}。".format(value),
            "请手动检查数据库结构后重试。",
        )


def require_int(db, sql, label):
    value = db.require_value(sql)
    try:
        return int(value)
    except (TypeError, ValueError):
        fail(
            "无法解析{}：{}。".format(label, value),
            "请手动检查数据库状态后重试。",
        )


def count_station(db):
    count = require_int(db, "SELECT COUNT(*) FROM logistics.station;", "station 行数")
    if count < 1:
        fail(
            "seed.sql 执行后 station 表仍为空。",
            "请查看上方 SQL 输出，并手动检查 sql/seed.sql。",
        )
    return count


def state_constraint_current(db):
    lines = db.query_lines(
        "SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
        "WHERE CONSTRAINT_SCHEMA='{}' AND CONSTRAINT_NAME='ck_order_state' "
        "LIMIT 1;".format(DB_NAME)
    )
    if not lines:
        return False
    clause = lines[-1]
    return ("AWAITING_RECEIPT" in clause) and ("CANCELLED" in clause)


def legacy_approved_count(db):
    return require_int(
        db,
        "SELECT COUNT(*) FROM logistics.delivery_order "
        "WHERE state='APPROVED' AND price_confirmed_at IS NULL;",
        "旧版未核价已批准单数量",
    )


def run_sql_script(db, relative_path):
    path = ROOT / relative_path
    if not path.is_file():
        fail(
            "找不到 SQL 文件：{}。".format(relative_path),
            "仓库不完整，请重新获取完整仓库后重试。",
        )
    info("执行 {} ...".format(relative_path))
    code, output = db.run_file(path)
    if code != 0:
        print_detail(output)
        fail(
            "执行 {} 失败（退出码 {}）。".format(relative_path, code),
            "请查看上方 MySQL 报错。脚本已停止，没有主动清空业务数据；"
            "修正问题后可重新运行。",
        )
    ok("{} 执行完成。".format(relative_path))


def initialize_database(db, reset_db):
    step("检查并初始化数据库")

    version = db.require_value("SELECT VERSION();")
    if "mariadb" in version.lower() or re.match(r"^5\.", version):
        fail(
            "检测到数据库版本 {}，本项目需要 MySQL 8。".format(version),
            "请改用 MySQL 8.0（推荐 mysql:8.0 容器），不要使用 MariaDB 或 MySQL 5.7。",
        )
    major_match = re.match(r"^(\d+)\.", version)
    if major_match and int(major_match.group(1)) < 8:
        fail(
            "检测到数据库版本 {}，本项目需要 MySQL 8。".format(version),
            "请改用 MySQL 8.0（推荐 mysql:8.0 容器）。",
        )
    ok("MySQL 版本：{}".format(version))

    if reset_db:
        print("")
        print("  " + "!" * 58)
        warn("已指定 --reset-db：以下操作会清空数据！")
        warn("将删除 logistics 库中的业务表 station、route_edge、operator、")
        warn("postman、delivery_order、order_route，以及其中的全部数据，然后重新执行")
        warn("sql/schema.sql 和 sql/seed.sql。此操作不可恢复，请确认这是你要的。")
        print("  " + "!" * 58)
        print("")
        run_sql_script(db, SCHEMA_SQL)
        run_sql_script(db, SEED_SQL)
        station_count = count_station(db)
        ok(
            "数据库已按 --reset-db 重建：schema.sql + seed.sql"
            "（station {} 行）。".format(station_count)
        )
        STATE.db_status = "已按 --reset-db 重建（原有业务数据已清空；station {} 行）".format(
            station_count
        )
        return

    db_exists = (
        db.require_value(
            "SELECT COUNT(*) FROM information_schema.SCHEMATA "
            "WHERE SCHEMA_NAME='{}';".format(DB_NAME)
        )
        == "1"
    )
    if not db_exists:
        info("未检测到 logistics 库，开始首次初始化 ...")
        run_sql_script(db, SCHEMA_SQL)
        run_sql_script(db, SEED_SQL)
        station_count = count_station(db)
        ok(
            "数据库首次初始化完成（schema.sql + seed.sql，station {} 行）。".format(
                station_count
            )
        )
        STATE.db_status = "本次已初始化（schema.sql + seed.sql，station {} 行）".format(
            station_count
        )
        return

    key_table_sql = (
        "SELECT COUNT(*) FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA='{}' AND TABLE_TYPE='BASE TABLE' "
        "AND TABLE_NAME IN ({});"
    ).format(DB_NAME, ",".join("'{}'".format(name) for name in KEY_TABLES))
    key_table_count = require_int(db, key_table_sql, "关键表数量")

    if key_table_count == 0:
        info(
            "logistics 库存在但没有任何本项目关键表，开始首次初始化"
            "（仅此情况会执行 schema.sql 的建表 DROP）..."
        )
        run_sql_script(db, SCHEMA_SQL)
        run_sql_script(db, SEED_SQL)
        station_count = count_station(db)
        ok(
            "数据库首次初始化完成（schema.sql + seed.sql，station {} 行）。".format(
                station_count
            )
        )
        STATE.db_status = "本次已初始化（schema.sql + seed.sql，station {} 行）".format(
            station_count
        )
        return

    if key_table_count < len(KEY_TABLES):
        present_sql = (
            "SELECT TABLE_NAME FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA='{}' AND TABLE_TYPE='BASE TABLE' "
            "AND TABLE_NAME IN ({});"
        ).format(DB_NAME, ",".join("'{}'".format(name) for name in KEY_TABLES))
        present = set(db.query_lines(present_sql))
        missing = [name for name in KEY_TABLES if name not in present]
        fail(
            "logistics 库已存在，但关键表不完整（缺少：{}）。".format(
                "、".join(missing) if missing else "（表数量异常）"
            ),
            "为避免 schema.sql 的 DROP 清空已有数据，脚本不会自动重建。"
            "请先备份；确认数据可丢弃后再手动执行 sql/schema.sql，"
            "否则按 README 顺序手动检查 sql/alter_pricing.sql、"
            "sql/alter_final_six.sql、sql/alter_track_map.sql、"
            "sql/alter_track_map_gcj02.sql。",
        )

    info("检测到已有 logistics 库和 6 张关键表，进入结构检查（不会重建）。")
    business_rows = require_int(
        db,
        "SELECT (SELECT COUNT(*) FROM logistics.station) + "
        "(SELECT COUNT(*) FROM logistics.route_edge) + "
        "(SELECT COUNT(*) FROM logistics.operator) + "
        "(SELECT COUNT(*) FROM logistics.postman) + "
        "(SELECT COUNT(*) FROM logistics.delivery_order);",
        "业务数据行数",
    )
    changed = False

    # 1) 运费字段
    pricing_columns = (
        "volume_cm3",
        "quoted_price",
        "actual_volume_cm3",
        "actual_weight",
        "price",
        "price_confirmed_at",
        "price_confirmed_by",
    )
    pricing_count = column_count(db, "delivery_order", pricing_columns)
    if pricing_count < len(pricing_columns):
        warn("检测到旧库缺少运费字段，执行 sql/alter_pricing.sql（可重复执行，不删数据）。")
        run_sql_script(db, ALTER_PRICING_SQL)
        pricing_count = column_count(db, "delivery_order", pricing_columns)
        if pricing_count < len(pricing_columns):
            fail(
                "alter_pricing.sql 执行后仍有运费字段缺失。",
                "请先备份，再按 README 手动检查数据库结构；不要直接重建库。",
            )
        changed = True
    else:
        ok("运费字段已是最新，跳过 alter_pricing.sql。")

    # 2) 期末六项
    final_six_columns = (
        "cancel_time",
        "price_confirmed_operator",
        "receipt_confirmed_at",
    )
    final_six_count = column_count(db, "delivery_order", final_six_columns)
    reasons = []
    if final_six_count < len(final_six_columns):
        reasons.append("缺少期末六项字段")
    if business_rows > 0:
        legacy_count = legacy_approved_count(db)
        if legacy_count > 0:
            reasons.append("存在 {} 张旧版未核价的已批准单".format(legacy_count))
    if not state_constraint_current(db):
        reasons.append("订单状态 CHECK 约束不是最新版本")

    if reasons:
        warn(
            "检测到旧结构：{}。执行 sql/alter_final_six.sql"
            "（可重复执行，不删数据）。".format("；".join(reasons))
        )
        warn(
            "该脚本会把旧版尚未核价的已批准单退回“待始发收件”，"
            "这是 README 说明的预期升级行为，不会删除订单。"
        )
        run_sql_script(db, ALTER_FINAL_SIX_SQL)
        final_six_count = column_count(db, "delivery_order", final_six_columns)
        if final_six_count < len(final_six_columns):
            fail(
                "alter_final_six.sql 执行后仍有关键字段缺失。",
                "请先备份，再按 README 手动检查数据库结构；不要直接重建库。",
            )
        if not state_constraint_current(db):
            fail(
                "alter_final_six.sql 执行后订单状态 CHECK 约束仍不是最新版本。",
                "请先备份，再按 README 手动检查 sql/alter_final_six.sql；"
                "不要直接重建库。",
            )
        if business_rows > 0:
            legacy_after = legacy_approved_count(db)
            if legacy_after > 0:
                warn(
                    "升级后仍有 {} 张已批准单未核价，请手动检查订单状态。".format(
                        legacy_after
                    )
                )
        changed = True
    else:
        ok("期末六项字段与状态约束已是最新，跳过 alter_final_six.sql。")

    # 3) 轨迹坐标
    track_columns = ("latitude", "longitude")
    track_count = column_count(db, "station", track_columns)
    if track_count < len(track_columns):
        warn(
            "检测到驿站表缺少经纬度列，执行 sql/alter_track_map.sql"
            "（写入 GCJ-02 坐标，不删数据）。"
        )
        run_sql_script(db, ALTER_TRACK_SQL)
        track_count = column_count(db, "station", track_columns)
        if track_count < len(track_columns):
            fail(
                "alter_track_map.sql 执行后经纬度列仍缺失。",
                "请先备份，再按 README 手动检查 station 表结构；不要直接重建库。",
            )
        changed = True
    else:
        null_coord_count = require_int(
            db,
            "SELECT COUNT(*) FROM logistics.station "
            "WHERE latitude IS NULL OR longitude IS NULL;",
            "缺少坐标的驿站数量",
        )
        if null_coord_count > 0:
            warn(
                "有 {} 个驿站缺少坐标，执行 sql/alter_track_map.sql "
                "补齐 GCJ-02 坐标。".format(null_coord_count)
            )
            run_sql_script(db, ALTER_TRACK_SQL)
            changed = True
        else:
            old_wgs_count = require_int(
                db,
                "SELECT COUNT(*) FROM logistics.station WHERE code='ST001' "
                "AND latitude=39.9068844 AND longitude=116.4845489;",
                "旧 WGS84 坐标标记数量",
            )
            if old_wgs_count > 0:
                warn(
                    "检测到 ST001 仍是旧版 WGS84 坐标，执行 "
                    "sql/alter_track_map_gcj02.sql 原地转换为 GCJ-02（只改经纬度）。"
                )
                run_sql_script(db, ALTER_TRACK_GCJ_SQL)
                gcj_count = require_int(
                    db,
                    "SELECT COUNT(*) FROM logistics.station WHERE code='ST001' "
                    "AND latitude=39.9081515 AND longitude=116.4906138;",
                    "ST001 GCJ-02 坐标标记数量",
                )
                if gcj_count < 1:
                    warn(
                        "坐标转换脚本执行后未检测到预期的 ST001 GCJ-02 坐标，"
                        "请手动检查 sql/alter_track_map_gcj02.sql。"
                    )
                changed = True
            else:
                ok(
                    "驿站坐标已是当前版本（未检测到旧 WGS84 标记），"
                    "跳过 alter_track_map_gcj02.sql。"
                )

    missing_coord_count = require_int(
        db,
        "SELECT COUNT(*) FROM logistics.station "
        "WHERE latitude IS NULL OR longitude IS NULL;",
        "缺少坐标的驿站数量",
    )
    if missing_coord_count > 0:
        warn(
            "仍有 {} 个驿站缺少坐标；轨迹地图可能无法完整显示，"
            "请手动检查 station 表。".format(missing_coord_count)
        )

    # 4) 基础数据
    if business_rows == 0:
        info(
            "数据表存在但基础数据为空，执行 sql/seed.sql "
            "补齐演示数据（不会删除数据）。"
        )
        run_sql_script(db, SEED_SQL)
        station_count = count_station(db)
        STATE.db_status = (
            "已存在，本次已补齐基础数据（未重建；station {} 行）".format(
                station_count
            )
        )
    else:
        suffix = "；本次执行了增量升级" if changed else ""
        STATE.db_status = (
            "已存在，结构检查完成（未重建、未清空；业务数据 {} 行{}）".format(
                business_rows, suffix
            )
        )
        ok("数据库已有业务数据（{} 行），跳过 seed.sql。".format(business_rows))

    ok("数据库结构检查完成。")


# ---------------------------------------------------------------------------
# 前端
# ---------------------------------------------------------------------------
AMAP_KEY_RE = re.compile(
    r"^(?:export\s+)?VITE_AMAP_JS_API_KEY\s*=\s*(.*)$"
)


def amap_key_present(path):
    """只返回是否填写了非空 Key，绝不返回或打印 Key 内容。"""
    try:
        text = path.read_text(encoding="utf-8-sig", errors="replace")
    except OSError:
        return False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = AMAP_KEY_RE.match(line)
        if not match:
            continue
        value = match.group(1).strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        if value.strip():
            return True
    return False


def prepare_frontend():
    step("准备前端（依赖 / .env.local / 构建）")

    if not WEB_DIR.is_dir():
        fail("找不到 web 前端目录。", "请确认在完整仓库根目录运行 init.py。")
    if not (WEB_DIR / "package.json").is_file():
        fail(
            "web 目录下缺少 package.json。",
            "请确认仓库完整，或重新获取前端工程。",
        )

    if not ENV_LOCAL.exists():
        if not ENV_EXAMPLE.is_file():
            fail("缺少 web/.env.example。", "请确认仓库完整后重试。")
        try:
            shutil.copyfile(str(ENV_EXAMPLE), str(ENV_LOCAL))
        except OSError as exc:
            fail(
                "创建 web/.env.local 失败：{}。".format(exc),
                "请检查 web 目录权限后重试。",
            )
        warn("已从 web/.env.example 创建 web/.env.local。")
        warn(
            "未填写 VITE_AMAP_JS_API_KEY 时系统仍可启动，"
            "但轨迹地图会提示缺少 Key。"
        )
        warn(
            "如需地图：编辑 web/.env.local 填入高德 Web 端 JS API Key，"
            "然后重新运行 start.ps1 或手动 npm run build。"
        )
    else:
        ok("web/.env.local 已存在，保持原样不覆盖。")

    if amap_key_present(ENV_LOCAL):
        STATE.key_status = "已填写（不显示内容）"
        ok("已检测到非空的高德 Key（不显示具体内容）。")
    else:
        STATE.key_status = "尚未填写（轨迹地图会提示缺少 Key）"
        warn(
            "web/.env.local 中 VITE_AMAP_JS_API_KEY 为空："
            "系统能启动，但轨迹地图会提示缺少 Key。"
        )

    vite_unix = WEB_DIR / "node_modules" / ".bin" / "vite"
    vite_win = WEB_DIR / "node_modules" / ".bin" / "vite.cmd"
    if vite_unix.exists() or vite_win.exists():
        STATE.deps_status = "已安装（跳过 npm install）"
        ok("前端依赖已存在，跳过 npm install。")
    else:
        info("未检测到完整前端依赖，执行 npm install（首次可能较慢）...")
        code = run_stream([STATE.npm_path, "install"], WEB_DIR, timeout=1200)
        if code != 0:
            fail(
                "npm install 失败（退出码 {}）。".format(code),
                "请检查网络与 npm 配置（代理/镜像），然后重新运行 init.py。",
            )
        if not (vite_unix.exists() or vite_win.exists()):
            fail(
                "npm install 完成后仍未找到 node_modules/.bin 下的 vite 启动器。",
                "请检查 npm 安装日志和 web/package.json。",
            )
        STATE.deps_status = "本次已安装"
        ok("前端依赖安装完成。")

    if STATIC_INDEX.is_file():
        STATE.build_status = "已存在（跳过 npm run build）"
        ok("已检测到前端构建产物，跳过 npm run build。")
        info(
            "如需强制重建：删除 src/main/resources/static 后重新运行 init.py，"
            "或在 web 目录执行 npm run build。"
        )
        info(
            "修改 web/.env.local 后，start.ps1 会检测到它比产物新并自动重建。"
        )
    else:
        info(
            "未检测到 src/main/resources/static/index.html，"
            "执行 npm run build ..."
        )
        code = run_stream([STATE.npm_path, "run", "build"], WEB_DIR, timeout=1200)
        if code != 0:
            fail(
                "npm run build 失败（退出码 {}）。".format(code),
                "请查看上方 Vite 报错；修正后重新运行 init.py。",
            )
        if not STATIC_INDEX.is_file():
            fail(
                "构建完成后仍找不到 src/main/resources/static/index.html。",
                "请检查 web/vite.config.ts 的 build.outDir 配置。",
            )
        STATE.build_status = "本次已构建"
        ok("前端构建完成，产物已写入 src/main/resources/static/。")


# ---------------------------------------------------------------------------
# 摘要与入口
# ---------------------------------------------------------------------------
def print_summary():
    step("完成")
    print("")
    print("=" * 64)
    print("  通驿物流系统 · 初始化完成")
    print("=" * 64)
    print("  Java        : {}".format(STATE.java_summary))
    print("  Node.js     : {}".format(STATE.node_summary))
    print("  npm         : {}".format(STATE.npm_summary))
    print("  MySQL       : {}，{}".format(STATE.db_desc, STATE.db_status))
    print("  前端依赖    : {}".format(STATE.deps_status))
    print("  前端静态产物: {}".format(STATE.build_status))
    print("  高德 Key    : {}".format(STATE.key_status))
    print("")
    print("  下一步：")
    print("    - Windows：双击 start.cmd，或在 PowerShell 运行 .\\start.ps1")
    print("    - 启动后访问：http://127.0.0.1:18080/")
    print(
        "    - 需要 Vite 开发服务器：.\\start.ps1 -Dev"
        "（http://127.0.0.1:5173/）"
    )
    if STATE.installed_tools:
        print("")
        print(
            "  [注意] 本次通过 winget 安装了：{}。".format(
                "、".join(STATE.installed_tools)
            )
        )
        print(
            "         请关闭并重新打开终端后再运行 start.cmd / start.ps1，"
            "以刷新 PATH。"
        )
    if STATE.key_status.startswith("尚未"):
        print("")
        print(
            "  提示：轨迹地图需要高德 Key。编辑 web/.env.local 的 "
            "VITE_AMAP_JS_API_KEY 后，"
        )
        print("        重新运行 start.ps1（会自动重建）或 cd web && npm run build。")
    print("=" * 64)


def check_python():
    step("检查 Python 版本")
    version = "{}.{}.{}".format(
        sys.version_info[0], sys.version_info[1], sys.version_info[2]
    )
    if sys.version_info < (3, 9):
        fail(
            "需要 Python 3.9 或更高版本，当前为 {}。".format(version),
            "请安装 Python 3.9+（https://www.python.org/downloads/），"
            "Windows 也可以直接双击 init.cmd 重新运行。",
        )
    ok("Python {}：{}".format(version, sys.executable))


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description=(
            "通驿物流系统一键初始化：检查环境、准备 MySQL、"
            "初始化/增量升级数据库、安装前端依赖并按需构建。"
            "不会启动 Spring Boot，也不会打开浏览器。"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例：\n"
            "  python init.py\n"
            "  python init.py --reset-db   "
            "# 危险：清空 logistics 业务表并重建演示数据\n"
        ),
    )
    parser.add_argument(
        "--reset-db",
        action="store_true",
        help="危险：删除 logistics 的业务表并重新执行 sql/schema.sql + sql/seed.sql",
    )
    return parser.parse_args(argv)


def run(argv):
    args = parse_args(argv)
    print("=" * 64)
    print("  通驿物流系统 · 一键初始化")
    print("  只准备环境、数据库和前端，不启动后端")
    print("=" * 64)

    check_python()
    ensure_java()
    ensure_node()
    db = resolve_database()
    initialize_database(db, args.reset_db)
    prepare_frontend()
    print_summary()
    return 0


def main(argv=None):
    _configure_output()
    try:
        return run(argv)
    except InitError as exc:
        sys.stdout.flush()
        print("", file=sys.stderr)
        print("[失败] {}".format(exc.message), file=sys.stderr, flush=True)
        if exc.next_step:
            print("下一步：{}".format(exc.next_step), file=sys.stderr, flush=True)
        return 1
    except KeyboardInterrupt:
        sys.stdout.flush()
        print("", file=sys.stderr)
        print(
            "[已取消] 初始化被用户中断；脚本没有主动清空业务数据。",
            file=sys.stderr,
            flush=True,
        )
        return 130
    except Exception as exc:
        sys.stdout.flush()
        print("", file=sys.stderr)
        print(
            "[失败] 初始化过程中出现未预期的错误：{}。".format(exc),
            file=sys.stderr,
            flush=True,
        )
        print(
            "下一步：请把上方错误信息发给维护者；脚本不会自动删库。",
            file=sys.stderr,
            flush=True,
        )
        if os.environ.get("TONGYI_INIT_DEBUG"):
            traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
