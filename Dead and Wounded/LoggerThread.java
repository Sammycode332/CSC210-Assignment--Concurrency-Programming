public class LoggerThread extends Thread {


    private String guess;
    private String result;



    public LoggerThread(String guess, String result){

        this.guess = guess;
        this.result = result;

    }



    public void run(){


        System.out.println(
                "[Logger Thread]"
        );

        System.out.println(
                "Saving guess: "
                + guess
        );


        try{

            Thread.sleep(500);

        }

        catch(Exception e){}



        System.out.println(
                "Saved result: "
                + result
        );

        System.out.println();


    }

}