
remove: stop undeploy

renew: stop undeploy deploy start

undeploy:
	-rm ~/Software/apache-tomcat-8.0.27/webapps/stemmarest.war
	-rm -rf ~/Software/apache-tomcat-8.0.27/webapps/stemmarest/

deploy:
	-cp target/stemmarest.war ~/Software/apache-tomcat-8.0.27/webapps/

start:
	~/Software/apache-tomcat-8.0.27/bin/startup.sh

stop:
	~/Software/apache-tomcat-8.0.27/bin/shutdown.sh
	sleep 2

fill_db: insert_users insert_traditions

insert_users:
	curl -XPUT localhost:8080/stemmarest/user/1 -d '{"id": "1", "role": "admin"}' -v -H "Content-Type: application/json"
	curl -XPUT localhost:8080/stemmarest/user/2 -d '{"id": "2", "role": "admin"}' -v -H "Content-Type: application/json"
	curl -XPUT localhost:8080/stemmarest/user/3 -d '{"id": "3", "role": "admin"}' -v -H "Content-Type: application/json"

insert_traditions:
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/testTradition.xml" localhost:8080/stemmarest/tradition
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/Sapientia.xml" localhost:8080/stemmarest/tradition
	curl -v -XPUT -F filetype=graphml -F name=Tradition -F direction=LR -F userId=1 -F "file=@src/TestFiles/john.xml" localhost:8080/stemmarest/tradition
