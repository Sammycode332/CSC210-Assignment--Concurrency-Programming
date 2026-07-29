public class TimerThread extends Thread {


    private boolean running = true;


    public void run(){


        int time = 300;
       
        System.out.println("... TIMER STARTED AT " + time);


        while(running && time > 0){


            try{

                Thread.sleep(1000);

            }

            catch(Exception e){}


            time--;


            System.out.println(
                    "[Timer Thread]"
            );

            System.out.println(
                    "Time remaining: "
                    + time
                    + " seconds"
            );

            System.out.println();


        }


        System.out.println(
                "[Timer Thread] Finished"
        );

    }



    public void stopTimer(){

        running = false;

    }

}