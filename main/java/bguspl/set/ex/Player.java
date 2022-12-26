package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

	/**
	 * The game environment object.
	 */
	private final Env env;

	/**
	 * Game entities.
	 */
	private final Table table;

	/**
	 * The id of the player (starting from 0).
	 */
	public final int id;

	/**
	 * The thread representing the current player.
	 */
	protected Thread playerThread;

	/**
	 * The thread of the AI (computer) player (an additional thread used to generate key presses).
	 */
	private Thread aiThread;

	/**
	 * True iff the player is human (not a computer player).
	 */
	private final boolean human;

	/**
	 * True iff game should be terminated.
	 */
	private volatile boolean terminate;

	/**
	 * The current score of the player.
	 */
	protected int score;

	protected Queue<Integer> keyPress;
	protected List<Integer> tokens;

	private boolean gracePenalty = false;
	private boolean freeze; // indicates whether the player is frozen

	private Dealer dealer;
	protected boolean toAward = false;
	/**
	 * The class constructor.
	 *
	 * @param env    - the environment object.
	 * @param dealer - the dealer object.
	 * @param table  - the table object.
	 * @param id     - the id of the player.
	 * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
	 */
	public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
		this.tokens = new LinkedList<>();
		this.terminate = false;
		this.dealer = dealer;
		this.freeze = false;
		this.keyPress = new ConcurrentLinkedQueue<>();
		this.score = 0;
		this.env = env;
		this.table = table;
		this.id = id;
		this.human = human;
	}

	/**
	 * The main player thread of each player starts here (main loop for the player thread).
	 */
	@Override
	public void run() {
		playerThread = Thread.currentThread();
		env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
		if (!human) createArtificialIntelligence();

		while (!terminate) { // check if necessary
			while(!keyPress.isEmpty()) {
				try {synchronized(table) {
					int slot = -1;
					synchronized(keyPress) {
						if(!keyPress.isEmpty()) {
							slot = keyPress.remove();
						}
					}
					if(slot != -1 && table.slotToCard[slot] == null)
						slot = -1;
					if(slot == -1) {
						break;
					}
					if(tokens.contains(slot)) {
						tokens.remove((Integer) slot);
						table.removeToken(id, slot);
					}
					else if(tokens.size() < env.config.featureSize) {
						tokens.add(slot);
						table.placeToken(id, slot);
						gracePenalty = false;
					}
				}
				if(tokens.size() == env.config.featureSize) {
					dealer.playerQ.add(this);
					synchronized(dealer.playerQ) {
						dealer.playerQ.notify();
					}
					synchronized(this) {
						this.wait(100);
					}
					if(toAward)
						penaltyForSet();						
					else {
						penalty();
					}
					keyPress.clear();
				}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				synchronized(this) {
					this.notifyAll();
				}
			}
			if(!terminate){
			synchronized(this) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					this.notify();
				}
			}}         	           
		}
		if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
				env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
	}

	/**
	 * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
	 * key presses. If the queue of key presses is full, the thread waits until it is not full.
	 */
	private void createArtificialIntelligence() {
		// note: this is a very, very smart AI (!)
		aiThread = new Thread(() -> {
			env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			while (!terminate) {
				while(keyPress.size() < env.config.featureSize) {
					synchronized(keyPress) {


						Random rand = new Random();
						int slot = rand.nextInt(env.config.rows * env.config.columns);
						if(keyPress.contains(slot)) {
							keyPress.remove(slot);
							try {
								synchronized(this) {
									this.notifyAll();
								}
							}catch(Exception ignored) {	
							}
						}
						else if(keyPress.size() < env.config.featureSize){
							keyPress.add(slot);
							try {
								synchronized(this) {
									this.notifyAll();
								}
							}catch(Exception ignored) {				
							}
						}
					}
				}
				synchronized(this) {
					this.notifyAll();
				}
				try {
					synchronized (this) { wait(10); }
				} catch (InterruptedException ignored) {}
			}
			env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
		}, "computer-" + id);
		aiThread.start();
	}

	/**
	 * Called when the game should be terminated.
	 */
	public void terminate() {
		terminate = true;
	}

	/**
	 * This method is called when a key is pressed.
	 *
	 * @param slot - the slot corresponding to the key pressed.
	 */
	public void keyPressed(Integer slot) {
        if(slot>=12 || slot<0) return;
		if(!freeze && keyPress.size() < 3) {
			if(keyPress.contains(slot)) {
				keyPress.remove(slot);
				try {
					synchronized(this) {
						this.notifyAll();
					}
				}catch(Exception ignored) {	
				}
			}
			else if(keyPress.size() < 3){
				keyPress.add(slot);
				try {
					synchronized(this) {
						this.notifyAll();
					}
				}catch(Exception ignored) {				
				}
			}
		}
	}

	/**
	 * Award a point to a player and perform other related actions.
	 *
	 * @post - the player's score is increased by 1.
	 * @post - the player's score is updated in the ui.
	 */
	public void point() {	
		score++;
		env.ui.setScore(id, score);
	}

	public void penaltyForSet() {	
		try {
			long i = env.config.pointFreezeMillis;
			freeze = true;
			while(i>600) {
				env.ui.setFreeze(this.id, i);
				i=i-500;
				Thread.sleep(500);
			}
			env.ui.setFreeze(this.id, 0);
			freeze = false;
			toAward = false;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int ignored = table.countCards(); // this part is just for demonstration in the unit tests
	}

	/**
	 * Penalize a player and perform other related actions.
	 */
	public void penalty() {
		if(!gracePenalty) {
			try {
				long i = env.config.penaltyFreezeMillis;
				freeze = true;
				while(i>600) {
					env.ui.setFreeze(this.id, i);
					i=i-500;
					Thread.sleep(500);
				}
				env.ui.setFreeze(this.id, 0);
				freeze = false;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			gracePenalty = true;
		}
	}

	public int score() {
		return score;
	}
}
