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

EXPOSE 8080

# Set default parameters
ENV LOCAL_IP=127.0.0.1 \
    LOCAL_PORT=8080 \
    LOCAL_NAME=srv

CMD ["sh", "-c", "node index.js --local_ip ${LOCAL_IP} --local_port ${LOCAL_PORT} --local_name ${LOCAL_NAME}"]