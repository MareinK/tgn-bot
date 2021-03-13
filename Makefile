dev:
	sudo docker build -t tgn-bot .
	sudo docker run -it --rm --env-file .env tgn-bot
