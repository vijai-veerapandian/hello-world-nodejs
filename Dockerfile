# Stage 1: Build dependencies
# This stage installs production dependencies to keep the final image lean.
FROM node:18-alpine AS deps
WORKDIR /app

# Copy package files and install only production dependencies
COPY package.json package-lock.json* ./
RUN npm install --only=production

# Stage 2: Build the application
# This stage copies the source code and the installed dependencies.
FROM node:18-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Stage 3: Production image
# This is the final, optimized image.
FROM node:18-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built assets from the builder stage
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=builder /app/server.js ./
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]