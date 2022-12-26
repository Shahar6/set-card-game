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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.beans.Transient;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Player player;
    @Mock
    private Player player2;
    @Mock
    private Logger logger;

   

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        player = new Player(env, dealer, table, 0, false);
        player2 = new Player(env, dealer, table, 1, true);
        Player[] players = new Player[2];
        dealer = new Dealer(env, table, players);
    }
/* @post: the time changed according to what was set.
*/
    @Test
    void changeTime(){
        long timeBefore = dealer.timer;
        int timeToChange = 1000;
        long time = dealer.changeTime(timeToChange);
        assertEquals(dealer.timer, timeBefore - timeToChange);
    }
/*@post: the time cannot be changed.
*/
    @Test
    void timerCanBeChange(){
        long change = -1;
        boolean changed = dealer.timerCanBeChange(change);
        assertFalse(changed , " the timer did not change because the time that sent is negetive.");
    }

}