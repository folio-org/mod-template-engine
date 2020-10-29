FROM folioci/alpine-jre-openjdk11:latest

# Install missing font library for barcode images generation (must be done as root)
USER root
RUN apk add --no-cache ttf-dejavu
RUN apk add --no-cache fontconfig
RUN apk add --no-cache freetype

USER folio

ENV VERTICLE_FILE mod-template-engine-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
