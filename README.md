# ChatroomByJava

The project has been developed and tested with java 1.7. The project is made up of 3 parts: Server.java， Client.java and User.java. The User class is defined to store some basic information of a user. 

Directions: The Makefile is used for compiling. The server should start running first, waiting for clients to connect to it. The user should open a new terminal for each user to operate.Frsit, user uses terminal to login. After logging in successfully, there will be an user interface.

How to run:
1. make
2. java Server 4242
3. (open n new terminal) java Client 127.0.0.1 4242
   (login):
4. Sample commads are just like the examples given in the homework. Can also take a look at the Test_Report.pdf 
5. Expalnations of code are notes in the code files.
6. additional features: 
(1)When A requests for B’s IP address, the message centre should notify B that A wants to talk it. If B agrees to the conversation, the server should provide A with B’s IP address. Else, A cannot initiate a conversation with B.			

When A requests for B’s IP address, the message centre should check B’s blacklist preferences. If B’s blacklist includes A, the message centre should not provide B’s IP address to A.
(2)User interface.