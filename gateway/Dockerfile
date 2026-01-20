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

EXPOSE 8181

# Set default parameters
ENV LOCAL_IP=127.0.0.1 \
    LOCAL_PORT=8181 \
    LOCAL_NAME=gwi \
    REMOTE_IP=127.0.0.1 \
    REMOTE_PORT=8080 \
    REMOTE_NAME=srv \
    RATE=2

CMD ["sh", "-c", "node index.js --local_ip ${LOCAL_IP} --local_port ${LOCAL_PORT} --local_name ${LOCAL_NAME} --remote_ip ${REMOTE_IP} --remote_port ${REMOTE_PORT} --remote_name ${REMOTE_NAME} --rate ${RATE}"]