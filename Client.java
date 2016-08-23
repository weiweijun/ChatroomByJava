import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;



public class Client  {
	
	String ip;
	String port;
	String username;
	int PORT;	
	static HashMap<String, String> p2pInfo = new HashMap<String, String>();	/* to store ip and port info about other peers */
	String mFromUser;

	JTextArea incoming;	/* user interface part to read from user */
	JTextField outgoing;	/* user interface part to display to user */
	
	public Client (String i, String p) {
		ip = i;
		port = p;
	}
	public static void main(String[] args) throws Exception {
		Client client = new Client(args[0],args[1]);
		client.go();			
	}
	private void go() throws Exception {
		login();	/* handle login */
	
		ListenFromServer serverJob = new ListenFromServer();	
		Thread t = new Thread(serverJob);
		t.start();

		ui();	/* start user interface */

		/* gracefully exit using control+c */
		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
            	Socket clientSocket;
				try {
					clientSocket = new Socket(ip,Integer.parseInt(port));
					DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
					outToServer.writeBytes(username+"\t"+"logout\n");
	                System.out.println("Shutdown hook ran!");
				} catch (NumberFormatException | IOException e) {
					e.printStackTrace();
				}
            }
        });
	}

	/* listen from server or other peers, defining a class which implements Runnable, so this object can be passed to a thead to do his job */
	class ListenFromServer implements Runnable{
		public void run() {
			ServerSocket serverSocket;
			try {
				serverSocket = new ServerSocket(PORT);
				String mFromServer;
				while(true) {
					Socket listenSoc = serverSocket.accept();
					BufferedReader inFromServer = new BufferedReader(new InputStreamReader(listenSoc.getInputStream()));						
					mFromServer = inFromServer.readLine();
					System.out.println(mFromServer);
					incoming.append(mFromServer+"\n");
					if (mFromServer.equals("Time is up, You have logged out!"))	
						System.exit(0);
					else if (mFromServer.length() > 6 && mFromServer.substring(0,5).equals("Error"))
						System.exit(1);
					else {
						String[] m = mFromServer.split(" ");

						/* if server send the ip and port info of other peer, store it */
						if (m.length == 3 && m[0].equals("P2P")) {
							System.out.println("shenm");
							String uP2P = m[1];
							if (!p2pInfo.containsKey(uP2P)) {
								p2pInfo.put(uP2P,m[2]);
							}
						}
					}	
				}
			} catch (IOException e) {
				System.out.println("Exception error in method 'run' in class ListenFromServer");
			}			
		}
	}	

	private void login() throws Exception {
		
		String mFromServer = "";
		String mFromUser;
		System.out.print("Username:");
		

		/* non-persistent connection, first wait for user to input, then build a connection and send to server and get reply immediately, then close the socket */
		for(int i=0; i<4; i++) {
			BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
			mFromUser = inFromUser.readLine();

			Socket loginSocket = new Socket(ip,Integer.parseInt(port));		
			DataOutputStream outToServer = new DataOutputStream(loginSocket.getOutputStream());
			BufferedReader  inFromServer = new BufferedReader(new InputStreamReader(loginSocket.getInputStream()));

			/* whenever send a message to the server, prefix with username. */
			if (i == 0) {
				username = mFromUser;
				outToServer.writeBytes("0"+"\t"+username+"\n");
			}
			else	
				outToServer.writeBytes("0"+"\t"+username+"\t"+mFromUser+"\n");	/* prefix 0 stands for login operation */
			mFromServer = inFromServer.readLine();

			if (!mFromServer.equals("Password:")) {
				System.out.println(mFromServer);
			}
			if (mFromServer.equals("Finished login")) {
				PORT = (int )(Math.random() * 60536 + 5000);
				outToServer.writeBytes(PORT+"\n");
				loginSocket.close();
				break;
			}
			loginSocket.close();			
			if (mFromServer.length()>5 && mFromServer.substring(0,5).equals("Error")) {
				System.exit(1);
			}
			System.out.print("Password:");					
		}		 
	}

	/* user interface */
	private void ui() {
		JFrame frame = new JFrame("Simple Chat Client");
		JPanel mainPanel = new JPanel();		
		outgoing = new JTextField(20);
		incoming = new JTextArea(15,30);
		incoming.setLineWrap(true);
		incoming.setWrapStyleWord(true);
		incoming.setEditable(false);
		JLabel userLabel = new JLabel("Current user: "+username,JLabel.CENTER);
		userLabel.setSize(10,10);
		JScrollPane scroller = new JScrollPane(incoming);
		scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JButton button = new JButton("Send");
		button.addActionListener(new SendButtonListener());
		mainPanel.add(incoming);
		mainPanel.add(outgoing);
		mainPanel.add(button);
		frame.getContentPane().add(BorderLayout.CENTER,mainPanel);
		frame.getContentPane().add(BorderLayout.NORTH,userLabel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400,500);
		frame.setVisible(true);
	}

	/* listen from the user who press the button to send a message. defining a class which implements Runnable, just like the "ListenFromServer" */
	class SendButtonListener implements ActionListener {
		public void actionPerformed (ActionEvent event) {
			mFromUser = outgoing.getText();
			String[] m = mFromUser.split(" ");

			/* to decide whether to talk to a peer or the server  */
			if (m[0].equals("private")) {
				if (m.length < 3) {
					System.out.println("invalid commad!");
					incoming.append("invalid commad!");
					return;
				} else if (m.length > 3)
					m = mFromUser.split(" ",3);
				if (!p2pInfo.containsKey(m[1])) {
					System.out.println("No information about <"+m[1]+">, cannot communicate privately!");
					incoming.append("No information about <"+m[1]+">, cannot communicate privately!");
				}
				else {
					String[] info = p2pInfo.get(m[1]).split("\t");
					Socket p2pSocket;
					try {
						p2pSocket = new Socket(info[0],Integer.parseInt(info[1]));
						DataOutputStream outToPeer = new DataOutputStream(p2pSocket.getOutputStream());
						outToPeer.writeBytes("<"+username+">[private]:"+m[2]+"\n");	
						p2pSocket.close();
					} catch (NumberFormatException | IOException e) {
						e.printStackTrace();
					}
					
					System.out.println("sent to peer "+m[1]+":"+m[2]);
				}			
			}
			else {
				Socket clientSocket;
				try {
					clientSocket = new Socket(ip,Integer.parseInt(port));
					DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
					outToServer.writeBytes(username+"\t"+mFromUser+"\n");
					System.out.println("sent to server: "+mFromUser);
					String mFromServer = null;
					/* 
					*non-persistent connection. user send message and immediately close the socket. 
					*because these commands in the following need server to send message back imeediately, so I choose to first get the reply from the server, then close the socket 
					*/
					if (m[0].equals("online") || m[0].equals("getaddress") || m[0].equals("block") || m[0].equals("unblock")) {
						BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
						mFromServer = inFromServer.readLine();
						incoming.append(mFromServer+"\n");
						System.out.println(mFromServer);
					} 
					clientSocket.close();
				} catch (NumberFormatException | IOException e) {
					e.printStackTrace();
				}				
			}
			if (mFromUser.equals("logout"))	
				System.exit(0);	
			outgoing.setText("");
			outgoing.requestFocus();	
		}
	}

}
