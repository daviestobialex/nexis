# Use official nginx image
FROM nginx:alpine

# Copy your HTML file(s) into Nginx default public folder
COPY ./index.html /usr/share/nginx/html/

# (Optional) Copy any other static assets (CSS, JS, images, etc.)
# COPY ./assets /usr/share/nginx/html/assets

# Copy SSL certificates
# Make sure these files exist: ./certs/server.crt and ./certs/server.key
COPY ./certs/server.crt /etc/ssl/certs/server.crt
COPY ./certs/private.key /etc/ssl/private/server.key

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Expose HTTPS (443) and HTTP (80)
EXPOSE 443


