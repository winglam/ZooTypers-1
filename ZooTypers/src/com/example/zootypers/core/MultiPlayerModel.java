package com.example.zootypers.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.zootypers.util.EmptyQueueException;
import com.example.zootypers.util.InternalErrorException;
import com.example.zootypers.util.InternetConnectionException;
import com.example.zootypers.util.States;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.RefreshCallback;

/** 
 * 
 * The Model class for Single Player store a list of words for the UI to display.
 * It keeps track of word and letter the user has typed and updates the view accordingly.
 * 
 * @author winglam, nhlien93, dyxliang
 * 
 */

public class MultiPlayerModel extends PlayerModel {

	private static final int QUEUE_TIMEOUT = 15000; // timer set for 15 sec to wait before giving up in queue
	private static final int SCORE_TIMEOUT = 50000; // timer set to 5 sec to wait for getting opponents score
	private static final int RECHECK_TIME = 500; // timer set to 1/2 sec to wait between checks
	
	private static final int LIST_SIZE = 100;
	private static final int NUMOFWORDS = 709; // maximum number of words in wordLists on Parse database

	private String name;

	private ParseObject match;

	private Map<String, String> info;

	private int animalName;


	/**
	 * Constructs a new SinglePlayerModel that takes in the ID of an animal and background,
	 * and also what the difficulty level is. The constructor will initialize the words list
	 * and fills in what words the view should display on the screen.
	 * 
	 * @param animalID, the string ID of a animal that is selected by the user
	 * @param backgroudID, the string ID of a background that is selected by the user
	 * @param diff, the difficulty level that is selected by the user
	 * @throws ParseException 
	 */
	public MultiPlayerModel(int wordsDis, String uname, int animalName) {
		super(wordsDis);
		this.animalName = animalName;
		this.name = uname;
		this.info = new HashMap<String, String>();
	}

	public void beginMatchMaking() throws InternetConnectionException, EmptyQueueException, InternalErrorException {
		if (findOpponent()) {
			setInfo(false);
			try {
				match.put("p2name", name);
				match.put("p2animal", animalName);
				match.put("p2score", 0);
				match.put("p2finished", false);
				match.save();
			} catch (ParseException e) {
				throw new InternetConnectionException();
			}
		} else {
			addToQueue();
			if (!checkStatus()) {
				throw new EmptyQueueException();
			}
		}
	}

	// checks whether name can be matched to an opponent
	// returns true if matched, false otherwise
	private boolean findOpponent() {
		try {
			ParseQuery query = new ParseQuery("Matches");
			query.whereEqualTo("p2name", "");
			query.whereNotEqualTo("p1name", name);
			match = query.getFirst();
			return true;
		} catch (ParseException e1) {
			return false;
		}
	}

	private void setInfo(boolean isPOne) {
		if (isPOne) {
			info.put("name", "p1name");
			info.put("animal", "p1animal");
			info.put("score", "p1score");
			info.put("finished", "p1finished");
			info.put("oname", "p2name");
			info.put("oanimal", "p2animal");
			info.put("oscore", "p2score");
			info.put("ofinished", "p2finished");
		} else {
			info.put("name", "p2name");
			info.put("animal", "p2animal");
			info.put("score", "p2score");
			info.put("finished", "p2finished");
			info.put("oname", "p1name");
			info.put("oanimal", "p1animal");
			info.put("oscore", "p1score");
			info.put("ofinished", "p1finished");
		}
	}

	private void addToQueue() throws InternetConnectionException {
		final int randy = (int) (Math.random() * (NUMOFWORDS));
		try {
			setInfo(true);
			match = new ParseObject("Matches");
			match.put("p1name", name);
			match.put("p1animal", animalName);
			match.put("p1score", 0);
			match.put("p1finished", false);
			match.put("p2name", "");
			match.put("wordIndex", randy);
			match.save();
		} catch (ParseException e) {
			throw new InternetConnectionException();
		}
	}

	private boolean checkStatus() throws InternetConnectionException, InternalErrorException {
		long starttime = System.currentTimeMillis();
		long endtime = starttime + QUEUE_TIMEOUT;
		while(System.currentTimeMillis() < endtime) {
			try {
				match.refresh();  	
				checkIfInMatch();
				if (!match.getString(info.get("oname")).equals("")) {
					return true;
				}
				Thread.sleep(RECHECK_TIME);
			} catch (ParseException e1) {
				throw new InternetConnectionException();
			} catch (InterruptedException e) {
				throw new InternalErrorException();
			}
		}
		return false;
	}

