param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Message = ""
)

function Run {
    param([string]$Cmd)
    Write-Host "> $Cmd" -ForegroundColor Cyan
    Invoke-Expression $Cmd
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FALLO: $Cmd (codigo $LASTEXITCODE). Me detengo aqui." -ForegroundColor Red
        exit 1
    }
}

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "La version debe verse asi: 1.1.3" -ForegroundColor Red
    exit 1
}
if ($Message -eq "") { $Message = "Version $Version" }

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne "main") {
    Write-Host "Estas en la rama '$branch'. Cambia a main primero: git checkout main" -ForegroundColor Red
    exit 1
}
if ((git tag -l "v$Version")) {
    Write-Host "El tag v$Version ya existe. Usa otro numero de version." -ForegroundColor Red
    exit 1
}

# 1. Traer lo que haya en GitHub (evita los rechazos "non-fast-forward")
Run "git pull --rebase --autostash origin main"

# 2. Poner la version en gradle\libs.versions.toml (versionName y versionCode + 1)
$toml = Join-Path (Get-Location) "gradle\libs.versions.toml"
$text = [IO.File]::ReadAllText($toml)
$text = [regex]::Replace($text, 'version-name = "[^"]*"', "version-name = `"$Version`"")
$text = [regex]::Replace($text, 'version-code = "(\d+)"', { param($m) 'version-code = "' + ([int]$m.Groups[1].Value + 1) + '"' })
[IO.File]::WriteAllText($toml, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host (Select-String -Path $toml -Pattern '^version-(name|code)' | ForEach-Object { $_.Line }) -ForegroundColor Yellow

# 3. Que no se cuele una marca de conflicto (lo que rompio main antes)
$marks = git grep -n -E "^(<<<<<<< |>>>>>>> )" -- . ':!*.patch'
if ($marks) {
    Write-Host "Hay marcas de conflicto sin resolver:" -ForegroundColor Red
    Write-Host $marks
    exit 1
}

# 4. Commit, push, tag y push del tag (esto dispara el release)
Run "git add -A"
Run "git commit -m `"$Message`""
Run "git push origin main"
Run "git tag v$Version"
Run "git push origin v$Version"
Write-Host "Listo: v$Version subida. Mira el release en GitHub > Actions." -ForegroundColor Green
