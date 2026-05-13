# Stage 1 — build
FROM node:20-alpine AS build
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10 --activate

# Cache dependencies layer
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile

# Build SPA
COPY . .
RUN pnpm build

# Stage 2 — runtime (nginx)
FROM nginx:alpine AS runtime

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config and built assets
COPY nginx.conf /etc/nginx/conf.d/autotix.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
