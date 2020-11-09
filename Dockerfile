FROM folioci/alpine-jre-openjdk11:latest

# Install missing font library for barcode images generation (must be done as root)
USER root

RUN apk add --no-cache libx11
RUN apk add --no-cache libxrender
RUN apk add --no-cache ttf-dejavu

USER folio

ENV VERTICLE_FILE mod-template-engine-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
