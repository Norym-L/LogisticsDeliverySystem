@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul 2>&1

rem 优先使用 Python Launcher 的 py -3；没有则回退 python。
rem 先确认版本为 3.9+，避免用旧解释器直接执行后出现难懂的语法错误。
set "PY_CMD="
py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
if not errorlevel 1 set "PY_CMD=py -3"
if not defined PY_CMD (
    python -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
    if not errorlevel 1 set "PY_CMD=python"
)

if not defined PY_CMD (
    echo [失败] 未找到 Python 3.9 或更高版本。
    echo 下一步：请安装 Python 3.9+（https://www.python.org/downloads/），
    echo         安装时勾选 "Add python.exe to PATH"，然后重新双击 init.cmd。
    pause
    endlocal & exit /b 1
)

%PY_CMD% "%~dp0init.py" %*
set "RC=%ERRORLEVEL%"

echo.
if not "%RC%"=="0" (
    echo [失败] 初始化未完成，请查看上方原因和下一步。
    pause
    endlocal & exit /b %RC%
)

echo 初始化完成。按任意键关闭此窗口，然后双击 start.cmd 启动系统。
pause >nul
endlocal & exit /b 0
