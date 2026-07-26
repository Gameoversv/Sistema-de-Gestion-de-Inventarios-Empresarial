FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Vite hornea estas variables en el bundle durante el build, no las lee en runtime.
#
# Se declaran como ARG a proposito. Antes solo salian de `frontend/.env`, que esta en
# .gitignore: existia en las maquinas de desarrollo y NO en CI, asi que la imagen que
# construia el pipeline salia con VITE_API_BASE_URL vacia y el frontend caia a rutas
# relativas. Eso hacia que dependiera de unos proxies de nginx sobre /products, que a su
# vez impedian que /products fuese ruta del SPA. Un fichero no versionado decidiendo el
# comportamiento del despliegue.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=inventory
ARG VITE_KEYCLOAK_CLIENT_ID=inventory-frontend
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
