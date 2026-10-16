FROM node:latest
USER root
RUN apt-get update && apt-get install curl
ADD . /app
WORKDIR /app
CMD npm start