	// populates wordsList by contacting the database for LIST_SIZE amount of words
	public void setWordsList() throws InternetConnectionException {
		List<ParseObject> wordObjects = null;
		try {
			checkIfInMatch();
			ParseQuery query = new ParseQuery("WordList");
			query.setSkip(match.getInt("wordIndex"));
			query.setLimit(LIST_SIZE); // limit to at most 20 results
			wordObjects= query.find();
			if (wordObjects.size() < LIST_SIZE) {
				ParseQuery query2 = new ParseQuery("WordList");
				query2.setLimit(LIST_SIZE - wordObjects.size());
				wordObjects.addAll(query2.find());
			}
		} catch (ParseException e1) {
			throw new InternetConnectionException();
		}	
		// changing words from parse objects into a list of strings.
		wordsList = new ArrayList<String>();
		for (ParseObject o : wordObjects) {
			wordsList.add(o.getString("word"));
		}
	}

	// checks if match online still has the same player in it.
	private void checkIfInMatch() throws InternetConnectionException {
		if (!match.getString(info.get("name")).equals(name)) {
			throw new InternetConnectionException();
		}
	}

	public int getOpponentAnimal() throws InternetConnectionException {
		try {
			match.refresh();
			checkIfInMatch();
		} catch (ParseException e) {
			throw new InternetConnectionException();
		}
		return match.getInt(info.get("oanimal"));
	}

	/**
	 * The typedLetter method handles what words and letter the user has
	 * typed so far and notify the view to highlight typed letter or fetch 
	 * a new word from the wordsList for the view to display accordingly.
	 * 
	 * @param letter, the letter that the user typed on the Android soft-keyboard
	 */
	public final void typedLetter(final char letter) {
		// currently not locked on to a word
		if (currWordIndex == -1) {
			for (int i = 0; i < wordsDisplayed.length; i++) {
				// if any of the first character in wordsDisplayed matched letter
				if (wordsList.get(wordsDisplayed[i]).charAt(0) == letter) {
					currWordIndex = i;
					currLetterIndex = 1;
					setChanged();
					notifyObservers(States.update.HIGHLIGHT);
					return;
				}
			}
			// locked on to a word being typed (letter == the index of current letter index in the word)
		} else if (wordsList.get(wordsDisplayed[currWordIndex]).charAt(currLetterIndex) == letter) {

			// store length of current word
			int wordLen = wordsList.get(wordsDisplayed[currWordIndex]).trim().length();

			// word is completed after final letter is typed
			if ((currLetterIndex + 1) >= wordLen) {
				int curScore = match.getInt(info.get("score"));
				curScore += wordLen;
				match.put(info.get("score"), curScore);
				match.saveInBackground();
				updateWordsDisplayed();
				currLetterIndex = -1;
				currWordIndex = -1;
			} else {
				currLetterIndex += 1;
				setChanged();
				notifyObservers(States.update.HIGHLIGHT);
			}
			return;
		}

		// wrong letter typed
		setChanged();
		notifyObservers(States.update.WRONG_LETTER);
	}

	public final void setUserFinish() throws InternetConnectionException {
		try {
			match.put(info.get("finished"), true);
			match.save();
		} catch (ParseException e) {
			throw new InternetConnectionException();
		}
	}

	// return true if my opponent has finished their game
	public final boolean isOpponentFinished() throws InternetConnectionException, InternalErrorException {
		long starttime = System.currentTimeMillis();
		long endtime = starttime + SCORE_TIMEOUT;
		while(System.currentTimeMillis() < endtime) {

			if (match.getBoolean(info.get("ofinished"))) {
				return true;
			}
			try {
				match.refresh();
				checkIfInMatch();
				Thread.sleep(RECHECK_TIME);
			} catch (ParseException e1) {
				throw new InternetConnectionException();
			} catch (InterruptedException e) {
				throw new InternalErrorException();
			}
		}
		return false;
	}

	/**
	 * refreshes the match then tries to delete themselves from the queue
	 * if the other player has not finished then just set their own finished field
	 * in the match to true.
	 * @throws InternetConnectionException 
	 * 
	 * @modifies this
	 */
	public void deleteUser() {
		try {
			if (info.get("name").equals("p1name"))
				match.delete();
		} catch (ParseException e) {
			// shouldn't need to worry about this since game is ending anyway
		}
	}

	/**
	 * refreshes the match in a background thread and notifies the
	 * UI to update the opponent score after the refresh is done.
	 * 
	 * @modifies this
	 */
	public void refreshInBackground() {
		match.refreshInBackground(new RefreshCallback() {
			public void done(ParseObject object, ParseException e) {
				if (e == null) {
					setChanged();
					notifyObservers(States.update.OPPONENT_SCORE);
				}
			}
		});
	}

	/**
	 * @return current score of the player
	 */
	public final int getScore() {
		return match.getInt(info.get("score"));
	}

	public final int getOpponentScore() {
		return match.getInt(info.get("oscore"));
	}
}