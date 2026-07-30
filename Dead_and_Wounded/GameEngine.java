import java.util.Random;

public class GameEngine {

    private String secretNumber;

    public GameEngine() {
        secretNumber = generateNumber();

        System.out.println("[Game Thread]");
        System.out.println("Secret number generated");
        System.out.println();
    }

    private String generateNumber() {
        Random random = new Random();

        String number = "";

        while (number.length() < 4) {
            int digit = random.nextInt(10);

            if (!number.contains("" + digit)) {
                number += digit;
            }
        }

        return number;
    }

    /**
     * A guess is well-formed only if it looks like the secret: exactly four
     * characters, every one a digit 0-9, and no digit repeated. Kept here beside
     * generateNumber() because both express the same rule about what a code is.
     */
    public static boolean isValidGuess(String guess) {
        if (guess == null || guess.length() != 4) {
            return false;
        }

        boolean[] seen = new boolean[10];

        for (int i = 0; i < 4; i++) {
            char c = guess.charAt(i);

            if (c < '0' || c > '9') {
                return false;
            }

            int digit = c - '0';

            if (seen[digit]) {
                return false;   // a repeated digit
            }

            seen[digit] = true;
        }

        return true;
    }

    public synchronized String checkGuess(String guess) {
        System.out.println("[Game Thread]");
        System.out.println("Checking guess: " + guess);

        int dead = 0;
        int wounded = 0;

        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) == secretNumber.charAt(i)) {
                dead++;
            } else if (secretNumber.contains("" + guess.charAt(i))) {
                wounded++;
            }
        }

        String result = "Dead: " + dead + " Wounded: " + wounded;

        System.out.println("[Game Thread]");
        System.out.println(result);
        System.out.println();

        return result;
    }

    public boolean win(String result) {
        return result.contains("Dead: 4");
    }

}