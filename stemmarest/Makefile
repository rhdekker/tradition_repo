

deploy:
	~/tomcat_stop
	rm ~/Software/apache-tomcat-8.5.5/webapps/stemmarest.war
	rm -rf ~/Software/apache-tomcat-8.5.5/webapps/stemmarest/
	mv target/stemmarest.war ~/Software/apache-tomcat-8.5.5/webapps/
	~/tomcat_start
