import javax.swing.*;
import java.awt.*;
import javax.swing.border.*;
import java.awt.event.*;
import java.lang.Comparable;
import java.util.Arrays;
/*
CST 338 - Assignment 6 Phase 3
Software Design Team
Lilia Chakarian, William Gillihan, and Christina Hunter
Build a new card game.
*/
public class Assign63
{
   static Controller controller;
   static Model model;
   static View view; 
   /*
   simple main to run game in
   */
   public static void main(String[] args)
   {
      controller = new Controller();
      view = new View("Card \"BUILD\" Game", Model.NUM_PLAYERS,
         Model.NUM_CARDS_PER_HAND);
      @SuppressWarnings("unused")
      Model model = new Model(controller, view);
      Model.startGame();
   }
   /*
   Model Class
   */
   private static class Model
   {
      static CardGameFramework cardBuildGame;
      static Thread timerThread;
      static boolean isClockRunning = false;
      static View view;
      static Controller controller;
      static int numPacksPerDeck = 1;
      static int numJokersPerPack = 0;
      static int numUnusedCardsPerPack = 0;
      static Card[] unusedCardsPerPack = null;
      private static final int COMP_PLAYER = 0;
      private static final int CARD_PLAYER = 1;
      private static int minutes = 0, seconds = 0;
      public static final int NUM_STACKS = 2;
      private static BuildDeck[] buildDecks = new BuildDeck[NUM_STACKS];
      private static int selectedCard = -1;
      private static int consecutiveTurnSkips = 0;
      private static boolean newCardTopsPlacedLastRound = false;
      public static final int NUM_CARDS_PER_HAND = 7;
      public static final int NUM_PLAYERS = 2;
      static Card[][] playerWinnings = 
         new Card[NUM_PLAYERS][NUM_CARDS_PER_HAND * 2];
      private static int[] noPlayCount = new int[NUM_PLAYERS];
      static final String START_CLOCK = "Start Clock"; 
      static final String STOP_CLOCK = "Stop Clock";
      static final String RESET_CLOCK = "Reset Clock"; 
      static final String CANNOT_PLAY = "I Can't Play.";
      public static String clockStr;
      /*
      Description: overload constructor for Model class
      Parameters: Controller class object. 
      Parameters: View class object.
      */
      public Model(Controller controller, View view)
      {
         Model.controller = controller;
         Model.view = view;
         Model.cardBuildGame = new CardGameFramework(Model.numPacksPerDeck,
            Model.numJokersPerPack, Model.numUnusedCardsPerPack,
            Model.unusedCardsPerPack, Model.NUM_PLAYERS,
            Model.NUM_CARDS_PER_HAND);
      }
      /*
      Runnable timer thread
      */
      static Runnable timer = new Runnable()
      {
         public void run()
         {
            while (isClockRunning)
            {
               clockStr = "";
               seconds++;
               if (seconds == 60)
               {
                  seconds = 0;
                  minutes++;
               }
               if (minutes < 10)
               {
                  clockStr = "0" + minutes + ":";
               }
               else
               {
                  clockStr = minutes + ":";
               }
               if (seconds < 10)
               {
                  clockStr += "0";
               }
               clockStr += seconds;
               View.updateClock(clockStr);
               try
               {
                  Thread.sleep(1000);
               }
               catch (Exception e)
               {
                  // catch and do nothing
               }
            }
         }
      };
      /*
      Name: resetTimer
      Description: resets minutes to 0 and seconds are
      set to -1 to adjust for no-delay start after reset.
      */
      public static void resetTimer()
      {
         seconds = -1;
         minutes = 0;
      }
      /*
      Name: getValidPlayValues
      Description: gets an character array of two 
      valid card values that can be played on a card
      Parameter: Card card that is being checked for valid play values.
      Returns: char array containing two values that are valid to play.
      */
      private static char[] getValidPlayValues(Card card)
      {
         String cardVals = new String(Card.valuRanks);
         char[] validVals = new char[2];
         // end cards Ace and Joker are treated different
         if (card.getValue() == cardVals.charAt(0))
         {
            validVals[0] = cardVals.charAt(cardVals.length() - 1);
            validVals[1] = cardVals.charAt(1);
         }
         else if (card.getValue() == cardVals.charAt(cardVals.length() - 1))
         {
            validVals[0] = cardVals.charAt(cardVals.length() - 2);
            validVals[1] = cardVals.charAt(0);
         }
         else
         {
            validVals[0] = cardVals.charAt(cardVals
               .indexOf(card.getValue()) - 1);
            validVals[1] = cardVals.charAt(cardVals
               .indexOf(card.getValue()) + 1);
         }
         return validVals;
      }
      /*
      Name: playComputerCard
      Description: used to play computer's card
      */
      private static void playComputerCard()
      {
         // try to play the computer's card
         int[] computerPlay = getPossiblePlay(COMP_PLAYER);
         if (computerPlay[0] != -1)
         {
            int computerDeck = computerPlay[0];
            int computerCardPosition = computerPlay[1];
            Card computerCard = cardBuildGame.getHand(COMP_PLAYER)
               .inspectCard(computerCardPosition);
            cardBuildGame.playCard(COMP_PLAYER, computerCardPosition);
            cardBuildGame.takeCard(COMP_PLAYER, NUM_CARDS_PER_HAND - 1);
            view.dealHand(COMP_PLAYER, cardBuildGame.getHand(COMP_PLAYER), false);
            view.setPlayedCard(computerDeck, computerCard);
            buildDecks[computerDeck].addCard(computerCard);
            view.setStatusTxt("Computer played " + computerCard +
               " on Build Deck " + (computerDeck + 1) + ".");
            consecutiveTurnSkips = 0;
         }
         else
         {
            // if computer does not have a card that it can play, skip it's turn
            view.setStatusTxt("Computer could not play a card.");
            noPlayCount[COMP_PLAYER]++;
            View.updateNoPlayLabel(noPlayCount[COMP_PLAYER],
               noPlayCount[CARD_PLAYER]);
            consecutiveTurnSkips++;
            if (consecutiveTurnSkips >= 2)
            {
               if (!newBuildDeckFace())
               {
                  return;
               }
               consecutiveTurnSkips = 0;
            }
         }
         // let player know if new cards have been placed on Build Decks
         if (newCardTopsPlacedLastRound)
         {
            view.setStatusTxt(view.getStatusTxt() + " A new card has been "
               + "placed on each Build Deck.");
            newCardTopsPlacedLastRound = false;
         }
         if (cardBuildGame.getHand(COMP_PLAYER).getNumCards() == 0)
         {
            gameOver();
         }
         view.setStatusTxt(view.getStatusTxt() + " Your turn!");
      }
      /*
      Name: placeCard
      Parameter: int stack card is placed on.
      Returns: true if the card was placed successfully.
      */
      public static boolean placeCard(int stack)
      {
         Card playerCard = cardBuildGame.getHand(CARD_PLAYER)
            .inspectCard(selectedCard);
         char[] validVals = getValidPlayValues(buildDecks[stack]
            .inspectTopCard());
         for (char value : validVals)
         {
            if (value == playerCard.getValue())
            {
               consecutiveTurnSkips = 0;
               buildDecks[stack].addCard(playerCard);
               cardBuildGame.playCard(CARD_PLAYER, selectedCard);
               selectedCard = -1;
               cardBuildGame.takeCard(CARD_PLAYER, NUM_CARDS_PER_HAND - 1);
               cardBuildGame.getHand(CARD_PLAYER).sort();
               view.dealHand(CARD_PLAYER, cardBuildGame
                  .getHand(CARD_PLAYER), true);
               view.setPlayedCard(stack, playerCard);
               if (cardBuildGame.getHand(CARD_PLAYER).getNumCards() == 0)
               {
                  gameOver();
                  return true;
               }
               
               playComputerCard();
               return true;
            }
         }
         view.setStatusTxt("Cannot play card there. Try again!");
         return false;
      }
      /*
      Name: whoWins
      Description: checks who had the least amount of skipped turns
      and returns the index number of that player, or -1 if a tie
      Returns: int value of player who won
      */
      private static int whoWins()
      {
         if (noPlayCount[COMP_PLAYER] == noPlayCount[CARD_PLAYER])
         {
            return -1;
         }
         else if (noPlayCount[COMP_PLAYER] > noPlayCount[CARD_PLAYER])
         {
            return 1;
         }
         else
         {
            return 0;
         }
      }
      /*
      Name: skipPlayerTurn
      Description: checks to see if player has a valid card to play,
      if there is a card to play it will notify player, if not,
      it increments player's number of skipped turns 
      */
      private static void skipPlayerTurn()
      {
         int play[] = getPossiblePlay(CARD_PLAYER);
         if (play[0] != -1)
         {
            view.setStatusTxt("You can play one of your cards on Build Deck "
               + (play[0] + 1) + ".");
            return;
         }
         boolean playComputerCard = true;
         consecutiveTurnSkips++;
         noPlayCount[CARD_PLAYER]++;
         view.setStatusTxt("");
         if (consecutiveTurnSkips == 2)
         {
            if (!newBuildDeckFace())
            {
               playComputerCard = false;
            }
            consecutiveTurnSkips = 0;
         }
         if(playComputerCard)
         {
            playComputerCard();
         }
         View.updateNoPlayLabel(noPlayCount[COMP_PLAYER],
            noPlayCount[CARD_PLAYER]);
      }
      /*
      Name: initGame
      Description: initializes game.
      */
      private static void initGame()
      {
         cardBuildGame.newGame();
         cardBuildGame.deal();
         cardBuildGame.getHand(CARD_PLAYER).sort();
         for (int i = 0; i < playerWinnings[0].length; i++)
         {
            playerWinnings[COMP_PLAYER][i] = null;
            playerWinnings[CARD_PLAYER][i] = null;
         }
         view.dealHand(COMP_PLAYER, cardBuildGame.getHand(COMP_PLAYER), false);
         view.dealHand(CARD_PLAYER, cardBuildGame.getHand(CARD_PLAYER), true);
         view.setPlayLabelText(0, "Build Deck 1");
         view.setPlayLabelText(1, "Build Deck 2");
         View.updateNoPlayLabel(0, 0);
         view.setStatusTxt("Select a card in your hand, then select a "
            + "Build Deck you would like to play it on.");
         buildDecks[0] = new BuildDeck(Deck.MAX_CARDS);
         buildDecks[1] = new BuildDeck(Deck.MAX_CARDS);
         buildDecks[0].addCard(cardBuildGame.getCardFromDeck());
         buildDecks[1].addCard(cardBuildGame.getCardFromDeck());
         view.setPlayedCard(0, buildDecks[0].inspectTopCard());
         view.setPlayedCard(1, buildDecks[1].inspectTopCard());
         View.removeStatusListener();
         minutes = 0;
         seconds = 0;
         View.updateClock("00:00");
         noPlayCount[COMP_PLAYER] = 0;
         noPlayCount[CARD_PLAYER] = 0;
         timerThread = new Thread(timer);
         View.addButtonListener();
         consecutiveTurnSkips = 0;
      }
      /*
      Name: startGame
      Description: initializes the card table,
      and then makes it visible.
      */
      private static void startGame()
      {
         initGame();
         view.showCardTable();
      }
      /*
      Name: getPossiblePlay
      Description: checks each build Deck, and determines if
      there is a card that can be played on either of the decks.
      Parameters: int index value of player.
      Returns: if there is a possible play it int array containing index
      of the playable Build Deck and index of playable card in players hand.
      otherwise it returns int array containing two -1's.
      */
      static int[] getPossiblePlay(int player)
      {
         for (int i = 0; i < buildDecks.length; i++)
         {
            char[] validVals = getValidPlayValues(buildDecks[i].inspectTopCard());           
            for (int x = 0; x < cardBuildGame.getHand(player).getNumCards(); x++)
            {
               Card card = cardBuildGame.getHand(player).inspectCard(x);
               for (char value : validVals)
               {
                  if (card.getValue() == value)
                  {
                     return new int[] {i, x};
                  }
               }
            }
         }
         return new int[] {-1, -1};
      }
      /*
      Name: newBuildDeckFace
      Description: adds fresh card to each Build Deck
      Returns: boolean true if enough cards in deck.
      */
      private static boolean newBuildDeckFace()
      {
         int errorFlagCount = 0;
         for (int i = 0; i < NUM_STACKS; i++)
         {
            Card card = cardBuildGame.getCardFromDeck();
            if (!card.errorFlag)
            {
               buildDecks[i].addCard(card);
               view.setPlayedCard(i, card);
            }
            else
            {
               errorFlagCount++;
            }
         }      
         if (errorFlagCount == NUM_STACKS)
         {
            gameOver();
            return false;
         }
         else
         {
            newCardTopsPlacedLastRound = true;
         }        
         return true;
      }
      /*
      Name: gameOver
      Description: ends the game by stopping clock if running,
      reporting who the winner is, removing listener from clock buttons, and
      adding listener to status text area to allow the player to play again
      */
      private static void gameOver()
      {
         isClockRunning = false;
         int winner = whoWins();
         if (winner == CARD_PLAYER)
            view.setStatusTxt(view.getStatusTxt() + "You Win!!!"
               + " Click here to start a new game!");
         else if (winner == 0)
            view.setStatusTxt(view.getStatusTxt() + "Computer Won!"
               + " Click here to start a new game!");
         else
            view.setStatusTxt(view.getStatusTxt() + "Tie Game."
               + " Click here to start a new game!");
         View.removeButtonListener();
         View.addStatusListener();
      }
   }
   /*
   View Class
   */
   private static class View
   {
      static JLabel[][] playerHands;
      static JLabel[] playedCardLabels;
      static JLabel[] playLabelText;
      static JLabel timerLbl = new JLabel("0:00");
      static JLabel noPlayLabel = new JLabel();
      static JPanel[] playedCardPanels = new JPanel[Model.NUM_STACKS];
      static JButton cantPlayBtn = new JButton(Model.CANNOT_PLAY);
      static JButton clockStartBtn = new JButton(Model.START_CLOCK);
      static JButton clockStopBtn = new JButton(Model.STOP_CLOCK);
      private static final Color SLCT_COLOR = new Color(255, 0, 0);
      private static final Color RED_LGT = new Color(255, 150, 150);
      private static final Color GREEN_LGT = new Color(125, 255, 150);
      private static final Color BLUE_LGT = new Color(125, 150, 255);
      private static final Color GREEN_BTN = new Color(100, 255, 100);
      private static final Color RED_BTN = new Color(255, 100, 100);
      static JLabel statusTxt = new JLabel("");
      static CardTable cardTable;
      /*
      Description: overloaded constructor for View class
      Parameters: String title of card table
      Parameters: int number of players
      Parameters: int number of cards per hand
      */
      public View(String title, int numPlayers, int numCardsPerHand)
      {
         View.cardTable = new CardTable(title, numCardsPerHand, numPlayers);
         View.playerHands = new JLabel[numPlayers][numCardsPerHand];
         View.playLabelText = new JLabel[numPlayers];
         // played label text ------------------------------------------------
         for (int i = 0; i < numPlayers; i++)
         {
            playLabelText[i] = new JLabel();
            playLabelText[i].setHorizontalAlignment(JLabel.CENTER);
            playLabelText[i].setVerticalAlignment(JLabel.TOP);
            cardTable.pnlPlayerMsg.add(playLabelText[i]);
         }
         // played card panel ------------------------------------------------
         playedCardPanels[0] = new JPanel();
         playedCardPanels[0].setBackground(GREEN_LGT);
         playedCardPanels[1] = new JPanel();
         playedCardPanels[1].setBackground(GREEN_LGT);
         FlowLayout flow = new FlowLayout(FlowLayout.CENTER);
         playedCardPanels[0].setLayout(flow);
         playedCardPanels[1].setLayout(flow);
         cardTable.pnlPlayedCards.add(playedCardPanels[0]);
         cardTable.pnlPlayedCards.add(playedCardPanels[1]);
         playedCardLabels = new JLabel[Model.NUM_PLAYERS];
         for (int i = 0; i < playedCardLabels.length; i++)
         {
            playedCardLabels[i] = new JLabel();
            playedCardLabels[i].setBackground(GREEN_LGT);
            playedCardLabels[i].setHorizontalAlignment(JLabel.CENTER);
            playedCardLabels[i].addMouseListener(Assign63.controller);
            playedCardPanels[i].add(playedCardLabels[i]);
         }
         // score area -------------------------------------------------------
         noPlayLabel.setHorizontalAlignment(JLabel.LEFT);
         noPlayLabel.setVerticalAlignment(JLabel.BOTTOM);
         cardTable.pnlNoPlays.add(noPlayLabel);
         cardTable.pnlNoPlays.setBackground(RED_LGT);
         // status area ------------------------------------------------------
         statusTxt.setHorizontalAlignment(JLabel.CENTER);
         cantPlayBtn.setHorizontalAlignment(JButton.CENTER);
         cantPlayBtn.setFocusPainted(false);
         cardTable.pnlStatMsg.setBackground(BLUE_LGT);
         cardTable.pnlStatMsg.add(statusTxt);
         cardTable.pnlStatMsg.add(cantPlayBtn);
         // clock area -------------------------------------------------------
         timerLbl.setVerticalAlignment(JLabel.CENTER);
         timerLbl.setHorizontalAlignment(JLabel.CENTER);
         timerLbl.setText(Model.clockStr);
         // create new Font for clock
         Font font = new Font("SansSerif", Font.BOLD, 84);
         timerLbl.setFont(font);
         cardTable.pnlTimerLbl.add(timerLbl);
         clockStartBtn.setBackground(GREEN_BTN);
         clockStartBtn.setBorderPainted(true);
         clockStartBtn.setOpaque(true);
         cardTable.pnlTimerSbBtn.add(clockStartBtn);
         clockStopBtn.setBackground(RED_BTN);
         clockStopBtn.setBorderPainted(true);
         clockStopBtn.setOpaque(true);
         cardTable.pnlTimerSbBtn.add(clockStopBtn);
         cardTable.pnlTimer.add(timerLbl);
      }
      /*
      Name: updateNoPlayLabel
      Description: updates the score board with the number of skipped plays.
      Parameters: int computer's score.
      Parameters: int player's score.
      */
      private static void updateNoPlayLabel(int computerScore, int playerScore)
      {
         noPlayLabel.setText("<html><strong><u>&nbsp;&nbsp;Plays Skipped"
            + "&nbsp;&nbsp;</u></strong><br>&nbsp;Computer: "
            + computerScore + "<br>&nbsp;You: " + playerScore + "</html>");
      }
      /*
      Name: updateClock
      Description: updates the clock with new time.
      Parameters: String clock string to update clock with.
      */
      private static void updateClock(String clockStr)
      {
         timerLbl.setText(clockStr);
         cardTable.pnlTimer.revalidate();
         cardTable.pnlTimer.repaint();
      }
      /*
      Name: addButtonListener
      Description: adds an action listener to clock buttons.
      */
      private static void addButtonListener()
      {
         cantPlayBtn.addActionListener(Model.controller);
         clockStartBtn.addActionListener(Model.controller);
         clockStopBtn.addActionListener(Model.controller);
      }
      /*
      Name: removeButtonListener
      Description: removes the action listener from clock buttons.
      */
      private static void removeButtonListener()
      {
         cantPlayBtn.removeActionListener(Model.controller);
         clockStartBtn.removeActionListener(Model.controller);
         clockStopBtn.removeActionListener(Model.controller);
      }
      /*
      Name: addStatusListener
      Description: adds a mouse listener to status text area.
      */
      private static void addStatusListener()
      {
         statusTxt.addMouseListener(Model.controller);
      }
      /*
      Name: removeStatusListener
      Description: removes the mouse listener from status text area.
      */
      private static void removeStatusListener()
      {
         statusTxt.setBorder(null);
         statusTxt.removeMouseListener(Model.controller);
      }
      /*
      Name: getStatusTxt
      Description: gets game status text.
      */
      private String getStatusTxt()
      {
         return statusTxt.getText();
      }
      /*
      Name: setStatusTxt
      Description: sets game status text.
      Parameter: String text to use for status.
      */
      private void setStatusTxt(String text)
      {
         statusTxt.setText(text);
         cardTable.pnlStatMsg.revalidate();
         cardTable.pnlStatMsg.repaint();
      }
      /*
      Name: setPlayedCard
      Description: sets card on indicated build stack.
      Parameter: int stack to set card on.
      Parameter: Card card to set on stack.
      */
      private void setPlayedCard(int stack, Card card)
      {
         if (card == null)
         {
            playedCardLabels[stack].setIcon(null);
         }
         else
         {
            playedCardLabels[stack].setIcon(GUICard.getIcon(card));
         }
      }
      /*
      Name: setPlayLabelText
      Description: sets label text for card player.
      Parameter: int player to set text for.
      Parameter: String text to set label with.
      */
      private void setPlayLabelText(int player, String text)
      {
         playLabelText[player].setText(text);
         cardTable.pnlPlayerMsg.revalidate();
         cardTable.pnlPlayerMsg.repaint();
      }
      /*
      Name: dealHand
      Description: deal hands to players.
      Parameter: int player to deal hand to.
      Parameter: Hand hand to be dealt.
      Parameter: boolean showCards true if cards are to be face up.
      */
      public void dealHand(int player, Hand hand, boolean showCards)
      {
         View.cardTable.handPanels[player].removeAll();
         View.playerHands[player] = new JLabel[Model.NUM_CARDS_PER_HAND];
         for (int i = 0; i < hand.getNumCards(); i++)
         {
            View.playerHands[player][i] = new JLabel();       
            if (showCards)
            {
               View.playerHands[player][i].setIcon(GUICard.getIcon(hand
                  .inspectCard(i)));
               View.playerHands[player][i].addMouseListener(Model.controller);
            }
            else
            {
               View.playerHands[player][i].setIcon(GUICard.getBackCardIcon());
            }
            View.cardTable.handPanels[player].add(View.playerHands[player][i]);
         }        
         View.cardTable.handPanels[player].validate();
         View.cardTable.handPanels[player].repaint();
      }
      /*
      Name: selectCard
      Description: adds a border to selected card
      Parameter:  JLabel label that the card is contained in
      */
      private void selectCard(JLabel label)
      {
         label.setBorder(new LineBorder(SLCT_COLOR));
      }
      /*
      Name: unSelectCard
      Description: removes a border from unselected card.
      Parameter: JLabel label that the card is contained in.
      */
      private void unSelectCard(JLabel label)
      {
         label.setBorder(null);
      }
      /*
      Name: showCardTable
      Description: sets the card table to visible.
      */
      private void showCardTable()
      {
         View.cardTable.setVisible(true);
      }
      /*
      GUICard Class, Bill
      Description: reads and store image files / Icons.
      */
      public static class GUICard
      {
         private static Icon[][] iconCards = 
            new ImageIcon[14][4];// 14 = A-K + joker
         private static Icon iconBack;
         static boolean iconsLoaded = false;
         private static String[] cardSuites = new
            String[] { "C", "D", "H", "S" };
         /*
         Default Constructor for GUICard
         */
         @SuppressWarnings("unused")
         public GUICard() 
         {
             loadCardIcons();
         }
         /*
         Name: loadCardIcons
         Description: used for storing the Icons in a 2D array.
         it avoids reloading the icons after it has already loaded them once.
         */
         private static void loadCardIcons()
         {
            if (!iconsLoaded)
            {
               // build the file names("AC.gif", "2C.gif", "3C.gif", "TC.gif",
               // etc.)
               // in a SHORT loop. For each file name, read it in and use it to
               // instantiate each of the 57 Icons in the icon[] array.
               for (int x = 0; x < cardSuites.length; x++) 
               {
                  for (int y = 0; y < Card.valuRanks.length; y++) 
                  {
                     iconCards[y][x] = new ImageIcon("images/" +
                        Card.valuRanks[y] + cardSuites[x] + ".gif");
                  }
               }
               iconBack = new ImageIcon("images/BK.gif");
               iconsLoaded = true;
            }
         }
         /*
         Name: turnIntIntoCardValue 
         Description: turns 0 - 13 into "A", "2", "3", ... "Q", "K", "X" 
         Parameter: int to turn into card value Returns: String value of card
         */
         @SuppressWarnings("unused")
         static String turnIntIntoCardValue(int k) 
         {
            return String.valueOf(Card.valuRanks[k]);
         }
         /*
         Name: turnIntIntoCardSuit 
         Description: turns 0 - 3 into "C", "D", "H", "S" 
         Parameter: int to turn into card suit Returns: String suit of card
         */
         @SuppressWarnings("unused")
         static String turnIntIntoCardSuit(int j) 
         {
            return cardSuites[j];
         }
         /*
         Name: valueAsInt
         Description: used to get int value of a card
         Parameter: Card object to get int value from
         Returns: int value of card
         */
         private static int valueAsInt(Card card)
         {
            String values = new String(Card.valuRanks);   
            return values.indexOf(card.getValue());
         }
         /*
         Name: suitAsInt
         Description: used to get int suit of a card
         Parameter: Card object to get int suit from
         Returns: int suit ordinal of card
         */
         private static int suitAsInt(Card card)
         {
            return card.getSuit().ordinal();
         }       
         /*
         Name: getIcon
         Description: takes aCard object from the client, and
         returns the Icon for that card 
         Parameter: Card object to get Icon for
         Return: Icon to represent Card object
         */
         public static Icon getIcon(Card card)
         {
            //Load all of the card icons if they haven't been already.
            if (!GUICard.iconsLoaded)
               GUICard.loadCardIcons();
            return iconCards[valueAsInt(card)][suitAsInt(card)];
         }
         /*
         Name: getBackCardIcon
         Description: used to get the Icon for a card
         Return: Icon to represent back of a card
         */
         public static Icon getBackCardIcon()
         {
            if (!GUICard.iconsLoaded)
               GUICard.loadCardIcons();
            return GUICard.iconBack;
         }
      }
      /*
      CardTable Class extends JFrame, Lilia, Bill 
      controls layout of panels and cards on GUI
      */
      @SuppressWarnings("serial")
      class CardTable extends JFrame
      {
         static final int MAX_CARDS_PER_HAND = 56;
         static final int MAX_PLAYERS = 2; // for now, we only allow 2 person games
         private int numCardsPerHand;
         private int numPlayers;         
         public JPanel[] handPanels;
         public JPanel pnlPlayArea, pnlPlayedCards, pnlPlayerMsg,
            pnlStatMsg, pnlNoPlays, pnlPlayedArea, pnlTimer, 
            pnlTimerLbl, pnlTimerBtn, pnlTimerSbBtn;
         
