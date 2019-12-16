FROM folioci/alpine-jre-openjdk8:latest

# Create a new group and user
RUN addgroup -S -g 999 folio && adduser -S -D -u 999 -G folio folio
USER folio

# Install missing font library for barcode images generation
RUN apk add --no-cache ttf-dejavu

ENV VERTICLE_FILE mod-template-engine-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
