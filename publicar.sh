#!/usr/bin/env bash
# publicar.sh — sube la versión, hace git add/commit/tag y push, de una sola vez.
#
# Uso (desde la raíz del repo):
#   ./publicar.sh                      # +1 al parche: 1.1.8 -> 1.1.9
#   ./publicar.sh minor                # 1.1.8 -> 1.2.0
#   ./publicar.sh major                # 1.1.8 -> 2.0.0
#   ./publicar.sh 1.3.0                # versión exacta
#   ./publicar.sh --actual             # publica la versión que ya está (sin subirla)
#   ./publicar.sh -m "Arreglé X"       # notas a mano (si no, usa tus commits desde el último tag)
#   ./publicar.sh -y                   # sin preguntar
#   ./publicar.sh --sin-push           # commit + tag en local, sin subir
#   ./publicar.sh --simular            # muestra qué haría, sin tocar nada
set -euo pipefail
cd "$(dirname "$0")"

TOML="gradle/libs.versions.toml"
BUMP="patch"; NOTES=""; YES=0; PUSH=1; DRY=0; KEEP=0

while [ $# -gt 0 ]; do
  case "$1" in
    patch|minor|major) BUMP="$1" ;;
    --actual) KEEP=1 ;;
    -m|--mensaje) shift; NOTES="${1:-}" ;;
    -y|--si) YES=1 ;;
    --sin-push) PUSH=0 ;;
    --simular) DRY=1 ;;
    -h|--ayuda) sed -n 2,14p "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    [0-9]*.[0-9]*.[0-9]*) BUMP="$1" ;;
    *) echo "Opción desconocida: $1 (usa --ayuda)"; exit 1 ;;
  esac
  shift
done

die() { echo "✖ $*" >&2; exit 1; }
run() { if [ "$DRY" = 1 ]; then echo "  [simulación] $*"; else "$@"; fi; }

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Esto no es un repositorio git. Ejecútalo en la raíz de tu repo."
[ -f "$TOML" ] || die "No encuentro $TOML"
git remote get-url origin >/dev/null 2>&1 || die "No hay remoto 'origin'. Agrégalo: git remote add origin <url>"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" != "HEAD" ] || die "Estás en 'detached HEAD'. Cambia a una rama (git switch main)."

CUR_NAME="$(grep -E '^version-name' "$TOML" | head -1 | sed -E 's/.*"([^"]*)".*/\1/')"
CUR_CODE="$(grep -E '^version-code' "$TOML" | head -1 | sed -E 's/.*"([^"]*)".*/\1/')"
[[ "$CUR_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "version-name inválido en $TOML: '$CUR_NAME'"
[[ "$CUR_CODE" =~ ^[0-9]+$ ]] || die "version-code inválido en $TOML: '$CUR_CODE'"

if [ "$KEEP" = 1 ]; then
  NEW_NAME="$CUR_NAME"; NEW_CODE="$CUR_CODE"
else
  IFS=. read -r MA MI PA <<<"$CUR_NAME"
  case "$BUMP" in
    patch) PA=$((PA+1)) ;;
    minor) MI=$((MI+1)); PA=0 ;;
    major) MA=$((MA+1)); MI=0; PA=0 ;;
    *) IFS=. read -r MA MI PA <<<"$BUMP" ;;
  esac
  NEW_NAME="$MA.$MI.$PA"; NEW_CODE=$((CUR_CODE+1))
fi
TAG="v$NEW_NAME"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && [ "$KEEP" = 0 ] && die "El tag $TAG ya existe. Elige otra versión."
if [ "$KEEP" = 1 ] && git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  die "El tag $TAG ya está en GitHub. Usa ./publicar.sh sin --actual para subir de versión."
fi

# ¿Hay cambios nuevos en el remoto que no tengas? (evita un push rechazado)
if git fetch -q origin "$BRANCH" 2>/dev/null; then
  BEHIND="$(git rev-list --count "HEAD..origin/$BRANCH" 2>/dev/null || echo 0)"
  [ "$BEHIND" = 0 ] || die "Tu rama está $BEHIND commit(s) atrás de origin/$BRANCH. Haz primero: git pull --rebase"
fi

# Notas: -m, o los commits desde el último tag, o una línea por defecto
if [ -z "$NOTES" ]; then
  LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
  RANGE="${LAST_TAG:+$LAST_TAG..HEAD}"
  NOTES="$(git log ${RANGE:-} --no-merges --pretty='- %s' 2>/dev/null | grep -v -E '^- Release v' | head -20 || true)"
  [ -n "$NOTES" ] || NOTES="- Mejoras y correcciones"
else
  NOTES="$(printf '%s\n' "$NOTES" | sed -E '/^[[:space:]]*-/! s/^/- /')"
fi

echo "──────────────────────────────────────────"
echo " Rama:     $BRANCH"
echo " Versión:  $CUR_NAME ($CUR_CODE)  →  $NEW_NAME ($NEW_CODE)   tag $TAG"
echo " Notas:"; printf '%s\n' "$NOTES" | sed 's/^/   /'
echo " Cambios sin commitear: $(git status --short | wc -l | tr -d ' ') archivo(s)"
git status --short | sed 's/^/   /' | head -15 || true
echo "──────────────────────────────────────────"
if [ "$YES" = 0 ] && [ "$DRY" = 0 ]; then
  read -r -p "¿Continuar? (s/N) " ok
  [ "$(printf '%s' "$ok" | tr 'A-Z' 'a-z')" = "s" ] || { echo "Cancelado."; exit 0; }
fi

# 1) Subir la versión
if [ "$KEEP" = 0 ]; then
  run sed -i -E "s/^version-name = \"[^\"]*\"/version-name = \"$NEW_NAME\"/; s/^version-code = \"[^\"]*\"/version-code = \"$NEW_CODE\"/" "$TOML"
fi

# 2) Notas de versión (solo si no existen ya, para no pisar las que escribas a mano)
if [ "$DRY" = 0 ]; then
  mkdir -p "changelogs/$NEW_NAME" "fastlane/metadata/android/en-US/changelogs"
  for L in es-ES en-US; do
    F="changelogs/$NEW_NAME/$L.md"
    [ -f "$F" ] || printf '# MaKaDiMi %s\n\n%s\n' "$NEW_NAME" "$NOTES" > "$F"
  done
  FL="fastlane/metadata/android/en-US/changelogs/$NEW_CODE.txt"
  [ -f "$FL" ] || printf '%s\n' "$NOTES" | head -c 500 > "$FL"
else
  echo "  [simulación] crear changelogs/$NEW_NAME/{es-ES,en-US}.md y fastlane .../$NEW_CODE.txt"
fi

# 3) git add / commit / tag / push
run git add -A
if [ "$DRY" = 1 ] || ! git diff --cached --quiet; then
  run git commit -q -m "Release $TAG" -m "$NOTES"
else
  echo "  (sin cambios que commitear; solo se crea el tag)"
fi
run git tag -a "$TAG" -m "MaKaDiMi $TAG"
if [ "$DRY" = 1 ]; then
  echo "✔ Simulación terminada: no se tocó nada."
elif [ "$PUSH" = 1 ]; then
  git push -u origin "$BRANCH"
  git push origin "$TAG"
  echo "✔ Listo: $TAG subido. GitHub Actions compilará el APK y creará el Release."
else
  echo "✔ Commit y tag $TAG creados en local. Para subir: git push -u origin $BRANCH && git push origin $TAG"
fi
