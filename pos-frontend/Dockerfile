FROM node:22-alpine AS build
ARG API_URL
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN sed -i "s#apiBaseUrl: '[^']*'#apiBaseUrl: '${API_URL}'#" src/app/environments/environments.prod.ts \
 && grep apiBaseUrl src/app/environments/environments.prod.ts \
 && npx ng build --configuration production

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/*/browser /usr/share/nginx/html
EXPOSE 80
