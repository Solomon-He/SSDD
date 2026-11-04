package org.ssdd.task;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssdd.auth.TokenService;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.detection.DetectionService;
import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One periodic dispatcher, no per-task in-memory backlog. DB state is the durable bounded queue. */
@Component
public class TaskWorker implements SmartLifecycle {
    private final TaskProperties config;
    private final TaskRepository tasks;
    private final TaskService service;
    private final DetectionService detection;
    private final DataSource dataSource;
    private final TransactionTemplate tx;
    private final AtomicBoolean stopping=new AtomicBoolean();
    private volatile boolean running;
    private volatile boolean applicationReady;
    private ScheduledExecutorService timer;
    private Connection ownership;
    private String lockName;
    private long connectionId;
    private boolean recovered;
    public TaskWorker(TaskProperties config,TaskRepository tasks,TaskService service,DetectionService detection,DataSource dataSource,PlatformTransactionManager manager){this.config=config;this.tasks=tasks;this.service=service;this.detection=detection;this.dataSource=dataSource;tx=new TransactionTemplate(manager);}
    @Override public void start(){
        if(!config.workerEnabled()||running||(timer!=null&&!timer.isTerminated()))return;
        stopping.set(false);running=true;recovered=false;
        timer=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"ssdd-async-worker");thread.setDaemon(false);return thread;});
        timer.scheduleWithFixedDelay(()->{if(applicationReady)runOnce();},config.pollInterval().toMillis(),config.pollInterval().toMillis(),TimeUnit.MILLISECONDS);
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void applicationReady(){applicationReady=true;}
    /** Also used by deterministic integration tests with automatic dispatch disabled. */
    public synchronized void runOnce(){
        if(stopping.get())return;
        try{
            if(!acquireOwnership())return;
            if(!recovered){service.recoverInterrupted();recovered=true;}
            // Idle polling must not reserve the shared synchronous detection slot.
            if(!tasks.hasEligible()){service.cleanupTerminal();return;}
            try(var admission=detection.admitBackground()){
                var row=tx.execute(status->tasks.claim());if(row!=null)service.execute(row,this::ownerAlive);
            }catch(BusinessException busy){if(busy.getStatus().value()!=503)throw busy;}
            service.cleanupTerminal();
        }catch(RuntimeException failure){
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Async dispatcher unavailable; durable tasks retained; type={}",failure.getClass().getSimpleName());releaseOwnership();recovered=false;
        }
    }
    private boolean acquireOwnership(){
        if(ownership!=null){if(ownerAlive())return true;releaseOwnership();recovered=false;}
        try{
            ownership=dataSource.getConnection();ownership.setNetworkTimeout(Runnable::run,5000);
            try(var statement=ownership.createStatement();var result=statement.executeQuery("SELECT DATABASE(),CONNECTION_ID()")){
                result.next();lockName="ssdd:task-worker:"+TokenService.hash(result.getString(1)).substring(0,40);connectionId=result.getLong(2);
            }
            try(var statement=ownership.prepareStatement("SELECT GET_LOCK(?,0)")){statement.setString(1,lockName);try(var result=statement.executeQuery()){result.next();if(result.getInt(1)==1)return true;}}
            releaseOwnership();return false;
        }catch(SQLException ex){releaseOwnership();throw new TaskFault("WORKER_OWNERSHIP_UNAVAILABLE",true);}
    }
    private boolean ownerAlive(){
        if(stopping.get()||ownership==null)return false;
        try(var statement=ownership.prepareStatement("SELECT IS_USED_LOCK(?)")){
            statement.setString(1,lockName);try(var result=statement.executeQuery()){return result.next()&&result.getLong(1)==connectionId;}
        }catch(SQLException ex){return false;}
    }
    private void releaseOwnership(){
        if(ownership==null)return;
        try(var statement=ownership.prepareStatement("SELECT RELEASE_LOCK(?)")){statement.setString(1,lockName);statement.execute();}
        catch(SQLException ex){try{ownership.abort(Runnable::run);}catch(SQLException ignored){}}
        finally{try{ownership.close();}catch(SQLException ignored){}ownership=null;}
    }
    @Override public void stop(Runnable callback){
        if(!stopping.compareAndSet(false,true)){callback.run();return;}
        running=false;if(timer!=null)timer.shutdown();
        var drain=new Thread(()->{
            try{if(timer!=null)timer.awaitTermination(35,TimeUnit.SECONDS);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();}
            finally{synchronized(this){releaseOwnership();}callback.run();}
        },"ssdd-async-worker-stop");drain.setDaemon(true);drain.start();
    }
    @Override public void stop(){stop(()->{});}
    @Override public boolean isRunning(){return running;}
    @Override public int getPhase(){return Integer.MAX_VALUE;}
}
