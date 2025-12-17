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

EXPOSE 9001

# Set default parameters
ENV LOCAL_IP=127.0.0.1 \
    LOCAL_PORT=9001 \
    LOCAL_NAME=dev1 \
    REMOTE_IP=127.0.0.1 \
    REMOTE_PORT=8282 \
    REMOTE_NAME=gwf1 \
    SEND_PERIOD=3000

CMD ["sh", "-c", "node index.js --local_ip ${LOCAL_IP} --local_port ${LOCAL_PORT} --local_name ${LOCAL_NAME} --remote_ip ${REMOTE_IP} --remote_port ${REMOTE_PORT} --remote_name ${REMOTE_NAME} --send_period ${SEND_PERIOD}"]