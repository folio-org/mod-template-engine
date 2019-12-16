FROM folioci/alpine-jre-openjdk8:latest

USER root

# Install missing font library for barcode images generation
RUN apk add --no-cache ttf-dejavu

ENV VERTICLE_FILE mod-template-engine-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
