FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Linha estável do nginx, variante slim: só o servidor, sem njs, xslt, geoip
# nem image filter (e as bibliotecas que eles trazem).
FROM nginxinc/nginx-unprivileged:1.30-alpine-slim
# Correções de segurança do Alpine publicadas depois do build da imagem base.
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
