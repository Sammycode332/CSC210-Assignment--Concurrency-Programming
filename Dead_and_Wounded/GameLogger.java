import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A single background thread that writes the game log.
 *
 * <p>Each guess used to spawn its own short-lived thread. Instead this class runs
 * one consumer for the life of the game and feeds it through a {@link BlockingQueue}:
 * the UI thread (the producer) drops a finished guess on the queue and returns at
 * once, while this thread (the consumer) takes entries one at a time and writes
 * them. That is the producer-consumer pattern from <em>Java Concurrency in
 * Practice</em>, ch. 5 -- the queue does all the hand-off and thread-safety work, so
 * neither side needs an explicit lock.
 */
public class GameLogger extends Thread {

    /** One guess and its result, timestamped when it was queued. */
    private static final class Entry {
        final String guess;
        final String result;
        final LocalDateTime time;

        Entry(String guess, String result) {
            this.guess = guess;
            this.result = result;
            this.time = LocalDateTime.now();
        }
    }

    /**
     * A sentinel that means "no more work is coming, finish and stop". Because the
     * queue is FIFO and there is one producer, every real entry queued before it is
     * written before the consumer sees this and exits. (The poison-pill shutdown
     * from JCiP ch. 7.)
     */
    private static final Entry POISON = new Entry(null, null);

    private final BlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
    private final String logFile;

    public GameLogger(String logFile) {
        this.logFile = logFile;
        setName("game-logger");
        setDaemon(true);
    }

    /**
     * Called by the UI thread. Hands the guess off to the queue and returns
     * immediately, so a slow disk write never stalls the interface. The queue is
     * unbounded, so this never blocks.
     */
    public void log(String guess, String result) {
        queue.add(new Entry(guess, result));
    }

    /** Ask the logger to drain whatever is queued and then stop. */
    public void shutdown() {
        queue.add(POISON);
    }

    @Override
    public void run() {
        System.out.println("[Logger Thread] started");

        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            while (true) {
                Entry entry = queue.take();     // blocks until there is work

                if (entry == POISON) {
                    break;                      // clean stop; queue already drained
                }

                write(out, entry);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.out.println("[Logger Thread] could not open log: " + e.getMessage());
        }

        System.out.println("[Logger Thread] finished");
    }

    private void write(PrintWriter out, Entry entry) {
        out.println("Time: " + entry.time);
        out.println("Guess: " + entry.guess);
        out.println("Result: " + entry.result);
        out.println("--------------------");
        out.flush();   // flush per entry so nothing is lost if the app is closed

        System.out.println("[Logger Thread]");
        System.out.println("Saved guess: " + entry.guess + " -> " + entry.result);
        System.out.println();
    }

}