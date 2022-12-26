package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

	/**
	 * The game environment object.
	 */
	private final Env env;

	/**
	 * Game entities.
	 */
	private final Table table;
	private final Player[] players;
	public long timer;
	/**
	 * The list of card ids that are left in the dealer's deck.
	 */
	private final List<Integer> deck;
	private TimerMode mode;

	/**
	 * True iff game should be terminated.
	 */
	enum TimerMode {
		Nothing, // don't display countdown timer
		lastAction, // display time since last action
		Shuffle // display time until shuffle
	}

	private volatile boolean terminate;
	protected Queue<Integer> toRemove; // stores all the slots that have cards that need to be removed
	protected Queue<Player> playerQ; // all players that have 3 tokens active
	public boolean keyLock = false;

	/**
	 * The time when the dealer needs to reshuffle the deck due to turn timeout.
	 */
	private long reshuffleTime = System.currentTimeMillis();

	public Dealer(Env env, Table table, Player[] players) {
		if (env.config.turnTimeoutMillis == 0)
			mode = TimerMode.lastAction;
		else if (env.config.turnTimeoutMillis < 0)
			mode = TimerMode.Nothing;
		else {
			mode = TimerMode.Shuffle;
		}
		this.playerQ = new ConcurrentLinkedQueue<>();
		this.toRemove = new ConcurrentLinkedQueue<>();
		this.terminate = false;
		this.env = env;
		this.table = table;
		this.players = players;
		deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
		timer = env.config.turnTimeoutMillis;
	}

	/**
	 * The dealer thread starts here (main loop for the dealer thread).
	 */
	@Override
	public void run() {
		Collections.shuffle(deck); // shuffle deck upon start
		env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
		int i = 0;
		for (Player p : players) {
			new Thread(p, "player " + i).start();
			i++;
		}
		while (!shouldFinish()) {
			placeCardsOnTable();
			timerLoop();
			updateTimerDisplay(false);
			removeAllCardsFromTable();
		}
		announceWinners();
		terminate();
		env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
	}

	/**
	 * The inner loop of the dealer thread that runs as long as the countdown did
	 * not time out.
	 */
	private void timerLoop() {
		while (!terminate && ((mode == TimerMode.Shuffle && System.currentTimeMillis() < reshuffleTime)
				|| (mode != TimerMode.Shuffle))) {
			sleepUntilWokenOrTimeout();
			updateTimerDisplay(false);
			removeCardsFromTable();
			placeCardsOnTable();
		}
	}

	/**
	 * Called when the game should be terminated.
	 */
	public void terminate() {
		for (int j = players.length - 1; j >= 0; j--) {
			Thread temp = players[j].playerThread;
			players[j].terminate();
			try {
				synchronized (players[j]) {
					players[j].notifyAll();
				}
				temp.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.terminate = true;
	}

	private int numberOfCards() {
		int sum = 0;
		for (int i = 0; i < table.slotToCard.length; i++) {
			if (table.slotToCard[i] != null)
				sum++;
		}
		return sum;
	}

	/**
	 * Check if the game should be terminated or the game end conditions are met.
	 *
	 * @return true iff the game should be finished.
	 */
	private boolean shouldFinish() {
		return terminate || (env.util.findSets(deck, 1).size() == 0);
	}

	/**
	 * Checks cards should be removed from the table and removes them.
	 */
	private void removeCardsFromTable() {
		synchronized (table) {
			List<Integer> trSlots = new ArrayList<>();
			while (!toRemove.isEmpty())
				trSlots.add(toRemove.remove());
			Collections.shuffle(trSlots);
			while (!trSlots.isEmpty())
				table.removeCard(trSlots.remove(0));
		}
	}

	/**
	 * Check if any cards can be removed from the deck and placed on the table.
	 */
	public void placeCardsOnTable() { // needs review
		List<Integer> l = new ArrayList<>();// need to take all the cards that left and check if there is one set, if
											// not terminate.
		for (int i = 0; i < table.slotToCard.length; i++) {
			if (table.slotToCard[i] != null)
				l.add(table.slotToCard[i]);
		}
		for (int i = 0; i < deck.size(); i++) {
			l.add(deck.get(i));
		}
		// List<int[]> sets = env.util.findSets(l, 1);
		if (env.util.findSets(l, 1).size() < 1) {
			terminate();
			return;
		}
		List<Integer> slots = new ArrayList<>();
		boolean flag = false; // signals whether timer requires reset after placing cards
		synchronized (table) {
			for (int i = 0; i < table.slotToCard.length; i++) {
				if (table.slotToCard[i] == null && !deck.isEmpty()) {
					slots.add(i);
					flag = true;
				}
			}
			Collections.shuffle(slots);
			while (!slots.isEmpty())
				table.placeCard(deck.remove(0), slots.remove(0));
			if (mode == TimerMode.Shuffle && flag)
				reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 500;
			if (mode != TimerMode.Shuffle) {
				List cards = new ArrayList<>();
				for (Integer i : table.slotToCard) {
					if (i != null)
						cards.add(i);
				}
				if (env.util.findSets(cards, 1).size() < 1) {
					removeAllCardsFromTable();
					placeCardsWithSet();
				}
				if(flag)
					this.reshuffleTime = System.currentTimeMillis();
			}
		}
	}

	public void placeCardsWithSet() { // assumes table is empty
		synchronized (table) {
			int amountOfCards = Math.min(env.config.rows * env.config.columns, deck.size());
			List<Integer> cardList = new ArrayList<>();
			for (int i = 0; i < amountOfCards; i++)
				cardList.add(deck.remove(0));
			if (env.util.findSets(cardList, 1).size() == 0) {
				placeCardsWithSet();
			} else {
				List<Integer> slots = new ArrayList<>();
				for(int i=0; i< amountOfCards; i++)
					slots.add(i);
				Collections.shuffle(slots);
				while(!slots.isEmpty())
					table.placeCard(cardList.remove(0), slots.remove(0));
			}
		}
	}

	/**
	 * Sleep for a fixed amount of time or until the thread is awakened for some
	 * purpose.
	 */
	private void sleepUntilWokenOrTimeout() {
		try {
			synchronized (playerQ) {
				playerQ.wait(10);
				if (!playerQ.isEmpty()) {
					synchronized (table) {
						while (!playerQ.isEmpty()) {
							Player p = playerQ.remove();
							int[] slots = { p.tokens.get(0), p.tokens.get(1), p.tokens.get(2) };
							int[] cards = { table.slotToCard[slots[0]], table.slotToCard[slots[1]],
									table.slotToCard[slots[2]] };
							if (env.util.testSet(cards)) { // if they form a set
								for (int i : cards) {
									this.toRemove.add(table.cardToSlot[i]);
								}
								p.toAward = true;
								p.point();
								for (Player p1 : players) {
									for (int j = p1.tokens.size() - 1; j >= 0; j--) {
										Integer i = p1.tokens.get(j);
										if (i.intValue() == slots[0]) {
											p1.tokens.remove(i);
											table.removeToken(p1.id, i);
										} else if (i.intValue() == slots[1]) {
											p1.tokens.remove(i);
											table.removeToken(p1.id, i);
										} else if (i.intValue() == slots[2]) {
											p1.tokens.remove(i);
											table.removeToken(p1.id, i);
										}
									}
								}
								if (mode == TimerMode.lastAction) {
									this.reshuffleTime = System.currentTimeMillis();
								}
							} else {
								p.toAward = false;
							}
							try {
								synchronized (p) {
									p.notifyAll();
								}
							} catch (Exception ignored) {
							}
						}
					}
				}
			}
		} catch (Exception e) {
		}
	}

	public boolean timerCanBeChange(long time) {
		if (timer - time >= 0 && time >= 0)
			return true;
		return false;
	}

	public long changeTime(long time) {
		if (timer - time >= 0 && time >= 0) {
			timer = timer - time;
			return (timer - time);
		}
		return -1;
	}

	/**
	 * Reset and/or update the countdown and the countdown display.
	 */
	public void updateTimerDisplay(boolean reset) {
		if (mode == TimerMode.Shuffle) {
			if (this.reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis)
				this.env.ui.setCountdown(this.reshuffleTime - System.currentTimeMillis(), reset);
			else if (this.reshuffleTime - System.currentTimeMillis() > 0) {
				this.env.ui.setCountdown(this.reshuffleTime - System.currentTimeMillis(), true);
			} else {
				this.env.ui.setCountdown(0, true);
			}
		} else if (mode == TimerMode.lastAction) {
			this.env.ui.setElapsed(System.currentTimeMillis() - this.reshuffleTime);
		}
	}

	/**
	 * Returns all the cards from the table to the deck.
	 */
	private void removeAllCardsFromTable() {
		synchronized (table) {
			for (Player p : players) {
				p.keyPress.clear();
				for (int j = p.tokens.size() - 1; j >= 0; j--) {
					table.removeToken(p.id, p.tokens.get(j));
					p.tokens.remove(j);
				}
			}
			List<Integer> trSlots = new ArrayList<>();

			for (int i = 0; i < table.slotToCard.length; i++) {
				if (this.table.slotToCard[i] != null) {
					this.deck.add(this.table.slotToCard[i]);
					trSlots.add(i);
				}
			}
			Collections.shuffle(trSlots);
			if (mode == TimerMode.lastAction)
				this.reshuffleTime = System.currentTimeMillis();
			while (!trSlots.isEmpty())
				table.removeCard(trSlots.remove(0));
			Collections.shuffle(deck);
		}
	}

	/**
	 * Check who is/are the winner/s and displays them.
	 */
	private void announceWinners() {
		int max = 0;
		int counter = 0;
		for (Player p : players) {
			if (p.score > max) {
				max = p.score;
			}
		}
		for (Player p : players) {
			if (p.score == max) {
				counter++;
			}
		}
		int[] winner = new int[counter];
		counter = 0;
		for (Player p : players) {
			if (p.score == max) {
				winner[counter] = p.id;
				counter++;
			}
		}
		env.ui.announceWinner(winner);
	}
}
