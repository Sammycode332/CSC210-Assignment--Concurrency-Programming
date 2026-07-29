import java.awt.*;
import javax.swing.*;


public class GameWindow extends JFrame {


    JTextField guessField;

    JButton button;

    JLabel result;


    GameEngine game;

    TimerThread timer;



    public GameWindow(){


        game = new GameEngine();


        setTitle("Dead and Wounded");

        setSize(400,300);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);



        setLayout(new FlowLayout());



        guessField = new JTextField(10);

        button = new JButton("Guess");

        result = new JLabel("Enter Guess");



        add(new JLabel("Guess:"));

        add(guessField);

        add(button);

        add(result);



        timer = new TimerThread();

        timer.start();



        button.addActionListener(e -> {


            String guess =
                    guessField.getText();



            String answer =
                    game.checkGuess(guess);



            result.setText(answer);



            LoggerThread logger =
                    new LoggerThread(
                            guess,
                            answer
                    );


            logger.start();



            if(game.win(answer)){


                JOptionPane.showMessageDialog(
                        this,
                        "YOU WIN!"
                );


                timer.stopTimer();


            }


        });



        setVisible(true);


    }


}