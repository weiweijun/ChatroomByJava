import java.util.ArrayList;

public class User {
	String username;
	String password;
	String ip = null;
	String PORT = null;
	int loginState = 0;		/* used to handle login */
	long blockTime = 0;
	int onlineElapsed = 0;
	int LIVE_TIME = 120;	/* 30s */
	ArrayList<String> blacklist = new ArrayList<String>();
	ArrayList<String> mailbox = new ArrayList<String>();

	public User (String u, String p) {
		username = u;
		password = p;
	}
}