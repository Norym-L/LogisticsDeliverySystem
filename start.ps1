#requires -Version 5.1
<#
.SYNOPSIS
    通驿物流系统 Windows 一键启动脚本。
.DESCRIPTION
    在仓库根目录双击 start.cmd，或在 PowerShell 中执行 .\start.ps1。
    脚本会依次检查 Java 21 / Maven Wrapper / Node.js / npm，准备 MySQL 3308，
    按需初始化或增量升级数据库，按需安装依赖并构建前端，最后在新窗口启动后端，
    等待 18080 就绪后用默认浏览器打开。
.PARAMETER Dev
    额外启动 Vite 开发服务器（http://127.0.0.1:5173/）。默认不启动。
.NOTES
    本脚本兼容 Windows PowerShell 5.1 与 PowerShell 7；仅用于 Windows。
    不会写入或修改 web/.env.local 中的高德 Key，也不会提交任何构建产物。
#>
[CmdletBinding()]
param(
    [switch]$Dev
)

$ErrorActionPreference = 'Continue'

$script:Root = $null
$script:Mvnw = $null
$script:DockerExe = $null
$script:MysqlExe = $null
$script:NpmExe = $null
$script:DbMode = 'none'
$script:DbContainer = 'logistics-mysql'
$script:LastDbError = ''
$script:Step = 0
$script:TotalSteps = 6

trap {
    Write-Host ''
    Write-Host ('[失败] 未处理的错误：' + $_.Exception.Message) -ForegroundColor Red
    Write-Host '下一步：请检查上方信息后重试；如果仍无法解决，请把错误信息发给维护者。' -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------------------
# 控制台 UTF-8，避免中文乱码
# ---------------------------------------------------------------------------
try {
    $utf8 = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [Console]::OutputEncoding = $utf8
    $OutputEncoding = $utf8
} catch { }
try {
    if (Get-Command chcp.com -ErrorAction SilentlyContinue) {
        $null = & chcp.com 65001 2>&1
    }
} catch { }
try { $Host.UI.RawUI.WindowTitle = '通驿物流系统一键启动' } catch { }

# ---------------------------------------------------------------------------
# 始终以脚本所在目录作为仓库根目录
# ---------------------------------------------------------------------------
if ($PSScriptRoot) {
    $script:Root = $PSScriptRoot
} else {
    $script:Root = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if (-not $script:Root) {
    Write-Host '[失败] 无法确定脚本所在目录。' -ForegroundColor Red
    exit 1
}
Set-Location -LiteralPath $script:Root

if ($env:OS -ne 'Windows_NT') {
    Write-Host ''
    Write-Host '[失败] 本脚本仅支持 Windows。' -ForegroundColor Red
    Write-Host '下一步：请在 Windows 的 PowerShell 5.1 或 PowerShell 7 中运行；Linux / macOS 请按 README 手动步骤启动。' -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------------------
# 输出辅助
# ---------------------------------------------------------------------------
function Write-Step {
    param([string]$Text)
    $script:Step = $script:Step + 1
    Write-Host ''
    Write-Host ('[' + $script:Step + '/' + $script:TotalSteps + '] ' + $Text) -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Text)
    Write-Host ('  [OK] ' + $Text) -ForegroundColor Green
}

function Write-Info {
    param([string]$Text)
    Write-Host ('  ' + $Text) -ForegroundColor Gray
}

function Write-Warn {
    param([string]$Text)
    Write-Host ('  [注意] ' + $Text) -ForegroundColor Yellow
}

function Fail {
    param(
        [string]$Message,
        [string]$NextStep
    )
    Write-Host ''
    Write-Host ('[失败] ' + $Message) -ForegroundColor Red
    if ($NextStep) {
        Write-Host ('下一步：' + $NextStep) -ForegroundColor Yellow
    }
    exit 1
}

# ---------------------------------------------------------------------------
# 端口探测与等待
# ---------------------------------------------------------------------------
function Test-TcpPort {
    param(
        [string]$TargetHost = '127.0.0.1',
        [int]$Port = 0,
        [int]$TimeoutMs = 800
    )
    if ($Port -le 0) { return $false }

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        try { $client.Close() } catch { }
    }
}

function Wait-TcpPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds,
        [string]$Label
    )
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $nextNotice = 15
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if (Test-TcpPort -Port $Port -TimeoutMs 800) {
            return $true
        }
        if ($watch.Elapsed.TotalSeconds -ge $nextNotice) {
            Write-Host ('  等待' + $Label + '就绪，已 ' + [int]$watch.Elapsed.TotalSeconds + ' 秒 ...') -ForegroundColor DarkGray
            $nextNotice = $nextNotice + 15
        }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

# ---------------------------------------------------------------------------
# Java 版本
# ---------------------------------------------------------------------------
function Get-JavaMajorVersion {
    param([string]$JavaExe)
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $JavaExe
        $psi.Arguments = '-version'
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.CreateNoWindow = $true

        $proc = New-Object System.Diagnostics.Process
        $proc.StartInfo = $psi
        $null = $proc.Start()
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()

        $text = $stdout + "`n" + $stderr
        $match = [regex]::Match($text, 'version\s+"(\d+)(?:\.(\d+))?')
        if (-not $match.Success) { return 0 }

        $major = [int]$match.Groups[1].Value
        if ($major -eq 1 -and $match.Groups[2].Success) {
            $major = [int]$match.Groups[2].Value
        }
        return $major
    } catch {
        return 0
    }
}

# ---------------------------------------------------------------------------
# Docker 辅助
# ---------------------------------------------------------------------------
function Test-DockerDaemon {
    if (-not $script:DockerExe) { return $false }
    $null = & $script:DockerExe version --format '{{.Server.Version}}' 2>&1
    return ($LASTEXITCODE -eq 0)
}

function Test-DockerContainerExists {
    if (-not $script:DockerExe) { return $false }
    $null = & $script:DockerExe inspect --type container $script:DbContainer 2>&1
    return ($LASTEXITCODE -eq 0)
}

