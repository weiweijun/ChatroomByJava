all:
	javac Server.java
	javac Client.java

clean :
	$(RM) *.class