package org.ssdd.inference;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.ssdd.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Coordinates publication with snapshot acquisition; leases retain the exact model metadata and native handle. */
@Component
public class ModelRuntime {
    private final ModelRepository repository;
    private final InferenceProperties properties;
    private final ReentrantLock changes=new ReentrantLock();
    private final ReentrantLock gate=new ReentrantLock();
    private final Set<Entry> retired=new HashSet<>();
    private final Map<String,List<Runnable>> idleActions=new HashMap<>();
    private Entry active;
    private boolean closed;
    private String lastError="NO_ACTIVE_MODEL";
    public ModelRuntime(ModelRepository repository,InferenceProperties properties) { this.repository=repository;this.properties=properties; }

    public final class Change implements AutoCloseable {
        private final AtomicBoolean done=new AtomicBoolean();
        @Override public void close() { if(done.compareAndSet(false,true))changes.unlock(); }
    }
    public Change beginChange() {
        if(!changes.tryLock())throw ModelErrors.busy();
        return new Change();
    }
    public void requireCapacity() {
        gate.lock();try { if(closed)throw ModelErrors.unavailable("推理运行时已停止");
            if(retired.size()+(active==null?0:1)>=properties.maxLoadedSessions())throw ModelErrors.busy();
        } finally { gate.unlock(); }
    }

    public static final class Entry {
        private ModelRepository.ModelRow model;
        private final InferenceSession session;
        private final long stateVersion;
        private int leases;
        private boolean retired;
        public Entry(ModelRepository.ModelRow model,InferenceSession session,long stateVersion) { this.model=model;this.session=session;this.stateVersion=stateVersion; }
    }
    public final class Lease implements AutoCloseable {
        private final Entry entry;
        private final ModelRepository.ModelRow snapshot;
        private final AtomicBoolean released=new AtomicBoolean();
        private Lease(Entry entry) { this.entry=entry;this.snapshot=entry.model; }
        public ModelRepository.ModelRow model() { return snapshot; }
        public InferenceSession.DeviceInfo device() { return entry.session.deviceInfo(); }
        public synchronized float[] run(float[] input) {
            if(released.get())throw new IllegalStateException("Lease already released");
            return entry.session.run(input);
        }
        public synchronized InferenceSession.RunResult runTimed(float[] input) {
            if(released.get())throw new IllegalStateException("Lease already released");
            return entry.session.runTimed(input);
        }
        @Override public synchronized void close() {
            if(!released.compareAndSet(false,true))return;
            boolean release;
            gate.lock();try { entry.leases--;release=entry.retired&&entry.leases==0; }finally { gate.unlock(); }
            if(release)dispose(entry);
        }
    }
    public Lease acquire() {
        gate.lock();try {
            if(closed||active==null)throw ModelErrors.unavailable("推理模型未就绪");
            var state=repository.state();
            if(state.version()!=active.stateVersion||!Objects.equals(state.activeModelId(),active.model.id()))
                throw ModelErrors.unavailable("活动模型状态已变化，请重新加载模型");
            active.leases++;return new Lease(active);
        } finally { gate.unlock(); }
    }

    /** Caller performs the short DB transaction and infallible assignment while this gate excludes acquisition. */
    public <T> T publication(Supplier<T> operation) {
        gate.lock();try { if(closed)throw ModelErrors.unavailable("推理运行时已停止");return operation.get(); }finally { gate.unlock(); }
    }
    public Entry install(Entry replacement) {
        if(!gate.isHeldByCurrentThread())throw new IllegalStateException("Publication gate required");
        var old=active;active=replacement;lastError=null;
        if(old!=null) { old.retired=true;retired.add(old); }
        return old;
    }
    public void retireIfIdle(Entry entry) {
        if(entry==null)return;
        gate.lock();boolean idle;try { idle=entry.retired&&entry.leases==0; }finally { gate.unlock(); }
        if(idle)dispose(entry);
    }
    private void dispose(Entry entry) {
        // Entry removal is serialized with close, so a file cannot be removed while native close is pending.
        gate.lock();
        try {
            if(!retired.contains(entry)||entry.leases!=0)return;
            try { entry.session.close(); }
            catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Retired inference Session release failed; model={}",entry.model.id());return;
            }
            retired.remove(entry);runIdleActions(entry.model.id());
        } finally { gate.unlock(); }
    }
    public void updateSnapshot(ModelRepository.ModelRow model) {
        if(!gate.isHeldByCurrentThread())throw new IllegalStateException("Publication gate required");
        if(active!=null&&active.model.id().equals(model.id()))active.model=model;
    }
    public void whenUnused(String id,Runnable action) {
        gate.lock();try { idleActions.computeIfAbsent(id,k->new ArrayList<>()).add(action);runIdleActions(id); }finally { gate.unlock(); }
    }
    private void runIdleActions(String id) {
        if(active!=null&&active.model.id().equals(id))return;
        if(retired.stream().anyMatch(e->e.model.id().equals(id)))return;
        var actions=idleActions.remove(id);
        if(actions!=null)for(var action:actions)try { action.run(); }catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Deferred model cleanup failed"); }
    }
    public void restoreFailure(String code) { gate.lock();try { lastError=code; }finally { gate.unlock(); } }
    public Map<String,Object> status() {
        gate.lock();try {
            var state=repository.state();
            boolean ready=!closed&&active!=null&&active.stateVersion==state.version()&&active.model.id().equals(state.activeModelId());
            var result=new LinkedHashMap<String,Object>();
            result.put("ready",ready);result.put("active_model_id",state.activeModelId());result.put("state_version",state.version());
            result.put("requested_device",properties.device().name().toLowerCase(Locale.ROOT));
            result.put("actual_device",ready?active.session.deviceInfo().actual():null);
            result.put("fallback_reason",ready?active.session.deviceInfo().fallbackReason():null);
            result.put("runtime_version",ready?active.session.deviceInfo().runtimeVersion():null);
            result.put("error_code",ready?null:lastError==null?"STALE_RUNTIME_STATE":lastError);
            result.put("retired_sessions",retired.size());result.put("active_leases",active==null?0:active.leases);
            return Collections.unmodifiableMap(result);
        } finally { gate.unlock(); }
    }
    @PreDestroy public void shutdown() {
        gate.lock();List<Entry> entries;
        try { closed=true;if(active!=null) {active.retired=true;retired.add(active);active=null;}entries=new ArrayList<>(retired); }
        finally { gate.unlock(); }
        entries.forEach(this::retireIfIdle);
    }
}
