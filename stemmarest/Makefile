
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
