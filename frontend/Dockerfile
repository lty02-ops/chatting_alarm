FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app

COPY --from=builder /app/dist ./dist
RUN npm install --global serve@14

EXPOSE 3000
CMD ["serve", "-s", "dist", "-l", "3000"]