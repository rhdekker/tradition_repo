initialize: deploy start

remove: stop undeploy

renew: stop undeploy deploy start

restart: stop start

redeploy: undeploy deploy

undeploy:
	-curl "http://marijn:admin@localhost:8080/manager/text/undeploy?path=/stemmarest"

deploy:
	-wget "http://marijn:admin@localhost:8080/manager/text/deploy?path=/stemmarest&war=file:./target/stemmarest.war" -O - -q

start:
	~/Software/apache-tomcat-8.0.27/bin/startup.sh

stop:
	~/Software/apache-tomcat-8.0.27/bin/shutdown.sh
	sleep 2

check:
	ps -elf | grep java | grep -v netbeans

fill_db: insert_users insert_traditions

insert_users:
	curl -XPUT localhost:8080/stemmarest/user/1 -d '{"id": "1", "role": "admin"}' -v -H "Content-Type: application/json"
	curl -XPUT localhost:8080/stemmarest/user/2 -d '{"id": "2", "role": "admin"}' -v -H "Content-Type: application/json"
	curl -XPUT localhost:8080/stemmarest/user/3 -d '{"id": "3", "role": "admin"}' -v -H "Content-Type: application/json"

insert_traditions:
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/testTradition.xml" localhost:8080/stemmarest/tradition
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/Sapientia.xml" localhost:8080/stemmarest/tradition
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/john.xml" localhost:8080/stemmarest/tradition
