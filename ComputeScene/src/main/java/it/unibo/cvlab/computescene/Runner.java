package it.unibo.cvlab.computescene;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe Runner: si tratta di un thread separato che servirà a fare parte del lavoro.
 */
public class Runner implements Runnable {

    private static final String TAG = Runner.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(Runner.class.getSimpleName());

    public interface Job{
        void doJob();
    }

    private Job job;

    //Thread su cui viaggerà il Runner.
    private Thread myThread;

    //Oggetti per la sincronizzazione.
    private volatile boolean jobRunning;
    private volatile boolean stop;
    private final Object stopCondition;
    private final Object finishCondition;

    public Runner() {
        this.myThread = new Thread(this);
        this.myThread.setName("Runner");
        this.jobRunning = false;
        this.stop = false;
        this.stopCondition = new Object();
        this.finishCondition = new Object();
    }

    public void start(){
        myThread.start();
    }

    public void stop() throws InterruptedException{
        stop = true;
        synchronized (stopCondition) {
            stopCondition.notify();
        }
        myThread.join();
    }

    public void doJob(Job job) {
        this.job = job;
        synchronized (stopCondition) {
            stopCondition.notify();
        }
    }


    public void waitJob() throws InterruptedException{
        synchronized (finishCondition){
            while(jobRunning) finishCondition.wait();
        }
    }

    @Override
    public void run() {
        try{
            while(!stop){
                jobRunning = true;

                if(job != null)
                    job.doJob();

                jobRunning = false;

                synchronized (finishCondition) {
                    finishCondition.notify();
                }

                synchronized (stopCondition){
                    stopCondition.wait();
                }

            }
        } catch (InterruptedException e) {
            Log.log(Level.WARNING, "Thread Interrotto nel wait", e);
        }
    }

}
