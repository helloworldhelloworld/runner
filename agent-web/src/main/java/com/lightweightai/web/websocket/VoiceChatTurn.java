package com.lightweightai.web.websocket;

import reactor.core.Disposable;
import reactor.core.Disposables;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connection-local ownership, allocated before chatExecutor dispatch (ADR-015). */
final class VoiceChatTurn {
    final String sessionId;
    final String turnId;
    final Disposable.Swap subscription = Disposables.swap();
    final AtomicBoolean ended = new AtomicBoolean();
    volatile Disposable attached;

    VoiceChatTurn(String sessionId, String turnId) {
        this.sessionId = sessionId;
        this.turnId = turnId;
    }

    synchronized void cancel() {
        ended.set(true);
        subscription.dispose();
    }

    synchronized void attach(Disposable disposable) {
        if (ended.get()) disposable.dispose();
        else { attached = disposable; subscription.update(disposable); }
    }
}
