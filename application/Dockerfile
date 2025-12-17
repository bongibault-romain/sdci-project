FROM node:20-alpine

WORKDIR /app

# Copy only dependency files first (better cache)
COPY package*.json ./

# Install only production dependencies & clean cache
RUN npm install --omit=dev \
    && npm cache clean --force

# Copy app source
COPY --chown=node:node . .

# Switch to non-root user
USER node

# Set default parameters
ENV REMOTE_IP=127.0.0.1 \
    REMOTE_PORT=8080 \
    DEVICE_NAME=dev1 \
    SEND_PERIOD=5000
        
CMD ["sh", "-c", "node index.js --remote_ip ${REMOTE_IP} --remote_port ${REMOTE_PORT} --device_name ${DEVICE_NAME} --send_period ${SEND_PERIOD}"]