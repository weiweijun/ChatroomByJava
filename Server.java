
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class Server {
	static HashMap<String, User> usersInfo = new HashMap<String, User>();	
	static ArrayList<String> onlineUsers = new ArrayList<String>();
	static final int BLOCK_TIME = 60*1000;	/* 60s */

	
	public static void main(String[] args) throws Exception {
		usersInit();	/* initialize HashMap usersInfo */	
		Server server = new Server();
		server.go(args[0]);		
	}	
	private void go(String port) throws Exception{
		System.out.println("Server is running...");		
		TimeoutChecker timeoutChecker = new TimeoutChecker();
		Thread timer = new Thread(timeoutChecker);
		timer.start();

		ServerSocket serverSocket = new ServerSocket(Integer.parseInt(port));
		while(true) {
			ClientJob j = new ClientJob(serverSocket.accept());
			Thread t = new Thread(j);
			t.start();		
		}
	}

	/* a thread to check whether there is an user running out live time*/
	private class TimeoutChecker implements Runnable {
		private int rate = 100; /* check users liveness once per 100ms */
		public void run() {
			while (true) {
				try {
					Thread.sleep(rate);
				} catch (InterruptedException e) {
					continue;
				}
				if(!onlineUsers.isEmpty()) {
					synchronized ( this ) {	
						Iterator<String> i = onlineUsers.iterator();
						while (i.hasNext()) {
							String next = i.next();
							User u = usersInfo.get(next);
							u.onlineElapsed += rate;
							if (u.onlineElapsed > u.LIVE_TIME*1000) {
								sendMessage(next,"Time is up, You have logged out!");
								System.out.println("Time is up, "+next+" have logged out!");
								broadcast(next,"Time is up, <"+next+"> has logged out!");				
								u.onlineElapsed = 0;
								i.remove();
							}							
						}
					}										
				}						
			}
		}
	}

	/* inner class ClientJob for the thread to do, eachtime a user build a connection, it will create a new thread */
	private class ClientJob implements Runnable {
		Socket connectionSocket;
		BufferedReader inFromClient;
		DataOutputStream outToClient;
		
		/* constructor */
		public ClientJob(Socket s) {
			connectionSocket = s;
			try {
				inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			}catch(Exception e) {
				System.out.println("Exception in constructor 'ClientJob' in class ClientJob");
			}			
		}
		public void run() {
			System.out.println("\nConnection succeed...\n");

			String mClient = null;
			try {
				mClient = inFromClient.readLine();
			} catch (IOException e1) {
				System.out.println("Exception in method 'run' in class ClientJob");
			}
			String[] mClientArray = mClient.split("\t",2);

			/* to decide whether this connection is used for login or nomal message */
			if (mClientArray[0].equals("0")) {
				System.out.println("going to call login method");
				login(mClientArray[1]);		/* login */
				System.out.println("going out of login method");
				return;
			}
			
			/* nomal message */
			System.out.println("jump over login");			
			try {
				String cu = mClientArray[0];
				usersInfo.get(cu).onlineElapsed = 0;
				System.out.println("curent user:"+cu);
				String message = mClientArray[1];
				String[] m = message.split(" ");
	
				if (m[0].equals("message")) {					
					if (m.length < 3) {
						sendMessage(cu,"invalid command!");
						return;
					} else if (m.length > 3)
						m = message.split(" ",3);
					if (usersInfo.get(m[1]).blacklist.contains(cu)) {
						sendMessage(cu,"Your message could not be delivered as the recipient has blocked you.");
					}
					else
						sendMessage(m[1], "<"+cu+">:"+m[2]);						
				}					
				else if (m[0].equals("broadcast")) {
					if (m.length < 2) {
						sendMessage(cu,"invalid command!");
						return;
					} else if (m.length > 2)
						m = message.split(" ",2);
					broadcast(cu,m[1]);	
					for (String u:onlineUsers) {
						if (usersInfo.get(u).blacklist.contains(cu)) {
							sendMessage(cu,"Your message could not be delivered to some recipients.");
							break;
						}		
					}					
				}
				else if (m[0].equals("online")) {
					if(!onlineUsers.isEmpty()) {
						StringBuffer buffer = new StringBuffer();
						for (String u : onlineUsers) {
							if (!u.equals(cu))
								buffer.append(u+"\t");
						}						
						String usOn = buffer.toString();
						outToClient.writeBytes("Online:"+usOn+"\n");
					}
					else
						outToClient.writeBytes("No other user is online\n");	
				}
				else if (m[0].equals("getaddress")) {
					if (m.length < 2) {
						outToClient.writeBytes("invalid command!\n");
						return;
					}
					if (onlineUsers.contains(m[1])) {
						if(usersInfo.get(m[1]).blacklist.contains(cu))
							outToClient.writeBytes("You could not talk to <"+m[1]+"> privately, because you are blocked.\n");
						else {
							outToClient.writeBytes("waiting for permission...\n");
							sendMessage(m[1],"Do you permit <"+cu+"> talk to you privately? Reply with 'permit <username> Y/N'");
						}
					}
					else
						outToClient.writeBytes("The request failed! <"+m[1]+"> is not online\n");
				
					
				}
				else if (m[0].equals("permit")) {
					if (m.length != 3) {
						sendMessage(cu,"invalid command!");
						return;
					} 
					if (m[2].equals("Y")) {
						User u = usersInfo.get(cu);
						String uAddr = u.ip + "\t" + u.PORT;
						sendMessage(m[1],"P2P "+cu+" "+uAddr);
					}
					else
						sendMessage(m[1],"<"+cu+"> refused your invitation.");		
				}
				else if (m[0].equals("logout")) {
					synchronized ( onlineUsers ) {
						onlineUsers.remove(cu);
					}
					usersInfo.get(cu).onlineElapsed = 0;
					broadcast(cu,"<"+cu+"> has logged out!");	
				}
				else if (m[0].equals("setLIVE_TIME")) {
					if (m.length < 2) {
						sendMessage(cu,"invalid command!");
					} else
						usersInfo.get(cu).LIVE_TIME = Integer.parseInt(m[1]);
				}
				else if (m[0].equals("LIVE")) {
					usersInfo.get(cu).onlineElapsed = 0;
				}
				else if (m[0].equals("block")) {
					if (m.length < 2) {
						outToClient.writeBytes("invalid command!\n");
					} 
					else {
						synchronized ( usersInfo ) {
							usersInfo.get(cu).blacklist.add(m[1]);
						}
						outToClient.writeBytes("User <"+m[1]+"> has been blocked\n");	
					}		
				}
				else if (m[0].equals("unblock")) {
					if (m.length < 2) {
						outToClient.writeBytes("invalid command!\n");
					} 
					else {
						synchronized ( usersInfo ) {
							usersInfo.get(cu).blacklist.remove(m[1]);
						}
						outToClient.writeBytes("User <"+m[1]+"> is unblocked\n");	
					}						
				}
				else
					sendMessage(cu,"invalid command!");			
			} catch (IOException e) {
				System.out.println("Exception in method 'run' in class ClientJob");
			}		
		}
		private void login(String raw){
			try {
				// flag = true;
				String[] m = raw.split("\t");
				String username = m[0];
				if (m.length == 1) {
					if (!usersInfo.containsKey(username)) {
						outToClient.writeBytes("Error: No such user!\n");
						return;
					}
					long blockElapsedTime = System.currentTimeMillis() - usersInfo.get(username).blockTime;
					if (blockElapsedTime < BLOCK_TIME) {
						outToClient.writeBytes("Error: Your account has been blocked. Please try again after sometime.\n");
						return;
					}
					outToClient.writeBytes("Password:"+"\n");
					System.out.println("get username");		
				} else if (m.length == 2) {
					String password = m[1];
					User u = usersInfo.get(username);
					String value = u.password;
					boolean auth = false;
					int loginState = ++u.loginState;
					auth = value.equals(password);
					if (auth) {
						outToClient.writeBytes("Finished login\n");
						u.loginState = 0;
						if (onlineUsers.contains(username)) {
							sendMessage(username,"Error: Someone log in from another IP, you have logged out!");
							onlineUsers.remove(username);
						}
						onlineUsers.add(username);
						System.out.println(onlineUsers);
						u.ip = getIp();
						u.PORT = inFromClient.readLine();
						broadcast(username,"<"+username+"> has logged in!");
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							System.out.println("Exception in method 'login'");
						}
						readOffMsg(username);				
					}else {
						if(loginState < 3) {
							outToClient.writeBytes("Invalid Password. Please try again\n");
						}
						else {
							outToClient.writeBytes("Error: Invalid Password. Your account has been blocked. Please try again after sometime.\n");
							u.blockTime = System.currentTimeMillis();
							u.loginState = 0;
						}							
					}
				}				
			}catch(Exception e) {
				System.out.println("Exception in method 'login'");
			}	
		}	
		private String getIp() {
			String ip = connectionSocket.getInetAddress().toString();
			ip = ip.substring(1,ip.length());
			return ip;
		}	

	}


	private void broadcast(String cu, String message) {
		if(!onlineUsers.isEmpty()) {
			synchronized ( this ) {
				for (String u:onlineUsers) {
					if (u.equals(cu) || usersInfo.get(cu).blacklist.contains(u)) 
						continue;
					else
						sendMessage(u,"<"+cu+">[Broadcast]:"+message);
				}					
			}
		}		
	}
	private void sendMessage(String username, String message) {
		if(onlineUsers.contains(username)) {
			User receiver = usersInfo.get(username);
			String ip = receiver.ip;
			String PORT = receiver.PORT;
			Socket sendSoc;
			try {
				sendSoc = new Socket(ip, Integer.parseInt(PORT));
				DataOutputStream sendToClient = new DataOutputStream(sendSoc.getOutputStream());
				sendToClient.writeBytes(message+"\n");
				sendSoc.close();
			} catch (NumberFormatException | IOException e) {
				System.out.println("Exception in method 'sendMessage' in class ClientJob");
			}			
		}else {
			User u = usersInfo.get(username);
			synchronized (usersInfo) {
				u.mailbox.add(message);
			}			
		}			
	}

	/* display the offline message to the new login user */
	private void readOffMsg(String username) {
		ArrayList<String> mailbox = usersInfo.get(username).mailbox;
		if (!mailbox.isEmpty()) {
			StringBuffer buffer = new StringBuffer();
			buffer.append("Offline message: ");
			synchronized (mailbox) {
				Iterator<String> i = mailbox.iterator();
				while(i.hasNext()) {
					buffer.append(i.next()+"\t");
					i.remove();
				}
			}
			System.out.println(buffer.toString());
			System.out.println(usersInfo.get(username).mailbox.isEmpty());
			sendMessage(username,buffer.toString());
		}		
	}
	private static void usersInit() {
		try {
			String sdir = System.getProperty("user.dir");
			String sep = System.getProperty("file.separator");
			
			BufferedReader fromText = new BufferedReader(new FileReader(sdir + sep + "credentials.txt"));
			String l;
			while ((l = fromText.readLine()) != null) {
				String[] user = l.split("\t");			
				usersInfo.put(user[0],new User(user[0], user[1]));
			}
			fromText.close();
		} catch (IOException e) {
			System.out.println("Exception in method 'usersInit'");
		}				
	}

	
}
