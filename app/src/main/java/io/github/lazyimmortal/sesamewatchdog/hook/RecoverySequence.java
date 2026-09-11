package io.github.lazyimmortal.sesamewatchdog.hook;

final class RecoverySequence {
    enum Stage {
        IDLE,
        WAITING_EXECUTE_RESULT,
        WAITING_RESTART_RESULT,
        WAITING_HARD_RECOVERY_RESULT,
        EXHAUSTED
    }

    enum Decision {
        NONE,
        SUCCEEDED,
        SEND_RESTART,
        REQUEST_HARD_RECOVERY,
        EXHAUSTED
    }

    private Stage stage = Stage.IDLE;
    private long baselineLastExecutionElapsedRealtime;
    private long lastStartedElapsedRealtime;
    private boolean baselineObserved;

    boolean start(
            long nowElapsedRealtime,
            long lastExecutionElapsedRealtime,
            long cooldownMillis) {
        if (stage != Stage.IDLE
                || lastExecutionElapsedRealtime < 0
                || (lastStartedElapsedRealtime != 0
                && nowElapsedRealtime - lastStartedElapsedRealtime < cooldownMillis)) {
            return false;
        }
        stage = Stage.WAITING_EXECUTE_RESULT;
        baselineLastExecutionElapsedRealtime = lastExecutionElapsedRealtime;
        baselineObserved = lastExecutionElapsedRealtime > 0L;
        lastStartedElapsedRealtime = nowElapsedRealtime;
        return true;
    }

    Decision observeProgress(long lastExecutionElapsedRealtime) {
        if (stage == Stage.IDLE || lastExecutionElapsedRealtime <= 0L) {
            return Decision.NONE;
        }
        if (!baselineObserved) {
            baselineLastExecutionElapsedRealtime = lastExecutionElapsedRealtime;
            baselineObserved = true;
            return Decision.NONE;
        }
        if (lastExecutionElapsedRealtime <= baselineLastExecutionElapsedRealtime) {
            return Decision.NONE;
        }
        stage = Stage.IDLE;
        baselineLastExecutionElapsedRealtime = 0L;
        baselineObserved = false;
        return Decision.SUCCEEDED;
    }

    Decision onObservationTimeout(long lastExecutionElapsedRealtime) {
        Decision progressDecision = observeProgress(lastExecutionElapsedRealtime);
        if (progressDecision == Decision.SUCCEEDED) {
            return progressDecision;
        }
        if (stage == Stage.WAITING_EXECUTE_RESULT) {
            stage = Stage.WAITING_RESTART_RESULT;
            return Decision.SEND_RESTART;
        }
        if (stage == Stage.WAITING_RESTART_RESULT) {
            stage = Stage.WAITING_HARD_RECOVERY_RESULT;
            return Decision.REQUEST_HARD_RECOVERY;
        }
        if (stage == Stage.WAITING_HARD_RECOVERY_RESULT) {
            stage = Stage.EXHAUSTED;
            return Decision.EXHAUSTED;
        }
        return Decision.NONE;
    }

    void markExhausted() {
        if (stage != Stage.IDLE) {
            stage = Stage.EXHAUSTED;
        }
    }

    void finishFailedAttempt() {
        if (stage == Stage.EXHAUSTED) {
            stage = Stage.IDLE;
            baselineLastExecutionElapsedRealtime = 0L;
            baselineObserved = false;
        }
    }

    void reset() {
        stage = Stage.IDLE;
        baselineLastExecutionElapsedRealtime = 0L;
        baselineObserved = false;
        lastStartedElapsedRealtime = 0L;
    }

    Stage getStage() {
        return stage;
    }

    long getBaselineLastExecutionElapsedRealtime() {
        return baselineLastExecutionElapsedRealtime;
    }

}
