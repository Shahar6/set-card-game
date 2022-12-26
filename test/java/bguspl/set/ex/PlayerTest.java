package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        player = new Player(env, dealer, table, 0, false);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {

        // force table.countCards to return 3
        // calculate the expected score for later
        int expectedScore = player.score() + 1;

        // call the method we are testing
        player.point();
        
        // check that the score was increased correctly
        assertEquals(expectedScore, player.score());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }
/*@post: the score will not be changed.
*/
    @Test
    void penalty(){
        //check that the player's score does not change after a penalty.
        int expectedScore = player.score();
        player.penalty();
        assertEquals(expectedScore, player.score());
    }
/*@post: the queue will not get illegal slots.
*@post: the queue will not get legal slots.
*/
    @Test
    void keyPressed(){
        //check that the press queue of player does not change when giving wrong slot.
        int expected = 0;
        int slot = -1;
        player.keyPress.clear();
        player.keyPressed(-1);
        assertEquals(expected, player.keyPress.size(), "the queue did not get the slot");
        //check that the press queue of player does change when giving correct slot.
        expected = 1;
        slot = 1;
        player.keyPressed(slot);
        assertEquals(expected, player.keyPress.size(), "the queue did get the slot");

    }
}