function Test-DockerContainerRunning {
    if (-not $script:DockerExe) { return $false }
    $state = (& $script:DockerExe inspect -f '{{.State.Running}}' $script:DbContainer 2>$null | Out-String).Trim()
    return ($state -eq 'true')
}

function Test-DockerContainerMaps3308 {
    if (-not $script:DockerExe) { return $false }
    $json = (& $script:DockerExe inspect -f '{{json .HostConfig.PortBindings}}' $script:DbContainer 2>$null | Out-String).Trim()
    if (-not $json) { return $false }
    try {
        $bindings = $json | ConvertFrom-Json
        $entries = $bindings.'3306/tcp'
        if ($null -eq $entries) { return $false }
        foreach ($entry in @($entries)) {
            if ($entry.HostPort -eq '3308') { return $true }
        }
    } catch {
        return $false
    }
    return $false
}

# ---------------------------------------------------------------------------
# 数据库命令执行（优先 docker exec，其次本机 mysql 客户端）
# ---------------------------------------------------------------------------
function Get-DockerMysqlOutput {
    param(
        [string[]]$MysqlArgs,
        [string]$InputText
    )
    if ($PSBoundParameters.ContainsKey('InputText')) {
        $output = $InputText | & $script:DockerExe exec -i -e 'MYSQL_PWD=123456' $script:DbContainer mysql -uroot --default-character-set=utf8mb4 @MysqlArgs 2>&1
    } else {
        $output = & $script:DockerExe exec -e 'MYSQL_PWD=123456' $script:DbContainer mysql -uroot --default-character-set=utf8mb4 @MysqlArgs 2>&1
    }
    return @{
        Output = $output
        Code   = $LASTEXITCODE
    }
}

