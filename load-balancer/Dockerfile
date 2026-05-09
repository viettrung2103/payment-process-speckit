# Payment System Load Balancer Dockerfile
# Based on nginx with Lua support for advanced rate limiting and dynamic configuration

FROM --platform=$TARGETPLATFORM nginx:1.25-alpine

# Install required packages for Lua support and utilities
RUN apk add --no-cache \
    nginx-mod-http-lua \
    lua5.1 \
    lua5.1-cjson \
    curl \
    bash \
    && rm -rf /var/cache/apk/*

# Create necessary directories
RUN mkdir -p /etc/nginx/lua /var/log/nginx /var/cache/nginx /tmp/nginx /var/run/nginx

# Note: nginx.conf and conf.d are mounted as volumes in docker-compose.yml
# Copy Lua scripts for advanced logic (these are copied during build)
COPY load-balancer/nginx/lua/ /etc/nginx/lua/

# Create nginx user and set permissions
RUN chown -R nginx:nginx /var/log/nginx /var/cache/nginx /tmp/nginx /etc/nginx /var/run/nginx

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/nginx_status || exit 1

# Expose ports
EXPOSE 80 8081

# Use non-root user
USER nginx

# Start nginx
CMD ["nginx", "-g", "daemon off;"]