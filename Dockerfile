FROM clojure
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN echo $DISCORD_BOT_TOKEN > token
CMD ["lein", "run"]