function Get-MysqlClientOutput {
    param(
        [string[]]$ClientArgs,
        [string]$InputText
    )
    $previous = $env:MYSQL_PWD
    $hadPrevious = ($null -ne $previous)
    $env:MYSQL_PWD = '123456'
    try {
        if ($PSBoundParameters.ContainsKey('InputText')) {
            $output = $InputText | & $script:MysqlExe @ClientArgs 2>&1
        } else {
            $output = & $script:MysqlExe @ClientArgs 2>&1
        }
        $code = $LASTEXITCODE
    } finally {
        if ($hadPrevious) {
            $env:MYSQL_PWD = $previous
        } else {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
    return @{
        Output = $output
        Code   = $code
    }
}

function Invoke-DbQuery {
    param([string]$Sql)

    if ($script:DbMode -eq 'docker') {
        $result = Get-DockerMysqlOutput -MysqlArgs @('-N', '-B', '-e', $Sql)
    } elseif ($script:DbMode -eq 'mysql') {
        $result = Get-MysqlClientOutput -ClientArgs @('--protocol=TCP', '-h127.0.0.1', '-P3308', '-uroot', '--default-character-set=utf8mb4', '-N', '-B', '-e', $Sql)
    } else {
        throw '当前没有可用的数据库客户端。'
    }

    if ($result.Code -ne 0) {
        $text = (@($result.Output) | ForEach-Object { [string]$_ }) -join "`n"
        $script:LastDbError = $text.Trim()
        throw ('MySQL 查询失败：' + $script:LastDbError)
    }

    $lines = @()
    foreach ($line in @($result.Output)) {
        if ($null -ne $line) {
            $lines = $lines + [string]$line
        }
    }
    return $lines
}

function Get-SingleValue {
    param([string]$Sql)
    $lines = @(Invoke-DbQuery -Sql $Sql)
    $nonEmpty = @()
    foreach ($line in $lines) {
        $text = [string]$line
        if ([string]::IsNullOrWhiteSpace($text)) { continue }
        # 过滤 mysql 客户端写在 stderr 的提示/警告，避免它们被当成查询结果。
        if ($text -match '^(mysql:\s*\[Warning\]|WARNING:)') { continue }
        $nonEmpty = $nonEmpty + $text
    }
    if ($nonEmpty.Count -eq 0) {
        throw ('数据库查询没有返回结果：' + $Sql)
    }
    return ([string]$nonEmpty[$nonEmpty.Count - 1]).Trim()
}

function Get-ColumnCount {
    param(
        [string]$Table,
        [string[]]$Columns
    )
    $columnList = ($Columns | ForEach-Object { "'" + $_ + "'" }) -join ','
    $sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='logistics' AND TABLE_NAME='$Table' AND COLUMN_NAME IN ($columnList);"
    return [int](Get-SingleValue -Sql $sql)
}

function Invoke-DbFile {
    param([string]$RelativePath)

    $fullPath = Join-Path $script:Root $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        Fail ('找不到 SQL 文件：' + $RelativePath) '仓库不完整，请重新获取完整仓库后重试。'
    }

    Write-Info ('执行 ' + $RelativePath + ' ...')
    $sqlText = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8

    if ($script:DbMode -eq 'docker') {
        $result = Get-DockerMysqlOutput -MysqlArgs @() -InputText $sqlText
    } elseif ($script:DbMode -eq 'mysql') {
        $result = Get-MysqlClientOutput -ClientArgs @('--protocol=TCP', '-h127.0.0.1', '-P3308', '-uroot', '--default-character-set=utf8mb4') -InputText $sqlText
    } else {
        Fail '当前没有可用的数据库客户端，无法执行 SQL。' '请安装并启动 Docker Desktop，或安装 mysql 客户端后重试。'
    }

    foreach ($line in @($result.Output)) {
        if ($null -ne $line) {
            Write-Host ('    ' + [string]$line) -ForegroundColor DarkGray
        }
    }
    if ($result.Code -ne 0) {
        Fail ('执行 ' + $RelativePath + ' 失败（退出码 ' + $result.Code + '）。') '请查看上方 MySQL 报错。脚本已停止，没有删除数据库；修正问题后可重新运行。'
    }
    Write-Ok ($RelativePath + ' 执行完成。')
}

function Wait-DbAccess {
    param([int]$TimeoutSeconds = 120)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $null = Invoke-DbQuery -Sql 'SELECT 1;'
            return $true
        } catch {
            $script:LastDbError = $_.Exception.Message
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

function Resolve-DatabaseService {
    $portUp = Test-TcpPort -Port 3308 -TimeoutMs 1000
    $dockerReady = $false
    $containerExists = $false
    $containerRunning = $false
    $containerMaps3308 = $false

    if ($script:DockerExe) {
        $dockerReady = Test-DockerDaemon
        if ($dockerReady) {
            $containerExists = Test-DockerContainerExists
            if ($containerExists) {
                $containerRunning = Test-DockerContainerRunning
                $containerMaps3308 = Test-DockerContainerMaps3308
            }
        }
    }

    # 3308 已经可连接：优先确认是不是本项目管理的容器
    if ($portUp) {
        if ($script:DockerExe -and $dockerReady -and $containerExists -and $containerRunning -and $containerMaps3308) {
            $script:DbMode = 'docker'
            Write-Ok '3308 端口可连接，使用容器 logistics-mysql（docker exec 执行 SQL）。'
        } elseif ($script:MysqlExe) {
            $script:DbMode = 'mysql'
            Write-Ok '3308 端口可连接，但不是可管理的 logistics-mysql 容器；使用本机 mysql 客户端。'
        } else {
            $script:DbMode = 'none'
            Write-Warn '3308 端口已在监听，但既不是可管理的 logistics-mysql 容器，本机也没有 mysql 客户端。'
            Write-Warn '将跳过数据库自动检查/初始化，直接启动后端；若后端报缺表/缺列，请按 README 手动执行 SQL。'
            return
        }

        if (-not (Wait-DbAccess -TimeoutSeconds 60)) {
            Fail '3308 端口有服务，但无法用 root / 123456 连接 MySQL。' ('请确认账号密码与 application.yaml 一致。最近错误：' + $script:LastDbError)
        }
        return
    }

    # 3308 未就绪，只能依赖 Docker
    if (-not $script:DockerExe) {
        Fail 'MySQL 端口 3308 未启动，且本机没有 Docker。' '请先启动 MySQL 8（监听 127.0.0.1:3308，root / 123456），或安装并启动 Docker Desktop 后重试。'
    }
    if (-not $dockerReady) {
        Fail 'MySQL 端口 3308 未启动，Docker 引擎也不可用。' '请启动 Docker Desktop 并等待它完全就绪后重试；或自行启动监听 3308 的 MySQL 8。'
    }
    if ($containerExists -and -not $containerMaps3308) {
        Fail '已存在名为 logistics-mysql 的容器，但它没有把 3306 映射到宿主机 3308。' '脚本不会自动删除容器。请先确认该容器用途并手动处理（例如备份后改名或重建），再重新运行。'
    }

    if ($containerExists) {
        if (-not $containerRunning) {
            Write-Info '正在启动已有容器 logistics-mysql ...'
            $startOutput = & $script:DockerExe start $script:DbContainer 2>&1
            if ($LASTEXITCODE -ne 0) {
                foreach ($line in @($startOutput)) {
                    if ($null -ne $line) { Write-Host ('    ' + [string]$line) -ForegroundColor DarkGray }
                }
                Fail '启动容器 logistics-mysql 失败。' '请运行 docker logs logistics-mysql 查看原因，并确认 Docker Desktop 正常后重试。'
            }
            Write-Ok '容器 logistics-mysql 已启动。'
        } else {
            Write-Info '容器 logistics-mysql 已在运行，等待端口就绪 ...'
        }
    } else {
        Write-Info '未找到容器 logistics-mysql，正在创建 MySQL 8 容器（首次会下载镜像，可能较慢）...'
        $runOutput = & $script:DockerExe run -d --name $script:DbContainer -p 3308:3306 -e 'MYSQL_ROOT_PASSWORD=123456' -e 'TZ=Asia/Shanghai' mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci 2>&1
        if ($LASTEXITCODE -ne 0) {
            foreach ($line in @($runOutput)) {
                if ($null -ne $line) { Write-Host ('    ' + [string]$line) -ForegroundColor DarkGray }
            }
            Fail '创建 MySQL 容器失败。' '请检查 Docker Desktop 是否正常、3308 是否被占用、网络能否拉取 mysql:8.0 镜像，然后重试。'
        }
        Write-Ok '容器 logistics-mysql 已创建。'
    }

    Write-Info '正在等待 MySQL 3308 就绪（最多 300 秒）...'
    if (-not (Wait-TcpPort -Port 3308 -TimeoutSeconds 300 -Label 'MySQL 3308')) {
        Fail '等待 MySQL 3308 就绪超时。' '请运行 docker logs logistics-mysql 查看日志；首次拉取镜像失败或 Docker Desktop 未就绪都可能导致超时。'
    }

    $script:DbMode = 'docker'
    if (-not (Wait-DbAccess -TimeoutSeconds 120)) {
        Fail 'MySQL 3308 已监听，但 root / 123456 无法登录。' ('若该容器不是本项目创建的，请自行检查密码。最近错误：' + $script:LastDbError)
    }
    Write-Ok 'MySQL 已就绪（容器 logistics-mysql，root / 123456）。'
}

function Initialize-Database {
    if ($script:DbMode -eq 'none') {
        Write-Warn '已跳过数据库初始化检查（原因见上一步）。'
        return
    }

    $version = Get-SingleValue -Sql 'SELECT VERSION();'
    if ($version -match 'MariaDB' -or $version -match '^5\.') {
        Fail ('检测到数据库版本 ' + $version + '，本项目需要 MySQL 8。') '请改用 MySQL 8.0（推荐 mysql:8.0 容器），不要使用 MariaDB 或 MySQL 5.7。'
    }
    Write-Info ('MySQL 版本：' + $version)

    $dbExists = ((Get-SingleValue -Sql "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='logistics';") -eq '1')
    $keyTables = 0
    if ($dbExists) {
        $keyTables = [int](Get-SingleValue -Sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='logistics' AND TABLE_NAME IN ('station','route_edge','operator','postman','delivery_order','order_route');")
    }

    # 只有库不存在、或库中完全没有本项目关键表时才允许执行 schema.sql。
    # schema.sql 自带 DROP TABLE，绝不能对已有业务数据的库盲目执行。
    if ((-not $dbExists) -or ($keyTables -eq 0)) {
        Write-Info '未检测到 logistics 库或本项目数据表，开始首次初始化（仅此情况会执行 schema.sql 的建表 DROP）。'
        Invoke-DbFile 'sql/schema.sql'
        Invoke-DbFile 'sql/seed.sql'
        $stationCount = [int](Get-SingleValue -Sql 'SELECT COUNT(*) FROM logistics.station;')
        if ($stationCount -lt 1) {
            Fail 'seed.sql 执行后 station 表仍为空。' '请查看上方 SQL 输出，并手动检查 sql/seed.sql。'
        }
        Write-Ok '数据库首次初始化完成（schema.sql + seed.sql）。'
        return
    }

    if ($keyTables -lt 6) {
        $expectedTables = @('station', 'route_edge', 'operator', 'postman', 'delivery_order', 'order_route')
        $presentTables = @(Invoke-DbQuery -Sql "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='logistics' AND TABLE_NAME IN ('station','route_edge','operator','postman','delivery_order','order_route');" | ForEach-Object { ([string]$_).Trim() })
        $missingTables = @()
        foreach ($tableName in $expectedTables) {
            if ($presentTables -notcontains $tableName) {
                $missingTables = $missingTables + $tableName
            }
        }
        if ($missingTables.Count -eq 0) {
            $missingTables = @('（表数量异常，请手动检查）')
        }
        Fail ('logistics 库已存在，但关键表不完整（缺少：' + ($missingTables -join '、') + '）。') '为避免 schema.sql 的 DROP 清空已有数据，脚本不会自动重建。请先备份；确认数据可丢弃后再手动执行 sql/schema.sql，否则按 README 顺序手动检查 sql/alter_pricing.sql、sql/alter_final_six.sql、sql/alter_track_map.sql、sql/alter_track_map_gcj02.sql。'
    }

    $businessRows = [int](Get-SingleValue -Sql 'SELECT (SELECT COUNT(*) FROM logistics.station) + (SELECT COUNT(*) FROM logistics.route_edge) + (SELECT COUNT(*) FROM logistics.operator) + (SELECT COUNT(*) FROM logistics.postman) + (SELECT COUNT(*) FROM logistics.delivery_order);')

    # 先做增量升级，避免旧表缺列时 seed.sql 插入失败。
    $pricingColumns = @('volume_cm3', 'quoted_price', 'actual_volume_cm3', 'actual_weight', 'price', 'price_confirmed_at', 'price_confirmed_by')
    $pricingCount = Get-ColumnCount -Table 'delivery_order' -Columns $pricingColumns
    if ($pricingCount -lt $pricingColumns.Count) {
        Write-Warn '检测到旧库缺少运费相关字段，按 README 执行 sql/alter_pricing.sql（可重复执行，不删数据）。'
        Invoke-DbFile 'sql/alter_pricing.sql'
        $pricingCount = Get-ColumnCount -Table 'delivery_order' -Columns $pricingColumns
        if ($pricingCount -lt $pricingColumns.Count) {
            Fail 'alter_pricing.sql 执行后仍有运费字段缺失。' '请按 README 手动检查数据库结构，不要直接重建库。'
        }
    } else {
        Write-Ok '运费字段已是最新，跳过 alter_pricing.sql。'
    }

    $finalSixColumns = @('cancel_time', 'price_confirmed_operator', 'receipt_confirmed_at')
    $finalSixCount = Get-ColumnCount -Table 'delivery_order' -Columns $finalSixColumns
    $finalSixReasons = @()
    if ($finalSixCount -lt $finalSixColumns.Count) {
        $finalSixReasons = $finalSixReasons + '缺少期末六项字段'
    }

    $legacyApprovedCount = 0
    if ($businessRows -gt 0) {
        $legacyApprovedCount = [int](Get-SingleValue -Sql "SELECT COUNT(*) FROM logistics.delivery_order WHERE state='APPROVED' AND price_confirmed_at IS NULL;")
        if ($legacyApprovedCount -gt 0) {
            $finalSixReasons = $finalSixReasons + ('存在 ' + $legacyApprovedCount + ' 张旧版未核价的已批准单')
        }
    }

    $stateCheckCurrent = $true
    try {
        $stateCheckClause = Get-SingleValue -Sql "SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='logistics' AND CONSTRAINT_NAME='ck_order_state' LIMIT 1;"
        if (($stateCheckClause -notmatch 'AWAITING_RECEIPT') -or ($stateCheckClause -notmatch 'CANCELLED')) {
            $stateCheckCurrent = $false
        }
    } catch {
        $stateCheckCurrent = $false
    }
    if (-not $stateCheckCurrent) {
        $finalSixReasons = $finalSixReasons + '订单状态 CHECK 约束不是最新版本'
    }

    if ($finalSixReasons.Count -gt 0) {
        Write-Warn ('检测到旧结构：' + ($finalSixReasons -join '；') + '。执行 sql/alter_final_six.sql（可重复执行，不删数据）。')
        Write-Warn '该脚本会把旧版尚未核价的已批准单退回“待始发收件”，这是 README 说明的预期升级行为，不会删除订单。'
        Invoke-DbFile 'sql/alter_final_six.sql'
        $finalSixCount = Get-ColumnCount -Table 'delivery_order' -Columns $finalSixColumns
        if ($finalSixCount -lt $finalSixColumns.Count) {
            Fail 'alter_final_six.sql 执行后仍有关键字段缺失。' '请按 README 手动检查数据库结构，不要直接重建库。'
        }
        $legacyAfter = 0
        if ($businessRows -gt 0) {
            $legacyAfter = [int](Get-SingleValue -Sql "SELECT COUNT(*) FROM logistics.delivery_order WHERE state='APPROVED' AND price_confirmed_at IS NULL;")
        }
        if ($legacyAfter -gt 0) {
            Write-Warn ('升级后仍有 ' + $legacyAfter + ' 张已批准单未核价，请手动检查订单状态。')
        }
    } else {
        Write-Ok '期末六项字段与状态约束已是最新，跳过 alter_final_six.sql。'
    }

    $trackColumns = @('latitude', 'longitude')
    $trackCount = Get-ColumnCount -Table 'station' -Columns $trackColumns
    if ($trackCount -lt $trackColumns.Count) {
        Write-Warn '检测到驿站表缺少经纬度列，执行 sql/alter_track_map.sql（写入 GCJ-02 坐标，不删数据）。'
        Invoke-DbFile 'sql/alter_track_map.sql'
        $trackCount = Get-ColumnCount -Table 'station' -Columns $trackColumns
        if ($trackCount -lt $trackColumns.Count) {
            Fail 'alter_track_map.sql 执行后经纬度列仍缺失。' '请按 README 手动检查 station 表结构，不要直接重建库。'
        }
    } else {
        $nullCoordCount = [int](Get-SingleValue -Sql 'SELECT COUNT(*) FROM logistics.station WHERE latitude IS NULL OR longitude IS NULL;')
        if ($nullCoordCount -gt 0) {
            Write-Warn ('有 ' + $nullCoordCount + ' 个驿站缺少坐标，执行 sql/alter_track_map.sql 补齐 GCJ-02 坐标。')
            Invoke-DbFile 'sql/alter_track_map.sql'
        } else {
            # 旧版 alter_track_map.sql 写入的是 WGS84；用 ST001 的旧坐标做一次精确标记检测。
            $oldWgsCount = [int](Get-SingleValue -Sql "SELECT COUNT(*) FROM logistics.station WHERE code='ST001' AND latitude=39.9068844 AND longitude=116.4845489;")
            if ($oldWgsCount -gt 0) {
                Write-Warn '检测到 ST001 仍是旧版 WGS84 坐标，执行 sql/alter_track_map_gcj02.sql 原地转换为 GCJ-02（只改经纬度）。'
                Invoke-DbFile 'sql/alter_track_map_gcj02.sql'
                $gcjCount = [int](Get-SingleValue -Sql "SELECT COUNT(*) FROM logistics.station WHERE code='ST001' AND latitude=39.9081515 AND longitude=116.4906138;")
                if ($gcjCount -lt 1) {
                    Write-Warn '坐标转换脚本执行后未检测到预期的 ST001 GCJ-02 坐标，请手动检查 sql/alter_track_map_gcj02.sql。'
                }
            } else {
                Write-Ok '驿站坐标已是当前版本（未检测到旧 WGS84 标记），跳过 alter_track_map_gcj02.sql。'
            }
        }
    }

    $missingCoordCount = [int](Get-SingleValue -Sql 'SELECT COUNT(*) FROM logistics.station WHERE latitude IS NULL OR longitude IS NULL;')
    if ($missingCoordCount -gt 0) {
        Write-Warn ('仍有 ' + $missingCoordCount + ' 个驿站缺少坐标；轨迹地图可能无法完整显示，请手动检查 station 表。')
    }

    if ($businessRows -eq 0) {
        Write-Info '数据表存在但基础数据为空，执行 sql/seed.sql 补齐演示数据（不会删除数据）。'
        Invoke-DbFile 'sql/seed.sql'
        $stationCount = [int](Get-SingleValue -Sql 'SELECT COUNT(*) FROM logistics.station;')
        if ($stationCount -lt 1) {
            Fail 'seed.sql 执行后 station 表仍为空。' '请查看上方 SQL 输出，并手动检查 sql/seed.sql。'
        }
    } else {
        Write-Ok '数据库已有业务数据，跳过 schema.sql / seed.sql。'
    }

    Write-Ok '数据库结构检查完成。'
}

# ---------------------------------------------------------------------------
# 前端
# ---------------------------------------------------------------------------
function Prepare-Frontend {
    $webDir = Join-Path $script:Root 'web'
    if (-not (Test-Path -LiteralPath $webDir)) {
        Fail '找不到 web 前端目录。' '请确认在完整仓库根目录运行本脚本。'
    }
    $packageJson = Join-Path $webDir 'package.json'
    if (-not (Test-Path -LiteralPath $packageJson)) {
        Fail 'web 目录下缺少 package.json。' '请确认仓库完整，或重新获取前端工程。'
    }

    $envLocal = Join-Path $webDir '.env.local'
    $envExample = Join-Path $webDir '.env.example'
    if (-not (Test-Path -LiteralPath $envLocal)) {
        if (-not (Test-Path -LiteralPath $envExample)) {
            Fail '缺少 web/.env.example。' '请确认仓库完整后重试。'
        }
        try {
            Copy-Item -LiteralPath $envExample -Destination $envLocal -ErrorAction Stop
        } catch {
            Fail ('创建 web/.env.local 失败：' + $_.Exception.Message) '请检查 web 目录权限后重试。'
        }
        Write-Warn '已从 web/.env.example 创建 web/.env.local。'
        Write-Warn '未填写 VITE_AMAP_JS_API_KEY 时系统仍可启动，但轨迹地图会提示缺少 Key。'
        Write-Warn '如需地图：编辑 web/.env.local 填入高德 Web 端 JS API Key，然后删除 src/main/resources/static 或重新运行本脚本（脚本会在 .env.local 更新后自动重建）。'
    } else {
        Write-Ok 'web/.env.local 已存在，保持原样不覆盖。'
        $envText = ''
        try {
            $envText = Get-Content -LiteralPath $envLocal -Raw -ErrorAction Stop
        } catch { }
        $keyMatch = [regex]::Match($envText, '(?m)^\s*VITE_AMAP_JS_API_KEY\s*=\s*(.*)$')
        $keyValue = ''
        if ($keyMatch.Success) {
            $keyValue = $keyMatch.Groups[1].Value.Trim().Trim('"').Trim("'")
        }
        if ([string]::IsNullOrWhiteSpace($keyValue)) {
            Write-Warn 'web/.env.local 中 VITE_AMAP_JS_API_KEY 为空：系统能启动，但轨迹地图会提示缺少 Key。'
        } else {
            Write-Ok '已检测到非空的高德 Key（不显示具体内容）。'
        }
    }

    $viteCmd = Join-Path $webDir 'node_modules\.bin\vite.cmd'
    $viteBin = Join-Path $webDir 'node_modules\.bin\vite'
    # 除 vite 启动器外，还要确认 tysha-fonts.css 依赖的自托管字体已安装，
    # 否则旧的 node_modules 会跳过 npm install，构建时才在 @fontsource 上报错。
    $fontCssFiles = @(
        'node_modules\@fontsource\inter-tight\latin-300.css',
        'node_modules\@fontsource\instrument-serif\latin-400.css',
        'node_modules\@fontsource\geist-mono\latin-400.css',
        'node_modules\@fontsource\noto-sans-sc\chinese-simplified-400.css',
        'node_modules\@fontsource\noto-serif-sc\chinese-simplified-400.css'
    )
    $viteMissing = (-not (Test-Path -LiteralPath $viteCmd) -and -not (Test-Path -LiteralPath $viteBin))
    $fontsMissing = @($fontCssFiles | Where-Object { -not (Test-Path -LiteralPath (Join-Path $webDir $_)) })
    if ($viteMissing -or $fontsMissing.Count -gt 0) {
        Write-Info '未检测到完整前端依赖（vite 或 @fontsource 字体缺失），执行 npm install（首次可能较慢）...'
        $installCode = 1
        Push-Location -LiteralPath $webDir
        try {
            & $script:NpmExe install
            $installCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        if ($installCode -ne 0) {
            Fail ('npm install 失败（退出码 ' + $installCode + '）。') '请检查网络与 npm 配置（代理/镜像），然后重新运行本脚本。'
        }
        $viteMissing = (-not (Test-Path -LiteralPath $viteCmd) -and -not (Test-Path -LiteralPath $viteBin))
        $fontsMissing = @($fontCssFiles | Where-Object { -not (Test-Path -LiteralPath (Join-Path $webDir $_)) })
        if ($viteMissing -or $fontsMissing.Count -gt 0) {
            Fail 'npm install 完成后仍缺少 vite 启动器或 @fontsource 字体文件。' '请检查 npm 安装日志与镜像配置；可删除 web\node_modules 后重新运行本脚本。'
        }
        Write-Ok '前端依赖安装完成。'
    } else {
        Write-Ok '前端依赖已存在（含 @fontsource 字体），跳过 npm install。'
    }

    $staticDir = Join-Path $script:Root 'src/main/resources/static'
    $staticIndex = Join-Path $staticDir 'index.html'
    $staticAssets = Join-Path $staticDir 'assets'
    $needBuild = $false

    if (-not (Test-Path -LiteralPath $staticIndex) -or -not (Test-Path -LiteralPath $staticAssets)) {
        $needBuild = $true
        Write-Info '前端构建产物不完整，需要执行 npm run build。'
    } elseif ((Test-Path -LiteralPath $envLocal) -and ((Get-Item -LiteralPath $envLocal -Force).LastWriteTimeUtc -gt (Get-Item -LiteralPath $staticIndex -Force).LastWriteTimeUtc)) {
        $needBuild = $true
        Write-Info 'web/.env.local 比构建产物新（可能刚配置 Key），需要重新构建前端。'
    } else {
        Write-Ok '已检测到前端构建产物，跳过 npm run build（二次启动更快）。'
        Write-Info '如需强制重建：删除 src/main/resources/static 后重新运行，或在 web 目录执行 npm run build。'
    }

    if ($needBuild) {
        Write-Info '正在构建前端（Vite）...'
        $buildCode = 1
        Push-Location -LiteralPath $webDir
        try {
            & $script:NpmExe run build
            $buildCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        if ($buildCode -ne 0) {
            Fail ('npm run build 失败（退出码 ' + $buildCode + '）。') '请查看上方 Vite 报错；修正后重新运行本脚本。'
        }
        if (-not (Test-Path -LiteralPath $staticIndex)) {
            Fail '构建完成后仍找不到 src/main/resources/static/index.html。' '请检查 web/vite.config.ts 的 build.outDir 配置。'
        }
        Write-Ok '前端构建完成，产物已写入 src/main/resources/static/。'
    }
}

# ---------------------------------------------------------------------------
# 后端与浏览器
# ---------------------------------------------------------------------------
function Test-ProjectBackend {
    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/station/list' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and ($response.Content -match 'ST001')) {
            return $true
        }
    } catch { }

    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and ($response.Content -match '通驿')) {
            return $true
        }
    } catch { }
    return $false
}

function Wait-BackendReady {
    param([int]$TimeoutSeconds = 180)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $nextNotice = 15
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if (Test-TcpPort -Port 18080 -TimeoutMs 800) {
            if (Test-ProjectBackend) { return $true }
        }
        if ($watch.Elapsed.TotalSeconds -ge $nextNotice) {
            Write-Host ('  等待后端就绪，已 ' + [int]$watch.Elapsed.TotalSeconds + ' 秒 ...') -ForegroundColor DarkGray
            $nextNotice = $nextNotice + 15
        }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

function Start-BackendServer {
    if (Test-TcpPort -Port 18080 -TimeoutMs 1000) {
        if (Test-ProjectBackend) {
            Write-Ok '检测到本项目后端已在 18080 运行，不重复启动。'
            return
        }
        Fail '端口 18080 已被占用，且没有识别到本项目的接口。' '若这是本项目正在启动的实例，请稍等片刻后重新运行；否则请关闭占用 18080 的程序（可用 Get-NetTCPConnection -LocalPort 18080 查看）再重试。'
    }

    $comSpec = $env:ComSpec
    if (-not $comSpec) { $comSpec = 'cmd.exe' }
    $backendCommand = 'chcp 65001 >nul && title 通驿物流后端 18080 && cd /d "' + $script:Root + '" && "' + $script:Mvnw + '" spring-boot:run -Dspring-boot.run.arguments=--server.port=18080'

    Write-Info '在新窗口启动后端：mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18080'
    try {
        Start-Process -FilePath $comSpec -ArgumentList @('/d', '/k', $backendCommand) -WorkingDirectory $script:Root -ErrorAction Stop | Out-Null
    } catch {
        Fail ('无法打开后端窗口：' + $_.Exception.Message) '可在仓库根目录手动执行 mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18080。'
    }

    Write-Info '等待后端 18080 就绪（最多 180 秒；首次启动会下载 Maven 依赖）...'
    if (-not (Wait-BackendReady -TimeoutSeconds 180)) {
        if (Test-TcpPort -Port 18080 -TimeoutMs 800) {
            Fail '18080 已监听，但接口未在 180 秒内就绪。' '请查看后端窗口日志（常见原因：数据库连接失败、缺表/缺列、启动异常），修正后重新运行。'
        } else {
            Fail '后端在 180 秒内没有监听 18080。' '请查看后端窗口日志。首次启动需要下载 Maven 依赖，可等网络稳定后重试（已下载的依赖会复用）。'
        }
    }
    Write-Ok '后端已就绪：http://127.0.0.1:18080/'
}

function Open-Browser {
    param([string]$Url)
    try {
        Start-Process -FilePath $Url -ErrorAction Stop | Out-Null
        Write-Host ('  已打开浏览器：' + $Url) -ForegroundColor Green
    } catch {
        Write-Warn ('未能自动打开浏览器，请手动访问 ' + $Url)
    }
}

function Test-VitePage {
    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and ($response.Content -match '通驿|/src/main.ts|vite')) {
            return $true
        }
    } catch { }
    return $false
}