         /*
         Constructor for CardTable Class
         Description: constructor filters input,
         adds any panels to the JFrame,
         and establishes layouts.
         Parameter: String title of JFrame 
         Parameter: int number of cards per hand 
         Parameter: int number of players
         */
         public CardTable(String title, int numCardsPerHand, int numPlayers)
         {
            super(title);
            if (!chkParams(title, numCardsPerHand, numPlayers)) 
            {
               return;
            }
            this.handPanels = new JPanel[numPlayers];
            this.setSize(900, 600);
            this.setMinimumSize(new Dimension(900, 600));
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // use border layout to allow different sized areas
            BorderLayout layout = new BorderLayout();
            this.setLayout(layout);
            // use flow layout for the hands ----------------------------------
            FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
            // computer hand --------------------------------------------------
            TitledBorder border = new TitledBorder("Computer Hand");
            this.handPanels[0] = new JPanel();
            this.handPanels[0].setBackground(View.GREEN_LGT);
            this.handPanels[0].setLayout(flowLayout);
            this.handPanels[0].setPreferredSize(new Dimension((int)
               this.getMinimumSize().getWidth() - 200, 110));
            JScrollPane scrollComputerHand = new JScrollPane(
               this.handPanels[0]);
            scrollComputerHand.setVerticalScrollBarPolicy(JScrollPane
               .VERTICAL_SCROLLBAR_NEVER);
            scrollComputerHand.setBorder(border);
            this.add(scrollComputerHand, BorderLayout.NORTH);
            // playing area ---------------------------------------------------
            border = new TitledBorder("Playing Area");
            GridLayout cardsArea = new GridLayout(1, 2);
            GridLayout statusArea = new GridLayout(2, 1);
            pnlPlayArea = new JPanel();
            pnlPlayArea.setBackground(View.GREEN_LGT);
            pnlPlayArea.setBorder(border);
            layout = new BorderLayout();
            pnlPlayArea.setLayout(layout);
            pnlPlayedArea = new JPanel();
            pnlPlayedArea.setBackground(View.GREEN_LGT);
            pnlPlayedArea.setLayout(new GridLayout(2, 1));
            pnlTimer = new JPanel();
            pnlTimer.setBackground(View.GREEN_LGT);
            pnlTimer.setLayout(statusArea);
            pnlNoPlays = new JPanel();
            pnlNoPlays.setBackground(View.GREEN_LGT);
            pnlNoPlays.setLayout(new GridLayout(3, 1));
            pnlPlayedCards = new JPanel();
            pnlPlayedCards.setBackground(View.GREEN_LGT);
            pnlPlayedCards.setLayout(cardsArea);
            pnlPlayerMsg = new JPanel();
            pnlPlayerMsg.setBackground(View.GREEN_LGT);
            pnlPlayerMsg.setLayout(cardsArea);
            pnlStatMsg = new JPanel();
            pnlStatMsg.setBackground(View.GREEN_LGT);
            pnlStatMsg.setLayout(statusArea);
            pnlPlayedArea.add(pnlPlayedCards);
            pnlPlayedArea.add(pnlPlayerMsg);
            pnlPlayArea.add(pnlTimer, BorderLayout.EAST);
            pnlPlayArea.add(pnlNoPlays, BorderLayout.EAST);
            pnlPlayArea.add(pnlPlayedArea, BorderLayout.CENTER);
            pnlPlayArea.add(pnlStatMsg, BorderLayout.SOUTH);
            this.add(pnlPlayArea, BorderLayout.CENTER);
            // timer clock ----------------------------------------------------
            border = new TitledBorder("Game Clock");
            GridLayout gridLayoutTimer = new GridLayout(1, 2);
            GridLayout subGridLayoutTimer = new GridLayout(2, 1);
            pnlTimer = new JPanel();
            pnlTimer.setBackground(View.GREEN_LGT);
            pnlTimer.setBorder(border);
            layout = new BorderLayout();
            pnlTimer.setLayout(layout);
            pnlTimerLbl = new JPanel();
            pnlTimerLbl.setBackground(View.GREEN_LGT);
            pnlTimerLbl.setLayout(gridLayoutTimer);
            pnlTimerBtn = new JPanel();
            pnlTimerBtn.setBackground(View.GREEN_LGT);
            pnlTimerBtn.setLayout(gridLayoutTimer);
            pnlTimerSbBtn = new JPanel();
            pnlTimerSbBtn.setBackground(View.GREEN_LGT);
            pnlTimerSbBtn.setLayout(subGridLayoutTimer);
            pnlTimerLbl.setPreferredSize(new Dimension(250, 50));
            pnlTimerBtn.setPreferredSize(new Dimension(250, 50));
            pnlTimerSbBtn.setPreferredSize(new Dimension(250, 50));
            pnlTimer.add(pnlTimerLbl, BorderLayout.NORTH);
            pnlTimer.add(pnlTimerBtn, BorderLayout.SOUTH);
            pnlTimerBtn.add(pnlTimerSbBtn);
            this.add(pnlTimer, BorderLayout.EAST);
            // players hand ---------------------------------------------------
            border = new TitledBorder("Your Hand");
            this.handPanels[1] = new JPanel();
            this.handPanels[1].setBackground(View.GREEN_LGT);
            this.handPanels[1].setLayout(flowLayout);
            this.handPanels[1].setPreferredSize(new Dimension((int) 
               this.getMinimumSize().getWidth() - 200, 110));
            JScrollPane scrollHumanHand = new JScrollPane(this.handPanels[1]);
            scrollHumanHand.setVerticalScrollBarPolicy(
               JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollHumanHand.setBorder(border);
            this.add(scrollHumanHand, BorderLayout.SOUTH);
         }
         /*
         Name: chkParams
         Description: input filter for the constructor
         Parameter: String title of JFrame
         Parameter: int number of cards per hand
         Parameter: int number of players
         Returns: boolean true if params are valid
         */
         private boolean chkParams(String title, int numCardsPerHand, 
               int numPlayers) 
         {
            if (title.length() <= 0 || numCardsPerHand <= 0 || 
                  numCardsPerHand > MAX_CARDS_PER_HAND || 
                  numPlayers <= 0 || numPlayers > MAX_PLAYERS) 
            {
               return false;
            }
            return true;
         }
         /*
         Name: getNumCardsPerHand
         Description: accessor for the number of cards per hand
         Returns: int number of cards per hand
         */
         @SuppressWarnings("unused")
         public int getNumCardsPerHand() 
         {
            return numCardsPerHand;
         }
         /*
         Name: getNumPlayers 
         Description: accessor for the number of players
         Returns: int number of players
         */
         @SuppressWarnings("unused")
         public int getNumPlayers() 
         {
            return numPlayers;
         }
      }
   }
   /*
   Controller Class
   */
   private static class Controller implements MouseListener, ActionListener
   {
      private static JLabel selectedCard = null;
      private static boolean isStackClickable = false;     
      /*
      Name: mouseEntered
      Description: fires when the mouse enters a card or status label area
      Parameter: MouseEvent event object
      */
      public void mouseEntered(MouseEvent e)
      {
         JLabel source = (JLabel)e.getSource();
        
         if (source == View.playedCardLabels[0] || 
               source == View.playedCardLabels[1])
         {
            if (isStackClickable)
            {
               view.selectCard(source);
            }
         }
      }     
      /*
      Name: mouseExited
      Description: fires when the mouse leaves a card or status label area
      Parameter: MouseEvent event object
      */
      public void mouseExited(MouseEvent e)
      {
         JLabel source = (JLabel)e.getSource();
         if (source != selectedCard)
         {
            view.unSelectCard(source);
         }
      }
      /*
      Name: mouseClicked
      Description: fires when the mouse clicks a card or status label
      Parameter: MouseEvent event object
      */
      public void mouseClicked(MouseEvent e)
      {
         JLabel source = (JLabel)e.getSource();        
         if (source == View.statusTxt)
         {
            Model.initGame();
            return;
         }
         for (int playerHand = 0; playerHand < View.playerHands.length;
            playerHand++)
         {
            for (int card = 0; card < Model.cardBuildGame.getHand(playerHand)
               .getNumCards(); card++)
            {
               if (View.playerHands[playerHand][card].getIcon() == 
                     View.GUICard.getBackCardIcon())
               {
                  continue;
               }
               if (View.playerHands[playerHand][card] == source)
               {
                  if (source != selectedCard && selectedCard != null)
                  {
                     view.unSelectCard(selectedCard);
                  }
                  selectedCard = source;
                  Model.selectedCard = card;
                  isStackClickable = true;
                  view.setStatusTxt("Click on the Build Deck you would like "
                     + "to play your card on.");
                  view.selectCard(source);
                  return;
               }
            }
         }
         if (isStackClickable)
         {
            boolean cardPlaced = false;           
            if (source == View.playedCardLabels[0])
            {
               cardPlaced = Model.placeCard(0);
            }
            else if (source == View.playedCardLabels[1])
               cardPlaced = Model.placeCard(1);           
            if (cardPlaced)
            {
               isStackClickable = false;
               view.unSelectCard(source);
               selectedCard = null;
            }
         }
      }
      /*
      Name: actionPerformed
      Description: performs action based on event received
      Parameter: ActionEvent event object
      */
      @SuppressWarnings("deprecation")
      public void actionPerformed(ActionEvent e)
      {
         JButton source = (JButton)e.getSource();         
         if (source.getActionCommand() == Model.CANNOT_PLAY)
         {
            Model.skipPlayerTurn();
         }
         else if (source.getActionCommand() == Model.START_CLOCK)
         {
            Model.isClockRunning = true;
            Model.timerThread = new Thread(Model.timer);
            Model.timerThread.start();
            source.setText(Model.RESET_CLOCK);
         }
         else
         {
            Model.timerThread.stop(); // @Deprecated
            try
            {
               Model.timerThread.join();
            }
            catch (InterruptedException e1)
            {
               e1.printStackTrace();
            }
            finally
            {
               if (source.getActionCommand() == Model.RESET_CLOCK)
               {
                  Model.isClockRunning = true;
                  Model.timerThread = new Thread(Model.timer);
                  Model.resetTimer();
                  Model.timerThread.start();
                  source.setText(Model.RESET_CLOCK);
               }
               else
               {
                  View.clockStartBtn.setText(Model.START_CLOCK);
               }
            }
         }
      }
      /*
      Name: mouseReleased
      Description: fires when mouse button is released.
      it is not used, but must be inherited.
      Parameter: MouseEvent object e
      */
      public void mouseReleased(MouseEvent e)
      {
         // not used, but must be inherited.
      }
      /*
      Name: mousePressed
      Description: fires when mouse button is pressed.
      it is not used, but must be inherited.
      Parameter: MouseEvent object e
      */
      public void mousePressed(MouseEvent e)
      {
         // not used, but must be inherited.
      }
   }
}
/*
Deck Class, Bill
*/
class Deck
{
   public static final int MAX_CARDS = 336; // 6 packs * 56 cards/pack
   public static Card[] masterPack = new Card[56];
   private Card[] cards;
   private int topCard;
   private int numPacks;
   private boolean flag = true;
   /*
   Name: Deck
   Description: default constructor for the Deck class
   */
   public Deck()
   {
      if (this.flag == true)
      {
         allocateMasterPack();
      }   
      this.numPacks = 1;
      init(numPacks);
      flag = false;
   }
   /*
   Name: Deck
   Parameter(s): int for number of packs
   Description: Overload constructor for the Deck class
   a constructor that populates the arrays 
   and assigns initial values to members.  
   Overloaded so that if no parameters are passed, 1 pack is assumed.
   */
   public Deck(int numPacks)
   {
      if (this.flag == true) 
      {
         allocateMasterPack();
      }
      int packs = numPacks;
      init(packs);
      flag = false; 
   }
   /*
   Name: init
   Parameter(s): int for number of packs
   Description: repopulate cards[] with the standard 52 ? numPacks cards. 
   We should not repopulate the static array, masterPack[], 
   since that was done once, in the (firstinvoked) constructor
   and never changes.
   */
   public void init(int numPacks)
   {
      int numCards = (((numPacks * 56) > 0) &&
         ((numPacks * 56) <= MAX_CARDS)) ? (numPacks * 56) : 56;
      cards = new Card[numCards];
      for (int i = 0; i < numCards; i += 56)
      {
         for (int j = 0; j < 56; j++)
         {
            cards[i + j] = new Card(masterPack[j]);
         }
      }
      topCard = numCards - 1;
   }
   /*
   Name:shuffle
   Description:mixes up the cards with the help of 
   the standard random number generator.
   */
   public void shuffle()
   {
      //shuffle the cards using Math.Random()
      int index1, index2, k;
      int numCards = cards.length;
      for (k = 0; k < numCards; k++)
      {
         index1 = (int) (Math.random() * numCards);
         index2 = (int) (Math.random() * numCards);
         Card card = cards[index1];
         cards[index1] = cards[index2];
         cards [index2] = card;
      }
      topCard = numCards - 1;
   }
   /*
   Name: dealCard
   Return: returns card in the top of cards[]
   or null when invalid
   Purpose of return: to allow the calling method
   to get the card from the top of cards[]
   Description: returns and removes the card 
   in the top occupied position of cards[].
   */
   public Card dealCard()
   {
     Card theCard;
      topCard = getTopCard();
      if (topCard < MAX_CARDS && topCard >= 0)
      {
         theCard = 
            new Card(cards[topCard].getValue(), cards[topCard].getSuit());
         cards[topCard] = null;
         topCard--;
         return theCard;
      }
      else
      {
         theCard = new Card('0', Card.Suit.spades);
         return theCard;
      }
   }
   /*
   Name: getTopCard
   Return: topCard
   Purpose of return: to allow the calling method to 
   have access to the private member topCard
   Description: Accessor for the int, topCard
   */
   public int getTopCard()
   {
      return topCard;
   }
   /*
   Name: Card inspectCard
   Parameter(s): int k 
   Return: a card object
   Purpose of return: to allow a a card or
   Returns a card with errorFlag = true if k is bad
   Description: Accessor for an individual card
   */
   public Card inspectCard(int k)
   {
      Card card;
      if (k > topCard)
      {
         card = new Card();
         card.set('0', Card.Suit.spades);
      }
      else
      {
         card = new Card();
         card.set(cards[k].getValue(), cards[k].getSuit());
      }
      return card;
   }
   /*
   Name: allocateMasterPack
   Description:this is a private method that will be called by the 
   constructor. However, it has to be done with a very simple twist: 
   even if many Deck objects are constructed in a given
   program, this static method will not allow itself to 
   be executed more than once. Since masterPack[] is a
   static, unchanging, entity, it need not be built every time 
   a new Deck is instantiated. So this method needs
   to be able to ask itself, "Have I been here before?", 
   and if the answer is "yes", it will immediately return
   without doing anything; it has already built masterPack[] 
   in a previous invocation
   */
   private static void allocateMasterPack()
   {
      //fill the masterpack
      int counter = 0;
      char[] value = {'A','2','3','4','5','6','7','8','9','T','J','Q','K','X'};
      for (Card.Suit s : Card.Suit.values())
      {
         for (int index = 0; index <= 13; index++)
         {
            masterPack[counter++] = new Card(value[index], s);
         }
      }
   }
   /*
   Name: addCard
   Description: adds card and makes sure that there are not
   too many instances of the card in the deck if you add it
   Parameter: Card card to be added
   Returns: boolean false if there will be too many cards
   */
   public boolean addCard(Card card)
   {
      if (Arrays.asList(cards).indexOf(card) > 0)
      {
         int openElement = 0;
         for (int x = 0; x < cards.length; x++)
         {
            if (cards[x] == null)
            {
               openElement = x;
               break;
            }
         }
         cards[openElement] = card;
         topCard = openElement;
         return true;
      }
      return false;
   }
   /*
   Name: removeCard
   Description: removes a specific card from the deck, and
   places the current top card into its place.
   Parameter: Card card to be removed
   Returns: boolean false if card is not in deck
   */
   public boolean removeCard(Card card)
   {
      int index = Arrays.asList(cards).indexOf(card);
      if (index > 0)
      {
         Card cardCopy = new Card(cards[topCard].getValue(),
            cards[topCard].getSuit());
         cards[index] = cardCopy;
         cards[topCard] = null;
         topCard--;
         return true;
      }
      return false;
   }
   /*
   Name: sort
   Description: puts all of the cards in the deck back into 
   the right order according to their values by calling 
   the Card class arraySort()
   */
   public void sort()
   {
      Card.arraySort(cards, cards.length);
   }
   /*
   Name: getNumCards
   Description: returns the number of cards remaining in the deck.
   Returns: int number of cards
   */
   public int getNumCards()
   {
      int numCards = 0;
      for (int x = 0; x < cards.length; x++)
      {
         if (cards[x] != null)
         {
            numCards++;
         }
      }
      return numCards;
   }
}
/*
Card Class, Lilia
*/
class Card  implements Comparable<Object>
{
   private char value;
   private Suit suit;
   public boolean errorFlag;
   public static char[] valuRanks  = {'A', '2', '3', '4',
      '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'X'};
   /*
   Name: Suit
   Description: enum for card suits
   */
   public enum Suit
   {
      clubs, diamonds, hearts, spades;
   }  
   /*
   Name: card
   Parameter(s): char for value, and suit
   Description: constructor for Card - setter
   */
   public Card(char value, Suit suit)
   {
      set(value, suit);     
   }
   /*
   Name: Card
   Parameter(s): a card object
   Description: constructor -overload instantiate without parameters
   */
   public Card(Card card)
   {
      this(card.value, card.suit);
   }
   /*
   Name: Card
   Description: default constructor for card 
   (no parameters passed) is the ('A', spades)
   */
   public Card()
   {
      this('A', Suit.spades);
   }
   /*
   Name: toString
   Return: returns the value and suit of a card
   in one string
   Purpose of return: to allow the calling method
   to easily display the value and suit of a card
   Description: to combine the value and suit of a card
   to on string and return an invalid notification if 
   a card is not valid
   */
   public String toString()
   {
      String val;
      String returnValue;
      if (errorFlag == true)
      {
         returnValue = "[invalid]";
         return returnValue;
      }
      else
      {
         switch (value) 
         {
            case 'A':
               val = "Ace";
               break;
            case 'T':
               val = "10";
               break;
            case 'J':
               val = "Jack";
               break;
            case 'Q':
               val = "Queen";
               break;
            case 'K':
               val = "King";
               break;
            case 'X':
               val = "Joker";
               break;
            default:
               val = value + "";
               break;
         }
         if (val == "Joker")
         {
            returnValue = val + " number " + (suit.ordinal() + 1);
         }
         else
         {
            returnValue = val + " of " + suit;
         }
         return returnValue;
      }
   }   
   /*
   Name: isValid
   Parameter(s): char for value, and suit
   Return: boolean
   Purpose of return: to indicate to the calling method
   if the value of a card is valid or not
   Description: check a card to see if it is of 
   valid value 
   */
   private boolean isValid(char value, Suit suit)
   {
      //checking for valid values of the cards within range of 2-10, or J-A.
      if ((value >= '2' && value <= '9') || value == 'T' || value == 'J' 
            || value == 'Q' || value == 'K' || value == 'A' || value == 'X')
      {
         return true;
      }
      else
      {
         return false;
      }
   }
   /*
   Name: set
   Parameter(s): char for value, and suit
   Return: boolean
   Purpose of return: to return a notification
   to indicate to the calling method if a card 
   was valid and set or not
   Description: Mutator that accepts the legal values  
   When bad values are passed, errorFlag is set to true 
   and other values can be left in any state. 
   If good values are passed, they are stored 
   and errorFlag is set to false. 
   Uses private helper(isValid method)
   */
   public boolean set(char value, Suit suit)
   {
      boolean isLegalValue;      
      isLegalValue = isValid(value, suit);
      if (isLegalValue)
      {
         this.value = value;
         this.suit = suit;
         this.errorFlag = false;
      }
      else
      {
         this.errorFlag = true;
         isLegalValue = false;
      }
      return isLegalValue;
   }
   /*
   Name: getValue
   Return: value
   Purpose of return: allows the calling method
   access to the private data value
   Description: accessor/getter for value
   */  
   public char getValue()
   {
      return value;
   }
   /*
   Name: getSuit
   Return: suit
   Purpose of return: allows the calling method
   access to the private data suit
   Description: accessor/getter for suit
   */  
   public Suit getSuit()
   {
      return suit;
   }
   /*
   Name: getErrorFlag
   Return: errorFlag
   Purpose of return: allows the calling method
   access to the private data errorFlag
   Description: accessor/getter for errorFlag
   */  
   public boolean getErrorFlag()
   {
      return errorFlag;
   }
   /*
   Name: equals
   Parameter(s): card object
   Return: boolean
   Purpose of return & Description:
   returns true if all the fields (members) are identical and 
   returns false if otherwise
   */
   public boolean equals(Card card)
   {
      if (this.value == card.value && this.suit == card.suit 
              && this.errorFlag == card.errorFlag)
      {
         return true;
      }
      else
      {
         return false;
      }
   }
   /*
   Name: arraySort
   Description: sorts the incoming array of cards using a bubble sort routine.
   Parameters: Card array of cards, and int size of the array
   */
   static void arraySort(Card[] cards, int arraySize)
   {
      Card temp;
      for (int i = 0; i < arraySize; i++)
      {
         for (int j = 1; j < arraySize - i; j++)
         {
            if (getIntVal(cards[j - 1].getValue())
                  > getIntVal(cards[j].getValue()))
            {
               temp = cards[j - 1];
               cards[j - 1] = cards[j];
               cards[j] = temp;
            }
         }
      }
   }
   /*
   Name: getIntVal
   Description: get the int value of a rank
   Parameter: char value of rank
   Returns: int value of rank
   */
   private static int getIntVal(char myRank)
   {
      int val = -1;
      for (int i = 0; i < valuRanks.length; i++)
      {
         if (valuRanks[i] == myRank)
         {
            return (val = i);
         }   
      }
      return val;
   }
   /*
   Name: compareTo
   Description: used to compard two card objects
   Parameter: Object t a card object
   Returns: 0 if the same, 1 if greater than, and -1 if less than
   */
   public int compareTo(Object t)
   {
      Card card = (Card) t;      
      if (t.getClass() != this.getClass())
      {
         return 1;
      }
      String strRanks = new String(valuRanks);
      if (strRanks.indexOf(card.getValue()) < 0)
      {
         return 1;
      }
      if (strRanks.indexOf(card.getValue()) < strRanks.indexOf(this.getValue()))
      {
         return 1;
      }
      if (strRanks.indexOf(card.getValue()) == strRanks.indexOf(this.getValue()))
      {
         return 0;
      }
      if (strRanks.indexOf(card.getValue()) > strRanks.indexOf(this.getValue()))
      {
         return -1;
      }
      return 1;
   } 
}
/*
Hand Class, Christina
*/
class Hand
{
   public static final int MAX_CARDS = 100;
   private Card[] myCards = new Card[MAX_CARDS];
   private int numCards;
   /*
   Name: Hand
   Parameter(s): default constructor
   Description: initializes variable numCards to 0
   */
   public Hand()
   {
      numCards = 0;
   }
   /*
   Name: getNumCards
   Return: int numCards
   Purpose of return: allows the calling method
   access to the private data numCards
   Description: accessor for numCards
   */
   public int getNumCards()
   {
      return numCards;
   }
   /*
   Name: resetHand
   Description: reset the hand to null
   */
   public void resetHand() 
   {
      numCards = 0;
   }
   /*
   Name: takeCard
   Parameter(s): card object
   Return: boolean
   Purpose of return: to allow the calling method
   to determine if a card was placed into myCards
   Description: inserts a card into myCards
   */
   public boolean takeCard(Card card)
   {
      if (numCards < MAX_CARDS)
      {
         Card newCard = new Card(card);
         myCards[numCards] = newCard;
         numCards++;
         return true;
      }
      else
      {
         return false;
      }        
   }
   /*
   Name: playCard
   Return: card object
   Purpose of return: to allow the calling 
   method to get a card from the hand
   Description: removes and returns the
   card at the top position of myCards
   */
   public Card playCard()
   {
      Card card;
      if (myCards[(numCards - 1)] == null)
      {
         card = new Card();
         card.set('0', Card.Suit.spades);
      }
      else
      {   
         card = myCards[(numCards - 1)];
         myCards[(numCards - 1)] = null;
         numCards--;
      }
      return card;
   }
   /*
   Name: playCard
   Parameter: int index to get card from
   Return: card object at index selected
   Purpose of return: to allow the calling 
   method to get a card from the hand
   Description: removes and returns the
   card at the index indicated of myCards
   */
   public Card playCard(int cardIndex)
   {
      if ( numCards == 0 ) //error
      {
         //Creates a card that does not work
         return new Card('M', Card.Suit.spades);
      }
      //Decreases numCards.
      Card card = myCards[cardIndex];
      numCards--;
      for (int i = cardIndex; i < numCards; i++)
      {
         myCards[i] = myCards[i + 1];
      }
      myCards[numCards] = null;
      return card;
   }
   /*
   Name: toString
   Return: a string combining all cards in a hand
   into one string
   Purpose of return: to allow the calling method
   to display the entire hand
   Description: combine the entire hand
   into one string
   */
   public String toString() 
   {
      //combines hand into one string
      String hand = "";
      if (myCards == null)
      {
         return hand;
      }
      else
      {
         for (int i = 0; i < numCards; i++)
         {
            if (myCards[i] != null)
            {
               if ((i + 1) < numCards)
               {
                  if (myCards[i + 1] == null)
                  {
                     hand += (myCards[i] + " ");
                  }
                  else
                  {
                     hand += (myCards[i] + "," + " ");
                  }
               }
               else
               {
                  hand += (myCards[i] + " ");
               }
            }           
         }
      }
      return hand;
   }
   /*
   Name: inspectCard
   Parameter(s): int k 
   Return: card object
   Purpose of return: to allow the calling method
   to get an individual card
   Description: Accessor for an individual card
   Returns a card with errorFlag = true if k is bad
   */
   public Card inspectCard(int k)
   {
      Card card;
      if (k > numCards)
      {
         card = new Card();
         card.set('0', Card.Suit.spades);
      }
      else
      {
         card = new Card();
         card.set(myCards[k].getValue(), myCards[k].getSuit());
      }
      return card;
   }
   /*
   Name: sort
   Description: sorts the hand by calling
   the arraySort() method in the Card class
   */
   public void sort()
   {
      Card.arraySort(myCards, numCards);
   }
}
/*
BuildDeck class
*/
class BuildDeck
{
   Card[] cards;
   int numCards;
   /*
   Description: Overload constructor for the BuildDeck class
   Parameter: in value of maximum cards.
   */
   BuildDeck(int maxCards)
   {
      cards = new Card[maxCards];
      numCards = 0;
   }
   /*
   Name: addCard
   Description: adds card to deck.
   Parameter: Card card to add to deck.
   Returns: boolean true if card was added.
   */
   public boolean addCard(Card card)
   {
      if (numCards == cards.length)
      {
         return false;
      }
      cards[numCards] = card;
      numCards++;
      return true;
   }
   /*
   Name: inspectTopCard
   Description: checks top card, and
   returns card if any cards remain,
   otherwise it returns invalid card
   Returns: Card card representing state of top card.
   */
   public Card inspectTopCard()
   {
      if (numCards == 0)
      {
         return new Card('M', Card.Suit.spades);
      }
      return new Card(cards[numCards - 1]);
   }
}
//class CardGameFramework  ----------------------------------------------------
class CardGameFramework
{
   private static final int MAX_PLAYERS = 50;
   private int numPlayers;
   private int numPacks;            // # standard 52-card packs per deck
   // ignoring jokers or unused cards
   private int numJokersPerPack;    // if 2 per pack & 3 packs per deck, get 6
   private int numUnusedCardsPerPack;  // # cards removed from each pack
   private int numCardsPerHand;        // # cards to deal each player
   private Deck deck;               // holds the initial full deck and gets
   // smaller (usually) during play
   private Hand[] hand;             // one Hand for each player
   private Card[] unusedCardsPerPack;   // an array holding the cards not used
   // in the game.  e.g. pinochle does not
   // use cards 2-8 of any suit
   Card playCard(int playerIndex, int cardIndex)
   {
      if (playerIndex < 0 ||  playerIndex > numPlayers - 1 ||
         cardIndex < 0 || cardIndex > numCardsPerHand - 1)
      {
         return new Card('M', Card.Suit.spades);
      }
      return hand[playerIndex].playCard(cardIndex);
   }   
   boolean takeCard(int playerIndex, int cardIndex)
   {
      // returns false if either argument is bad
      if (playerIndex < 0 ||  playerIndex > numPlayers - 1 ||
         cardIndex < 0 || cardIndex > numCardsPerHand - 1)
      {
         return false;
      }
      // Are there enough Cards?
      if (deck.getNumCards() <= 0)
         return false;
      return hand[playerIndex].takeCard(deck.dealCard());
   } // end takeCard()
   public CardGameFramework(int numPacks, int numJokersPerPack,
                            int numUnusedCardsPerPack, Card[] unusedCardsPerPack,
                            int numPlayers, int numCardsPerHand)
   {
      int k;
      // filter bad values
      if (numPacks < 1 || numPacks > 6)
         numPacks = 1;
      if (numJokersPerPack < 0 || numJokersPerPack > 4)
         numJokersPerPack = 0;
      if (numUnusedCardsPerPack < 0 || numUnusedCardsPerPack > 50) //  > 1 card
         numUnusedCardsPerPack = 0;
      if (numPlayers < 1 || numPlayers > MAX_PLAYERS)
         numPlayers = 4;
      // one of many ways to assure at least one full deal to all players
      if (numCardsPerHand < 1 ||
         numCardsPerHand > numPacks * (52 - numUnusedCardsPerPack)
            / numPlayers)
         numCardsPerHand = numPacks * (52 - numUnusedCardsPerPack) / numPlayers;
      // allocate
      this.unusedCardsPerPack = new Card[numUnusedCardsPerPack];
      this.hand = new Hand[numPlayers];
      for (k = 0; k < numPlayers; k++)
         this.hand[k] = new Hand();
      deck = new Deck(numPacks);
      // assign to members
      this.numPacks = numPacks;
      this.numJokersPerPack = numJokersPerPack;
      this.numUnusedCardsPerPack = numUnusedCardsPerPack;
      this.numPlayers = numPlayers;
      this.numCardsPerHand = numCardsPerHand;
      for (k = 0; k < numUnusedCardsPerPack; k++)
         this.unusedCardsPerPack[k] = unusedCardsPerPack[k];
      // prepare deck and shuffle
      newGame();
   }
   // constructor overload/default for game like bridge
   public CardGameFramework()
   {
      this(1, 0, 0, null, 4, 13);
   }
   public Hand getHand(int k)
   {
      // hands start from 0 like arrays
      // on error return automatic empty hand
      if (k < 0 || k >= numPlayers)
         return new Hand();
      return hand[k];
   }
   public Card getCardFromDeck()
   {
      return deck.dealCard();
   }
   public int getNumCardsRemainingInDeck()
   {
      return deck.getNumCards();
   }
   public void newGame()
   {
      int k, j;
      // clear the hands
      for (k = 0; k < numPlayers; k++)
         hand[k].resetHand();
      // restock the deck
      deck.init(numPacks);
      // remove unused cards
      for (k = 0; k < numUnusedCardsPerPack; k++)
         deck.removeCard(unusedCardsPerPack[k]);
      // add jokers
      for (k = 0; k < numPacks; k++)
         for (j = 0; j < numJokersPerPack; j++)
            deck.addCard(new Card('X', Card.Suit.values()[j]));
      // shuffle the cards
      deck.shuffle();
   }
   public boolean deal()
   {
      // returns false if not enough cards, but deals what it can
      int k, j;
      boolean enoughCards;
      // clear all hands
      for (j = 0; j < numPlayers; j++)
         hand[j].resetHand();
      enoughCards = true;
      for (k = 0; k < numCardsPerHand && enoughCards; k++)
      {
         for (j = 0; j < numPlayers; j++)
            if (deck.getNumCards() > 0)
               hand[j].takeCard(deck.dealCard());
            else
            {
               enoughCards = false;
               break;
            }
      }
      return enoughCards;
   }
   void sortHands()
   {
      int k;
      for (k = 0; k < numPlayers; k++)
         hand[k].sort();
   }
}