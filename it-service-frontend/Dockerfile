# 1. Aşama: Build (Derleme)
FROM node:22-alpine AS builder 
WORKDIR /app

# Gerisi tamamen aynı kalacak...
COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# 2. Aşama: Run (Çalıştırma)
FROM nginx:stable-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]