function Start-DevServer {
    if (Test-TcpPort -Port 5173 -TimeoutMs 800) {
        if (Test-VitePage) {
            Write-Ok '检测到 5173 已有 Vite 页面，不重复启动。'
            return $true
        }
        Write-Warn '端口 5173 已被其他程序占用，跳过 npm run dev。'
        return $false
    }

    $webDir = Join-Path $script:Root 'web'
    $comSpec = $env:ComSpec
    if (-not $comSpec) { $comSpec = 'cmd.exe' }

    $npmForCmd = 'npm'
    if ($script:NpmExe -match '\.cmd$|\.exe$') {
        $npmForCmd = '"' + $script:NpmExe + '"'
    }
    $devCommand = 'chcp 65001 >nul && title 通驿前端 Vite 5173 && cd /d "' + $webDir + '" && ' + $npmForCmd + ' run dev -- --host 127.0.0.1'

    Write-Info '在新窗口启动 Vite 开发服务器：npm run dev（绑定 127.0.0.1）'
    try {
        Start-Process -FilePath $comSpec -ArgumentList @('/d', '/k', $devCommand) -WorkingDirectory $webDir -ErrorAction Stop | Out-Null
    } catch {
        Write-Warn ('无法打开 Vite 窗口：' + $_.Exception.Message)
        return $false
    }

    Write-Info '等待 Vite 5173 就绪（最多 120 秒）...'
    if (-not (Wait-TcpPort -Port 5173 -TimeoutSeconds 120 -Label 'Vite 5173')) {
        Write-Warn 'Vite 未在 120 秒内监听 5173，请查看前端窗口日志。'
        return $false
    }

    $pageReady = $false
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt 30) {
        if (Test-VitePage) {
            $pageReady = $true
            break
        }
        Start-Sleep -Milliseconds 1000
    }
    if (-not $pageReady) {
        Write-Warn '5173 已监听，但页面检查未通过；仍将打开 5173，请查看前端窗口日志。'
    }
    return $true
}

# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host '  通驿物流系统 · Windows 一键启动' -ForegroundColor Cyan
Write-Host '  默认入口：http://127.0.0.1:18080/（后端托管前端静态页）' -ForegroundColor Gray
Write-Host '  可选参数：.\start.ps1 -Dev 额外启动 Vite（5173）' -ForegroundColor Gray
Write-Host '============================================================' -ForegroundColor Cyan

Write-Step '检查运行环境（Java 21 / mvnw.cmd / Node.js / npm）'

# Java：Maven Wrapper 会优先使用 JAVA_HOME，因此两处都要检查
$pathJava = $null
$pathJavaMajor = 0
$javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCmd) { $javaCmd = Get-Command java -ErrorAction SilentlyContinue }
if ($javaCmd) {
    $pathJava = $javaCmd.Source
    if (-not $pathJava) { $pathJava = $javaCmd.Path }
}
if ($pathJava) {
    $pathJavaMajor = Get-JavaMajorVersion -JavaExe $pathJava
}

$javaHomeJava = $null
$javaHomeMajor = 0
if ($env:JAVA_HOME) {
    $javaHomeJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaHomeJava)) {
        Fail ('JAVA_HOME 已设置为 ' + $env:JAVA_HOME + '，但找不到 bin\java.exe。') '请修正或删除 JAVA_HOME 后重试（Maven Wrapper 会优先使用 JAVA_HOME）。'
    }
    $javaHomeMajor = Get-JavaMajorVersion -JavaExe $javaHomeJava
    if ($javaHomeMajor -eq 0) {
        Fail '无法识别 JAVA_HOME 指向的 java 版本。' '请把 JAVA_HOME 改为 JDK 21 的安装目录后重试。'
    }
    if ($javaHomeMajor -lt 21) {
        Fail ('JAVA_HOME 指向的 Java 主版本为 ' + $javaHomeMajor + '，Maven 会使用它，需要 JDK 21。') '请把 JAVA_HOME 改为 JDK 21 的安装目录后重试。'
    }
}

