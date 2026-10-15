#!/usr/bin/env bash
# Uso: ./scripts/set-github-user.sh TU_USUARIO [NOMBRE_REPO]
# Sustituye el marcador TU_USUARIO_GITHUB (y opcionalmente el nombre del repo) en todo el proyecto.
set -euo pipefail
USER_NAME="${1:?Uso: $0 TU_USUARIO_GITHUB [NOMBRE_REPO]}"
REPO_NAME="${2:-MaKaDiMi}"
cd "$(dirname "$0")/.."
if [[ ! "$USER_NAME" =~ ^[A-Za-z0-9]([A-Za-z0-9-]{0,38})$ ]]; then
  echo "Usuario de GitHub no válido: $USER_NAME" >&2; exit 1
fi
FILES=$(grep -rIl --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude=set-github-user.sh "TU_USUARIO_GITHUB" . || true)
[ -z "$FILES" ] && { echo "No hay marcadores TU_USUARIO_GITHUB que sustituir."; exit 0; }
for f in $FILES; do
  sed -i "s#github.com/TU_USUARIO_GITHUB/MaKaDiMi#github.com/${USER_NAME}/${REPO_NAME}#g; s#repos/TU_USUARIO_GITHUB/MaKaDiMi#repos/${USER_NAME}/${REPO_NAME}#g; s#TU_USUARIO_GITHUB#${USER_NAME}#g" "$f"
done
# nombre del repo en BrandLinks
sed -i "s#const val REPO_NAME = \".*\"#const val REPO_NAME = \"${REPO_NAME}\"#" composeApp/src/commonMain/kotlin/com/kaspercito/simpmusic/utils/BrandLinks.kt
echo "Listo: enlaces apuntando a https://github.com/${USER_NAME}/${REPO_NAME}"