if (-not $pathJava -and -not $javaHomeJava) {
    Fail '未找到 Java。' '请安装 JDK 21（Oracle JDK 或 Eclipse Temurin 21），安装后重新打开窗口再运行，并确认 java -version 输出 21。'
}

if ($pathJava -and $pathJavaMajor -ge 21) {
    Write-Ok ('Java ' + $pathJavaMajor + '：' + $pathJava)
    if ($javaHomeJava) {
        Write-Info ('JAVA_HOME 也指向 JDK ' + $javaHomeMajor + '：' + $javaHomeJava)
    }
} elseif ($javaHomeJava -and $javaHomeMajor -ge 21) {
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    if ($pathJava) {
        if ($pathJavaMajor -eq 0) {
            Write-Warn 'PATH 中的 Java 版本无法识别，但 JAVA_HOME 指向 JDK 21；Maven 会使用 JAVA_HOME，继续。'
        } else {
            Write-Warn ('PATH 中的 Java 主版本为 ' + $pathJavaMajor + '，但 JAVA_HOME 指向 JDK ' + $javaHomeMajor + '；Maven 会使用 JAVA_HOME，继续。')
        }
    } else {
        Write-Warn 'PATH 中没有 java，已临时把 JAVA_HOME\bin 加入本窗口 PATH。'
    }
    Write-Ok ('Java ' + $javaHomeMajor + '：' + $javaHomeJava)
} elseif ($pathJava -and $pathJavaMajor -eq 0) {
    Fail '无法识别 java -version 输出。' '请安装 JDK 21，并确认 java -version 能正常输出版本号。'
} elseif ($pathJava) {
    Fail ('当前 Java 主版本为 ' + $pathJavaMajor + '，需要 JDK 21 或更高版本。') '请安装 JDK 21 并调整 PATH / JAVA_HOME 后重试。'
} else {
    Fail '未找到可用的 Java 21。' '请安装 JDK 21 后重试。'
}

# Maven Wrapper
$script:Mvnw = Join-Path $script:Root 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $script:Mvnw)) {
    Fail '仓库根目录缺少 mvnw.cmd。' '请确认在完整仓库根目录运行本脚本，或重新克隆仓库。'
}
Write-Ok '找到 Maven Wrapper：mvnw.cmd'

# Node.js
$nodeCmd = Get-Command node.exe -ErrorAction SilentlyContinue
if (-not $nodeCmd) { $nodeCmd = Get-Command node -ErrorAction SilentlyContinue }
$nodeExe = $null
if ($nodeCmd) {
    $nodeExe = $nodeCmd.Source
    if (-not $nodeExe) { $nodeExe = $nodeCmd.Path }
}
if (-not $nodeExe) {
    Fail '未找到 Node.js。' '请安装 Node.js 18 或更高版本（建议 LTS），安装后重新打开窗口再运行。'
}
$nodeVersionText = ''
try {
    $nodeVersionText = (& $nodeExe --version 2>&1 | Out-String).Trim()
} catch { }
$nodeMatch = [regex]::Match($nodeVersionText, 'v(\d+)')
if ($nodeMatch.Success) {
    $nodeMajor = [int]$nodeMatch.Groups[1].Value
    if ($nodeMajor -lt 18) {
        Fail ('当前 Node.js 主版本为 ' + $nodeMajor + '，Vite 6 需要 18 或更高版本。') '请升级 Node.js（建议 LTS）后重试。'
    }
    Write-Ok ('Node.js ' + $nodeVersionText)
} else {
    Write-Warn ('无法解析 Node.js 版本（' + $nodeVersionText + '），仍继续。')
}

# npm
$npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npmCmd) { $npmCmd = Get-Command npm -ErrorAction SilentlyContinue }
if ($npmCmd) {
    $script:NpmExe = $npmCmd.Source
    if (-not $script:NpmExe) { $script:NpmExe = $npmCmd.Path }
}
if (-not $script:NpmExe) {
    Fail '未找到 npm。' '请重新安装 Node.js（会同时安装 npm），或确认 npm 在 PATH 中后重试。'
}
Write-Ok ('npm：' + $script:NpmExe)

# 可选工具
$dockerCmd = Get-Command docker.exe -ErrorAction SilentlyContinue
if (-not $dockerCmd) { $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue }
if ($dockerCmd) {
    $script:DockerExe = $dockerCmd.Source
    if (-not $script:DockerExe) { $script:DockerExe = $dockerCmd.Path }
}
$mysqlCmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysqlCmd) { $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue }
if ($mysqlCmd) {
    $script:MysqlExe = $mysqlCmd.Source
    if (-not $script:MysqlExe) { $script:MysqlExe = $mysqlCmd.Path }
}
if ($script:DockerExe) {
    Write-Info ('Docker：' + $script:DockerExe)
} else {
    Write-Info 'Docker：未找到（若 3308 已有 MySQL 8 则不需要）'
}
if ($script:MysqlExe) {
    Write-Info ('mysql 客户端：' + $script:MysqlExe)
} else {
    Write-Info 'mysql 客户端：未找到（数据库初始化会优先使用 docker exec）'
}

Write-Step '准备 MySQL 3308'
Resolve-DatabaseService

Write-Step '检查并初始化数据库'
try {
    Initialize-Database
} catch {
    Fail ('数据库检查/初始化失败：' + $_.Exception.Message) '请查看上方 SQL 报错并手动修复；脚本不会自动删库。'
}

Write-Step '准备前端（依赖 / .env.local / 构建）'
try {
    Prepare-Frontend
} catch {
    Fail ('前端准备失败：' + $_.Exception.Message) '请查看上方报错；修正后重新运行本脚本。'
}

Write-Step '启动后端（18080）'
try {
    Start-BackendServer
} catch {
    Fail ('后端启动失败：' + $_.Exception.Message) '请查看后端窗口日志，修正后重新运行本脚本。'
}

Write-Step '打开浏览器'
Open-Browser 'http://127.0.0.1:18080/'
Write-Host ''
Write-Host '启动完成。后端在单独的窗口中运行；关闭那个窗口即可停止后端。' -ForegroundColor Green
Write-Host '演示账号：admin / 123456（路径管理员）；st001 ~ st008 / 123456（节点管理员）；pm001 ~ pm016 / 123456（快递员）。' -ForegroundColor Gray

if ($Dev) {
    if (Start-DevServer) {
        Open-Browser 'http://127.0.0.1:5173/'
    } else {
        Write-Warn 'Vite 开发服务器未就绪；18080 演示入口不受影响。'
    }
}

exit 0
